package com.stock.dashboard.backend.service;

import com.stock.dashboard.backend.exception.ResourceNotFoundException;
import com.stock.dashboard.backend.model.User;
import com.stock.dashboard.backend.model.payload.request.UpdatePasswordRequest;
import com.stock.dashboard.backend.model.payload.request.UpdateUserRequest;
import com.stock.dashboard.backend.model.payload.response.UserProfileResponse;
import com.stock.dashboard.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserProfileResponse getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        return new UserProfileResponse(user);
    }

    public UserProfileResponse updateUserProfile(Long userId, UpdateUserRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (req.getNickname() != null) user.setNickname(req.getNickname());
        if (req.getName() != null) user.setName(req.getName());
        if (req.getAge() != null) user.setAge(req.getAge());

        userRepository.save(user);
        return new UserProfileResponse(user);
    }

    public void changePassword(Long userId, UpdatePasswordRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (!passwordEncoder.matches(req.getOldPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Old password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);
    }
}
