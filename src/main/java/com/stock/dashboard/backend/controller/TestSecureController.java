package com.stock.dashboard.backend.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/secure")
public class TestSecureController {

    @GetMapping("/me")
    public ResponseEntity<?> getMyInfo(@AuthenticationPrincipal UserDetails userDetails) {
        log.info("[TestSecureController] Authenticated user: {}",
                userDetails != null ? userDetails.getUsername() : "Anonymous");

        if (userDetails == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        return ResponseEntity.ok("Access granted ✅ User: " + userDetails.getUsername());
    }
}
