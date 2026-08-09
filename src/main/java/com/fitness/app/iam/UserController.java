package com.fitness.app.iam;

import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.iam.dto.ChangePasswordRequest;
import com.fitness.app.iam.dto.StatusChangeRequest;
import com.fitness.app.iam.dto.TwoFactorRequest;
import com.fitness.app.iam.dto.UserResponse;
import com.fitness.app.iam.model.UserRole;
import com.fitness.app.iam.model.UserStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The /users routes. Who may call what is decided in SecurityConfig: /users/me/**
 * is open to every role, the rest belongs to the administrator.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController
{
    private final UserService userService;

    @GetMapping
    public PagedModel<UserResponse> list(@RequestParam(required = false) UserRole   role,
                                         @RequestParam(required = false) UserStatus status,
                                         @RequestParam(required = false) String     search,
                                         Pageable                                   pageable)
    {
        return new PagedModel<>(userService.search(role, status, search, pageable));
    }

    @GetMapping("/{appUserId}")
    public UserResponse detail(@PathVariable Long appUserId)
    {
        return userService.findById(appUserId);
    }

    @PatchMapping("/{appUserId}/status")
    public UserResponse changeStatus(@PathVariable Long appUserId,
                                     @Valid @RequestBody StatusChangeRequest request)
    {
        return userService.changeStatus(appUserId, request.status());
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthenticatedUser principal,
                               @Valid @RequestBody ChangePasswordRequest request)
    {
        userService.changePassword(principal.appUserId(), request);
    }

    @PatchMapping("/me/two-factor")
    public UserResponse updateTwoFactor(@AuthenticationPrincipal AuthenticatedUser principal,
                                        @Valid @RequestBody TwoFactorRequest request)
    {
        return userService.updateTwoFactor(principal.appUserId(), request);
    }
}
