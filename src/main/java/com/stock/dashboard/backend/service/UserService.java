package com.stock.dashboard.backend.service;

import com.stock.dashboard.backend.exception.ResourceNotFoundException;
import com.stock.dashboard.backend.model.Role;
import com.stock.dashboard.backend.model.RoleName;
import com.stock.dashboard.backend.model.User;
import com.stock.dashboard.backend.model.payload.request.SignUpRequest;
import com.stock.dashboard.backend.model.payload.request.UpdatePasswordRequest;
import com.stock.dashboard.backend.model.payload.request.UpdateUserRequest;

import com.stock.dashboard.backend.model.payload.response.UserProfileResponse;
import com.stock.dashboard.backend.repository.RoleRepository;
import com.stock.dashboard.backend.repository.UserRepository;
import com.stock.dashboard.backend.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;
    private final EmailService emailService;
    private final JwtTokenProvider jwtTokenProvider;



    //회원가입
    public User registerUser(SignUpRequest req){
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다.");
        }
      String encodePw  = passwordEncoder.encode(req.getPassword()); // 비번 인코딩
        User user = new User(
                req.getEmail(),
                encodePw,
                req.getName(),
                req.getAge(),
                req.getPhoneNumber(),
                "local", // provider (현재는 local 고정)
                false    // emailVerified 초기값

        );
//롤 유저 권한 부여
        Role userRole = roleRepository.findByRole(RoleName.ROLE_USER)
                .orElseThrow(() -> new RuntimeException("Role Not Found"));

        user.addRole(userRole);

        //  DB 저장
        User savedUser = userRepository.save(user);


        //  이메일 인증 토큰 생성
        String token = jwtTokenProvider.generateEmailVerificationToken(savedUser.getId(), savedUser.getEmail());


        //  인증 메일 발송
        emailService.sendVerificationEmail(savedUser, token);

        // 저장된 user 반환
        return savedUser;

    }

    public UserProfileResponse getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        return new UserProfileResponse(user);
    }

    public UserProfileResponse updateUserProfile(Long userId, UpdateUserRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        user.updateProfile(req);
        userRepository.save(user);
        return new UserProfileResponse(user);
    }

    public void changePassword(Long userId, UpdatePasswordRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        // 기존 비밀번호 확인
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Old password is incorrect");
        }
        // 새로운 비밀번호 인코딩
     String encodePw = passwordEncoder.encode(req.getNewPassword());

        // 엔티티의 커스텀 메서드 사용
        user.updatePassword(encodePw);

        userRepository.save(user);
    }
}
