package com.stock.dashboard.backend.model;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;
@Entity
@Table(
        name = "EMAIL_VERIFICATION_TOKENS",
        indexes = {
                @Index(name = "IDX_EVT_USER_ID", columnList = "USER_ID"),
                @Index(name = "IDX_EVT_TOKEN_HASH", columnList = "TOKEN_HASH", unique = true)
        }
)
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "USER_ID", nullable = false)
    private User user;

    @Column(name = "EMAIL", nullable = false, length = 320)
    private String email;

    @Column(name = "TOKEN_HASH", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "EXPIRES_AT", nullable = false)
    private Instant expiresAt;

    @Column(name = "USED_AT")
    private Instant usedAt;

    protected EmailVerificationToken() {}

    private EmailVerificationToken(User user, String email, String tokenHash, Instant expiresAt) {
        this.user = user;
        this.email = email;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public static EmailVerificationToken create(User user, String email, String tokenHash, Duration ttl) {
        return new EmailVerificationToken(user, email, tokenHash, Instant.now().plus(ttl));
    }

    public boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    public boolean isUsed() { return usedAt != null; }
    public void markUsed() { this.usedAt = Instant.now(); }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Long getUserId() { return user != null ? user.getId() : null; } // 필요하면
    public String getEmail() { return email; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
}