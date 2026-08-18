package com.fitness.app.directory;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.directory.dto.MemberRequest;
import com.fitness.app.directory.dto.MemberResponse;
import com.fitness.app.directory.model.Member;
import com.fitness.app.directory.model.MemberStatus;
import com.fitness.app.iam.UserService;
import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.iam.dto.UserResponse;
import com.fitness.app.iam.model.UserRole;
import com.fitness.app.training.TrainerAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
    private final TrainerService   trainerService;

    /**
     * ObjectProvider and not a direct field: notification.NotificationService injects
     * MemberService to resolve the account behind every notice, and
     * training.TrainerAssignmentService notifies when it moves a caseload. All three
     * arrows are the ones 02-Modulos §3 allows, but together they close a bean cycle
     * Spring refuses to build. The same resolution MembershipService already applies
     * to classes: the call keeps its documented direction, only the lookup waits until
     * the bean graph is up.
     */
    private final ObjectProvider<TrainerAssignmentService> trainerAssignmentServiceProvider;

    /**
     * memberIds null means no membership filter was asked for; an empty list means one
     * was and nobody matched. Both are normalized to a sentinel because JPQL cannot
     * bind a null list into an IN, and no member has id 0.
     */
    @Transactional(readOnly = true)
    public Page<MemberResponse> search(MemberStatus status, List<Long> memberIds, String search,
                                       AuthenticatedUser principal, Pageable pageable)
    {
        var scopedIds          = scopeToCaseload(memberIds, principal);
        var filterByMembership = scopedIds != null;
        var ids                = scopedIds == null || scopedIds.isEmpty() ? List.of(0L) : scopedIds;

        return memberRepository.search(status, filterByMembership, ids, search == null ? "" : search, pageable)
                .map(MemberResponse::from);
    }

    /**
     * The listing side of the rule assertOwnFile enforces one row at a time: a trainer
     * sees "la lista de socios que tiene asignados de forma personal" (Enunciado) and
     * not the directory of the gym. A listing narrows where a detail rejects, so the
     * caseload is intersected with whatever filter membership already resolved.
     */
    private List<Long> scopeToCaseload(List<Long> memberIds, AuthenticatedUser principal)
    {
        if (principal.role() != UserRole.TRAINER)
        {
            return memberIds;
        }

        var caseload = trainerAssignmentServiceProvider.getObject()
                .assignedMemberIds(trainerService.findTrainerIdByUser(principal));

        return memberIds == null ? caseload
                                 : memberIds.stream().filter(caseload::contains).toList();
    }

    @Transactional(readOnly = true)
    public MemberResponse findById(Long memberId, AuthenticatedUser principal)
    {
        var member = findOrFail(memberId);

        assertOwnFile(principal, member);

        return MemberResponse.from(member);
    }

    /**
     * The memberId of the signed-in member. classes' rating endpoint is S-only and
     * carries no member_id in its body - the enrolled member is always the caller,
     * the same resolution TrainerService.findTrainerIdByUser does for trainers.
     */
    @Transactional(readOnly = true)
    public Long findOwnMemberId(AuthenticatedUser principal)
    {
        var personId = userService.findById(principal.appUserId()).personId();

        return findMemberIdByPersonId(personId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    /**
     * The file of a person, empty when there is none. iam stamps it on the sign-in
     * payload, where a MEMBER account registered in two steps may still have no file:
     * there the absence is an answer and not a failure, which is why this one returns
     * Optional and findOwnMemberId is the one that throws.
     */
    @Transactional(readOnly = true)
    public Optional<Long> findMemberIdByPersonId(Long personId)
    {
        return memberRepository.findByPerson_PersonId(personId).map(Member::getMemberId);
    }

    /**
     * The account that receives a notice addressed to a member. notification stores
     * app_user_id while membership and classes carry member_id, and the dependency
     * matrix (02-Modulos §3) lets notification reach directory and directory reach
     * iam - never notification to iam directly. Resolving it here keeps that hop in
     * one place instead of repeating it in every caller.
     *
     * Empty when the member has no credentials: the two-step registration allows a
     * file without an account, and a notice with no recipient is skipped, not failed.
     */
    @Transactional(readOnly = true)
    public Optional<UserResponse> accountOf(Long memberId)
    {
        return memberRepository.findById(memberId)
                .flatMap(member -> userService.findByPersonId(member.getPerson().getPersonId()));
    }

    /** Resolves member names in bulk: one query for a whole page instead of N. */
    @Transactional(readOnly = true)
    public Map<Long, String> findNames(Collection<Long> memberIds)
    {
        if (memberIds.isEmpty())
        {
            return Map.of();
        }

        return memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(Member::getMemberId, m -> m.getPerson().getFullName()));
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
     * The row-level rules of §3.2 #3: a member only reaches their own file, and a
     * trainer only the files of the members assigned to them. The administrator and
     * the receptionist reach every file, which is why neither role is named here.
     */
    private void assertOwnFile(AuthenticatedUser principal, Member member)
    {
        if (principal.role() == UserRole.TRAINER)
        {
            var trainerId = trainerService.findTrainerIdByUser(principal);

            if (!trainerAssignmentServiceProvider.getObject().isAssignedTo(trainerId, member.getMemberId()))
            {
                throw new BusinessException(ErrorCode.TRAINER_SCOPE_VIOLATION);
            }

            return;
        }

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
