package com.stock.dashboard.backend.model;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;

@Entity
@Table(
        name = "email_verification_tokens",
        indexes = {
                @Index(name = "idx_evt_user_id", columnList = "userId"),
                @Index(name = "idx_evt_token_hash", columnList = "tokenHash", unique = true)
        }
)
public class EmailVerificationToken {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(nullable = false, length = 64, unique = true)
    private String tokenHash; // sha256 hex (64)

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant usedAt;

    protected EmailVerificationToken() {}

    private EmailVerificationToken(Long userId, String email, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.email = email;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public static EmailVerificationToken create(Long userId, String email, String tokenHash, Duration ttl) {
        return new EmailVerificationToken(userId, email, tokenHash, Instant.now().plus(ttl));
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public void markUsed() {
        this.usedAt = Instant.now();
    }

    // getters (필요한 것만)
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
}
