package com.fitness.app.access;

import com.fitness.app.access.dto.GuestPassRequest;
import com.fitness.app.access.dto.GuestPassResponse;
import com.fitness.app.access.model.GuestPass;
import com.fitness.app.access.model.GuestPassType;
import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.directory.PersonService;
import com.fitness.app.iam.dto.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Guest passes: FREE_TRIAL (one per person ever), PAID_DAY_PASS and MEMBER_GUEST
 * (unlimited). MEMBER_GUEST requires a host_member_id.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class GuestPassService
{
    private final GuestPassRepository guestPassRepository;
    private final PersonService       personService;

    public GuestPassResponse create(GuestPassRequest request, AuthenticatedUser principal)
    {
        // Create or reuse person
        var person = personService.createOrReuse(request.person());

        // Validate MEMBER_GUEST has a host
        if (request.passType() == GuestPassType.MEMBER_GUEST && request.hostMemberId() == null)
        {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "El pase de invitado requiere un anfitrión.");
        }

        // Only FREE_TRIAL is limited: one per person ever
        if (request.passType() == GuestPassType.FREE_TRIAL)
        {
            var existingFreePasses = guestPassRepository.countByPersonIdAndPassType(
                    person.getPersonId(), GuestPassType.FREE_TRIAL);

            if (existingFreePasses >= 1)
            {
                throw new BusinessException(ErrorCode.GUEST_PASS_LIMIT_REACHED);
            }
        }

        // Create guest pass
        var guestPass = new GuestPass();

        guestPass.setPersonId(person.getPersonId());
        guestPass.setPassType(request.passType());
        guestPass.setHostMemberId(request.hostMemberId());
        guestPass.setCheckedInAt(Instant.now());
        guestPass.setNotes(request.notes());
        guestPass.setRegisteredByUserId(principal.appUserId());

        var saved = guestPassRepository.save(guestPass);

        var personContact = personService.findContact(person.getPersonId());

        return GuestPassResponse.from(saved, personContact);
    }

    @Transactional(readOnly = true)
    public Page<GuestPassResponse> search(LocalDate from, LocalDate to, GuestPassType passType, Pageable pageable)
    {
        var range = DayRange.parse(from, to);

        var passes = guestPassRepository.search(range.from(), range.to(), passType, pageable);

        // Bulk load person contacts for all passes in the page
        var personIds = passes.getContent().stream()
                .map(GuestPass::getPersonId)
                .toList();

        var personContacts = personService.findContacts(personIds);

        return passes.map(pass -> GuestPassResponse.from(pass, personContacts.get(pass.getPersonId())));
    }
}
