package com.stock.dashboard.backend.model;

import com.stock.dashboard.backend.model.audit.DateAudit; // 경로 수정
import com.stock.dashboard.backend.model.token.RefreshToken;
import com.stock.dashboard.backend.model.vo.DeviceType; // 경로 수정
import com.stock.dashboard.backend.model.User; // User 엔티티 경로 추가
import jakarta.persistence.*; // Jakarta EE 변경
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Entity
@Table(name = "USER_DEVICE")
@Slf4j
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserDevice extends DateAudit {

    @Id
    @Column(name = "USER_DEVICE_ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // User 엔티티가 model.vo 패키지에 있다고 가정하고 추가
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", nullable = false)
    private User user;

    @Column(name = "DEVICE_TYPE")
    @Enumerated(value = EnumType.STRING)
    private DeviceType deviceType;

    @Column(name = "NOTIFICATION_TOKEN")
    private String notificationToken;

    @Column(name = "DEVICE_ID", nullable = false)
    private String deviceId;

    @OneToOne(optional = false, mappedBy = "userDevice")
    private RefreshToken refreshToken; // 경로 수정

    @Column(name = "IS_REFRESH_ACTIVE")
    private Boolean isRefreshActive = true; // 기본값 true로 설정
}