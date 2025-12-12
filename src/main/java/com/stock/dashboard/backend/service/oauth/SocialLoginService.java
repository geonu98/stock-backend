package com.stock.dashboard.backend.service.oauth;

import com.stock.dashboard.backend.model.Role;
import com.stock.dashboard.backend.model.RoleName;
import com.stock.dashboard.backend.model.User;
import com.stock.dashboard.backend.repository.RoleRepository;
import com.stock.dashboard.backend.repository.UserRepository;
import com.stock.dashboard.backend.security.JwtTokenProvider;
import com.stock.dashboard.backend.security.model.CustomUserDetails;
import com.stock.dashboard.backend.service.oauth.dto.ConnectEmailRequest;
import com.stock.dashboard.backend.service.oauth.dto.SocialUserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SocialLoginService {

    private final List<SocialOAuthService> socialOAuthServices;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public String getLoginUrl(String provider) {
        return "https://kauth.kakao.com/oauth/authorize"
                + "?client_id=" + System.getenv("KAKAO_CLIENT_ID")
                + "&redirect_uri=" + System.getenv("KAKAO_REDIRECT_URI")
                + "&response_type=code";
    }

    public SocialUserInfo getUserInfo(String provider, String code) {

        SocialOAuthService oAuthService = socialOAuthServices.stream()
                .filter(service -> service.supports(provider))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported provider: " + provider));

        return oAuthService.getUserInfo(code);
    }

    public String loginWithSocialInfo(SocialUserInfo info) {
        if (info.email() == null || info.email().isBlank()) {
            throw new IllegalStateException("Email required for social login");
        }

        return processSocialLogin(info.email(), info.provider(), info.providerId(), info);
    }

    public String connectEmailAndLogin(ConnectEmailRequest req) {

        if (req.email() == null || req.email().isBlank()) {
            throw new IllegalArgumentException("Email required");
        }

        SocialUserInfo info = new SocialUserInfo(
                req.provider(), req.providerId(), req.email(), null, null
        );

        return processSocialLogin(req.email(), req.provider(), req.providerId(), info);
    }

    private String processSocialLogin(String email, String provider, String providerId, SocialUserInfo info) {
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> createNewSocialUser(info));

        user.connectSocial(provider, providerId);

// 최신 프로필 정보 갱신
        if (info.nickname() != null && !info.nickname().isBlank()) {
            user.updateNickname(info.nickname());
        }
        if (info.profileImage() != null) {
            user.updateProfileImage(info.profileImage());
        }

        userRepository.save(user);
        return jwtTokenProvider.generateToken(new CustomUserDetails(user));
    }

    private User createNewSocialUser(SocialUserInfo info) {
        Role defaultRole = roleRepository.findByRole(RoleName.ROLE_USER)
                .orElseThrow(() -> new IllegalStateException("ROLE_USER not found"));

        return userRepository.save(
                User.createSocialUser(
                        info.email(),
                        info.nickname(),
                        info.provider(),
                        info.providerId(),
                        info.profileImage(),
                        defaultRole
                )
        );
    }
}
