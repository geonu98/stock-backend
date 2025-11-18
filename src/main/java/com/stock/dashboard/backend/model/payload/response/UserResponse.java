package com.stock.dashboard.backend.model.payload.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.stock.dashboard.backend.model.Role; // 경로 수정
import com.stock.dashboard.backend.model.User; // 경로 수정
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {

    private String email;
    private String username;
    private Set<Role> roles;
    private Long id;
    private boolean active;
    private String name;
    private int age;
    private String createdAt;
    private String updatedAt;
    private List<String> interests;

    public UserResponse(String username, String email, Set<Role> roles) {
        this.username = username;
        this.email = email;
        this.roles = roles;
    }

    public UserResponse(String username, String email, Set<Role> roles, Long id, boolean active, String name) {
        this.username = username;
        this.email = email;
        this.roles = roles;
        this.id = id;
        this.active = active;
        this.name = name;
    }

    public UserResponse(User user) {
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.roles = user.getRoles();
        this.id = user.getId();
        this.active = user.getActive();
        this.name = user.getUsername();

        // Null 체크 및 toString() 변환 로직 추가 (날짜 필드가 null이 아님을 가정)
        if (user.getAge() != null) {
            this.age = user.getAge();
        }
        if (user.getCreatedAt() != null) {
            this.createdAt = user.getCreatedAt().toString();
        }
        if (user.getUpdatedAt() != null) {
            this.updatedAt = user.getUpdatedAt().toString();
        }
        this.interests = user.getInterestNames();
    }
}