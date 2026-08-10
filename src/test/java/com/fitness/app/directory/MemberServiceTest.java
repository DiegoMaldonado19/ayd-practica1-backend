package com.fitness.app.directory;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.directory.dto.MemberRequest;
import com.fitness.app.directory.dto.PersonRequestDTO;
import com.fitness.app.directory.model.DocumentType;
import com.fitness.app.directory.model.Member;
import com.fitness.app.directory.model.MemberStatus;
import com.fitness.app.directory.model.Person;
import com.fitness.app.iam.UserService;
import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.iam.dto.UserResponse;
import com.fitness.app.iam.model.UserRole;
import com.fitness.app.iam.model.UserStatus;
import com.fitness.app.iam.model.VerificationChannel;
import com.fitness.app.training.TrainerAssignmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The rules the Postman collection cannot show: what the member code is built
 * from, and who is allowed to read a file. Everything else in MemberService is a
 * pass-through the collection already exercises.
 */
@ExtendWith(MockitoExtension.class)
class MemberServiceTest
{
    private static final Long PERSON_ID  = 7L;
    private static final Long MEMBER_ID  = 3L;
    private static final Long TRAINER_ID = 4L;

    @Mock private MemberRepository memberRepository;
    @Mock private PersonService    personService;
    @Mock private UserService      userService;
    @Mock private TrainerService   trainerService;

    /** Deferred in the service to keep directory -> training -> notification out of a bean cycle. */
    @Mock private ObjectProvider<TrainerAssignmentService> trainerAssignmentServiceProvider;
    @Mock private TrainerAssignmentService                 trainerAssignmentService;

    @InjectMocks private MemberService memberService;

    @Test
    void buildsTheMemberCodeFromThePerson()
    {
        when(personService.createOrReuse(any())).thenReturn(person());
        when(memberRepository.existsByPerson_PersonId(PERSON_ID)).thenReturn(false);
        when(memberRepository.save(any(Member.class))).thenAnswer(call -> call.getArgument(0));

        var created = memberService.create(request());

        assertEquals("MEM-0007", created.memberCode());
        assertEquals(MemberStatus.ACTIVE, created.status());
    }

    @Test
    void refusesToFileTheSamePersonAsMemberTwice()
    {
        // The person is reused on purpose: a trainer signing up as a member is the
        // same person. Being a member twice is what uq_member_person forbids.
        when(personService.createOrReuse(any())).thenReturn(person());
        when(memberRepository.existsByPerson_PersonId(PERSON_ID)).thenReturn(true);

        assertEquals(ErrorCode.DOCUMENT_ALREADY_REGISTERED,
                     assertThrows(BusinessException.class,
                                  () -> memberService.create(request())).getErrorCode());
    }

    @Test
    void letsAMemberReadOnlyTheirOwnFile()
    {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member()));
        when(userService.findById(1L)).thenReturn(account(PERSON_ID));

        assertEquals(MEMBER_ID, memberService.findById(MEMBER_ID, principal(UserRole.MEMBER)).memberId());
    }

    @Test
    void hidesAnotherMembersFile()
    {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member()));
        when(userService.findById(1L)).thenReturn(account(99L));

        assertEquals(ErrorCode.FORBIDDEN_RESOURCE,
                     assertThrows(BusinessException.class,
                                  () -> memberService.findById(MEMBER_ID, principal(UserRole.MEMBER)))
                             .getErrorCode());
    }

    @Test
    void letsATrainerReadTheFileOfAnAssignedMember()
    {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member()));
        when(trainerService.findTrainerIdByUser(principal(UserRole.TRAINER))).thenReturn(TRAINER_ID);
        when(trainerAssignmentServiceProvider.getObject()).thenReturn(trainerAssignmentService);
        when(trainerAssignmentService.isAssignedTo(TRAINER_ID, MEMBER_ID)).thenReturn(true);

        assertEquals(MEMBER_ID, memberService.findById(MEMBER_ID, principal(UserRole.TRAINER)).memberId());
    }

    @Test
    void hidesTheFileOfAMemberTheTrainerIsNotAssignedTo()
    {
        // "El entrenador solo ve a los suyos" (§3.2): the open row in
        // trainer_assignment is what makes a member theirs.
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member()));
        when(trainerService.findTrainerIdByUser(principal(UserRole.TRAINER))).thenReturn(TRAINER_ID);
        when(trainerAssignmentServiceProvider.getObject()).thenReturn(trainerAssignmentService);
        when(trainerAssignmentService.isAssignedTo(TRAINER_ID, MEMBER_ID)).thenReturn(false);

        assertEquals(ErrorCode.TRAINER_SCOPE_VIOLATION,
                     assertThrows(BusinessException.class,
                                  () -> memberService.findById(MEMBER_ID, principal(UserRole.TRAINER)))
                             .getErrorCode());
    }

    @Test
    void datesTheFileOnWithdrawalAndClearsItOnReactivation()
    {
        var member = member();

        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        // ck_member_term_on: the date only makes sense while the file is withdrawn.
        assertEquals(LocalDate.now(),
                     memberService.changeStatus(MEMBER_ID, MemberStatus.WITHDRAWN).terminatedOn());
        assertNull(memberService.changeStatus(MEMBER_ID, MemberStatus.ACTIVE).terminatedOn());
    }

    private static MemberRequest request()
    {
        return new MemberRequest(new PersonRequestDTO(DocumentType.DPI, "1000000000102", "Ana", "Lopez",
                                                      null, LocalDate.of(1995, 4, 12),
                                                      "ana@fitnessapp.local", "55550001", null),
                                 "Luis Lopez", "55550002", null);
    }

    private static Person person()
    {
        var person = new Person();

        person.setPersonId(PERSON_ID);
        person.setDocumentType(DocumentType.DPI);
        person.setDocumentNumber("1000000000102");
        person.setFirstName("Ana");
        person.setLastName("Lopez");

        return person;
    }

    private static Member member()
    {
        var member = new Member();

        member.setMemberId(MEMBER_ID);
        member.setPerson(person());
        member.setMemberCode("MEM-0007");
        member.setJoinedOn(LocalDate.now());
        member.setStatus(MemberStatus.ACTIVE);

        return member;
    }

    private static AuthenticatedUser principal(UserRole role)
    {
        return new AuthenticatedUser(1L, "alopez", role);
    }

    private static UserResponse account(Long personId)
    {
        return new UserResponse(1L, personId, "alopez", "Ana Lopez", "ana@fitnessapp.local",
                                UserRole.MEMBER, UserStatus.ACTIVE, true, VerificationChannel.EMAIL, null);
    }
}
