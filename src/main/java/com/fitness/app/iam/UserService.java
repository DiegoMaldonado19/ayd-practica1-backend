package com.fitness.app.iam;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.directory.PersonService;
import com.fitness.app.iam.dto.ChangePasswordRequest;
import com.fitness.app.iam.dto.TwoFactorRequest;
import com.fitness.app.iam.dto.UserResponse;
import com.fitness.app.iam.model.AppUser;
import com.fitness.app.iam.model.UserRole;
import com.fitness.app.iam.model.UserStatus;
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
