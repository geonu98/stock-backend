package com.stock.dashboard.backend.controller;

import com.stock.dashboard.backend.model.payload.request.UpdatePasswordRequest;
import com.stock.dashboard.backend.model.payload.request.UpdateUserRequest;
import com.stock.dashboard.backend.model.payload.response.UserProfileResponse;
import com.stock.dashboard.backend.service.UserService;
import com.stock.dashboard.backend.security.model.CustomUserDetails;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(userService.getUserProfile(user.getId()));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody UpdateUserRequest req) {
        return ResponseEntity.ok(userService.updateUserProfile(user.getId(), req));
    }

    @PutMapping("/password")
    public ResponseEntity<String> changePassword(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody UpdatePasswordRequest req) {
        userService.changePassword(user.getId(), req);
        return ResponseEntity.ok("Password updated successfully");
    }
}
