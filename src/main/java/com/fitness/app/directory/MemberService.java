package com.fitness.app.directory;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.directory.dto.MemberRequest;
import com.fitness.app.directory.dto.MemberResponse;
import com.fitness.app.directory.model.Member;
import com.fitness.app.directory.model.MemberStatus;
import com.fitness.app.iam.UserService;
import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.iam.model.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * The member file: the alta of §3.2 #2 and the maintenance around it.
 *
 * The row-level rules of §3.2 #3 and #4 live here and not in a SecurityConfig
 * matcher, because a matcher can tell /members/7 from /members, but not whether
 * the caller is member 7 (02-Modulos §2.7).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MemberService
{
    private final MemberRepository memberRepository;
    private final PersonService    personService;
    private final UserService      userService;

    @Transactional(readOnly = true)
    public Page<MemberResponse> search(MemberStatus status, String search, Pageable pageable)
    {
        return memberRepository.search(status, search == null ? "" : search, pageable)
                .map(MemberResponse::from);
    }

    @Transactional(readOnly = true)
    public MemberResponse findById(Long memberId, AuthenticatedUser principal)
    {
        var member = findOrFail(memberId);

        assertOwnFile(principal, member);

        return MemberResponse.from(member);
    }

    public MemberResponse create(MemberRequest request)
    {
        var person = personService.createOrReuse(request.person());

        // The person may already exist because a trainer signed up as a member.
        // Being a member twice is what uq_member_person forbids.
        if (memberRepository.existsByPerson_PersonId(person.getPersonId()))
        {
            throw new BusinessException(ErrorCode.DOCUMENT_ALREADY_REGISTERED);
        }

        var member = new Member();

        member.setPerson(person);
        // Derived from the person and not from the generated member id:
        // uq_member_person already makes it unique, and this way the code is known
        // before the insert instead of needing a second write to fill it in.
        member.setMemberCode("MEM-%04d".formatted(person.getPersonId()));
        member.setJoinedOn(LocalDate.now());
        member.setStatus(MemberStatus.ACTIVE);
        applyRequest(member, request);

        return MemberResponse.from(memberRepository.save(member));
    }

    public MemberResponse update(Long memberId, MemberRequest request, AuthenticatedUser principal)
    {
        var member = findOrFail(memberId);

        assertOwnFile(principal, member);
        personService.apply(member.getPerson(), request.person());
        applyRequest(member, request);

        return MemberResponse.from(member);
    }

    public MemberResponse changeStatus(Long memberId, MemberStatus status)
    {
        var member = findOrFail(memberId);

        member.setStatus(status);
        // WITHDRAWN is the logical deletion, so it dates the file; going back to
        // ACTIVE clears the date, which is what ck_member_term_on expects.
        member.setTerminatedOn(status == MemberStatus.WITHDRAWN ? LocalDate.now() : null);

        return MemberResponse.from(member);
    }

    /**
     * A member only reaches their own file. The trainer scope of §3.2 #3 is not
     * enforced yet.
     *
     * ponytail: knowing which members a trainer has means counting
     * trainer_assignment, a table of training, so TRAINER_SCOPE_VIOLATION arrives
     * with that module. Until then a trainer sees every file.
     */
    private void assertOwnFile(AuthenticatedUser principal, Member member)
    {
        if (principal.role() != UserRole.MEMBER)
        {
            return;
        }

        var ownPersonId = userService.findById(principal.appUserId()).personId();

        if (!member.getPerson().getPersonId().equals(ownPersonId))
        {
            throw new BusinessException(ErrorCode.FORBIDDEN_RESOURCE);
        }
    }

    private static void applyRequest(Member member, MemberRequest request)
    {
        member.setEmergencyContactName(request.emergencyContactName());
        member.setEmergencyContactPhone(request.emergencyContactPhone());
        member.setNotes(request.notes());
    }

    private Member findOrFail(Long memberId)
    {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
