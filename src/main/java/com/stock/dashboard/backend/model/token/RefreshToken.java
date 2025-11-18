package com.stock.dashboard.backend.model.token;

import com.stock.dashboard.backend.model.audit.DateAudit; // DateAudit 경로
import com.stock.dashboard.backend.model.UserDevice;
import jakarta.persistence.*; // Jakarta EE 변경
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.NaturalId;

import java.time.Instant;

@Entity
@Table(name = "REFRESH_TOKEN")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken extends DateAudit {

    @Id
    @Column(name = "TOKEN_ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "TOKEN", nullable = false, unique = true)
    @NaturalId(mutable = true)
    private String token;

    // UserDevice 경로가 동일한 model.token 패키지에 있다고 가정합니다.
    @OneToOne(optional = false, cascade = CascadeType.ALL)
    @JoinColumn(name = "USER_DEVICE_ID", unique = true)
    private UserDevice userDevice;

    @Column(name = "REFRESH_COUNT")
    private Long refreshCount = 0L; // 기본값 0으로 초기화

    @Column(name = "EXPIRY_DT", nullable = false)
    private Instant expiryDt;

    /**
     * 리프레시 토큰 사용 횟수를 1 증가시킵니다.
     */
    public void incrementRefreshCount() {
        refreshCount = refreshCount + 1;
    }
}