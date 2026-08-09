package com.fitness.app.iam;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.directory.PersonService;
import com.fitness.app.iam.dto.ChangePasswordRequest;
import com.fitness.app.iam.dto.CreateUserRequest;
import com.fitness.app.iam.dto.TwoFactorRequest;
import com.fitness.app.iam.dto.UserResponse;
import com.fitness.app.iam.model.AppUser;
import com.fitness.app.iam.model.UserRole;
import com.fitness.app.iam.model.UserStatus;
import com.fitness.app.iam.model.VerificationChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Account maintenance: the administrator's listing, and every user's own settings. */
@Service
@RequiredArgsConstructor
@Transactional
public class UserService
{
    private final UserRepository  userRepository;
    private final PersonService   personService;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Page<UserResponse> search(UserRole role, UserStatus status, String search, Pageable pageable)
    {
        var users    = userRepository.search(role, status, search == null ? "" : search, pageable);
        // One query for the whole page instead of one per row.
        var contacts = personService.findContacts(users.getContent().stream()
                                                          .map(AppUser::getPersonId)
                                                          .toList());

        return users.map(user -> UserResponse.from(user, contacts.get(user.getPersonId())));
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long appUserId)
    {
        return toResponse(findOrFail(appUserId));
    }

    /**
     * Credentials for a person who already has a file in directory. The account is
     * born ACTIVE so the person can sign in with the password they were handed, and
     * with two-factor on, which is the column default.
     *
     * findContact is the check that the person exists: it already answers
     * PERSON_NOT_FOUND, and its result is the contact block of the response.
     */
    public UserResponse create(CreateUserRequest request)
    {
        var contact = personService.findContact(request.personId());

        if (userRepository.existsByUsername(request.username()))
        {
            throw new BusinessException(ErrorCode.USERNAME_TAKEN);
        }

        // uq_app_user_person: a trainer who is also a member signs in once, not twice.
        if (userRepository.existsByPersonId(request.personId()))
        {
            throw new BusinessException(ErrorCode.PERSON_ALREADY_HAS_ACCOUNT);
        }

        var now  = Instant.now();
        var user = new AppUser();

        user.setPersonId(request.personId());
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setStatus(UserStatus.ACTIVE);
        user.setTwoFactorEnabled(true);
        // The DDL defaults status, the channel and created_at, but the entity maps
        // them, so Hibernate sends them in the INSERT and a null would break NOT NULL.
        user.setTwoFactorChannel(VerificationChannel.EMAIL);
        user.setPasswordChangedAt(now);
        user.setCreatedAt(now);

        return UserResponse.from(userRepository.save(user), contact);
    }

    public UserResponse changeStatus(Long appUserId, UserStatus status)
    {
        var user = findOrFail(appUserId);

        user.setStatus(status);

        // Activating is also how an administrator unblocks an account.
        if (status == UserStatus.ACTIVE)
        {
            user.clearFailedAttempts();
        }

        return toResponse(user);
    }

    public void changePassword(Long appUserId, ChangePasswordRequest request)
    {
        var user = findOrFail(appUserId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash()))
        {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(Instant.now());
    }

    public UserResponse updateTwoFactor(Long appUserId, TwoFactorRequest request)
    {
        var user = findOrFail(appUserId);

        user.setTwoFactorEnabled(request.enabled());

        // A null channel keeps the one already stored: the column is NOT NULL.
        if (request.channel() != null)
        {
            user.setTwoFactorChannel(request.channel());
        }

        return toResponse(user);
    }

    private AppUser findOrFail(Long appUserId)
    {
        return userRepository.findById(appUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private UserResponse toResponse(AppUser user)
    {
        return UserResponse.from(user, personService.findContact(user.getPersonId()));
    }
}
