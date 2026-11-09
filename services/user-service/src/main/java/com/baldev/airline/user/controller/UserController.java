package com.baldev.airline.user.controller;

import com.baldev.airline.common.exception.ForbiddenException;
import com.baldev.airline.user.payload.dto.UserDTO;
import com.baldev.airline.common.web.CurrentUser;
import com.baldev.airline.user.service.AuthService;
import com.baldev.airline.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    /** The caller's own profile, resolved from the verified token (never from a client-supplied id). */
    @GetMapping("/profile")
    public UserDTO getUserProfile(CurrentUser user) {
        return userService.getUserById(user.id());
    }

    /** "Sign out everywhere": revokes every session and access token of the caller, on every device. */
    @PostMapping("/me/logout-all")
    public ResponseEntity<Void> logoutEverywhere(CurrentUser user) {
        authService.logoutEverywhere(user.id());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{userId}")
    public UserDTO getUserById(@PathVariable Long userId, CurrentUser user) {
        if (!user.isSystemAdmin() && !user.id().equals(userId)) {
            throw new ForbiddenException("You can only view your own account");
        }
        return userService.getUserById(userId);
    }

    @GetMapping
    public Page<UserDTO> getUsers(CurrentUser user, @PageableDefault(size = 20) Pageable pageable) {
        if (!user.isSystemAdmin()) {
            throw new ForbiddenException("Only system administrators can list users");
        }
        return userService.getAllUsers(pageable);
    }
}
