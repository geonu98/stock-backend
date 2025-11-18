package com.stock.dashboard.backend.model.payload.response;

import com.stock.dashboard.backend.model.User;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class UserProfileResponse {
    private Long id;
    private String email;
    private String username;
    private String nickname;
    private String name;
    private Integer age;
    private Boolean active;
    private Boolean emailVerified;
    private List<String> roles;
    private List<String> interests;
    private LocalDateTime createdAt;

    public UserProfileResponse(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.username = user.getUsername();
        this.nickname = user.getNickname();
        this.name = user.getName();
        this.age = user.getAge();
        this.active = user.getActive();
        this.emailVerified = user.getIsEmailVerified();
        this.roles = user.getRoles().stream()
                .map(role -> role.getRole().name())
                .toList();
        this.interests = user.getInterestNames();
        this.createdAt = user.getCreatedAt();

}

}
