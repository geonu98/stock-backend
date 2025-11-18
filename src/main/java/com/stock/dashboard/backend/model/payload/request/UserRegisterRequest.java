package com.stock.dashboard.backend.model.payload.request;

import lombok.*;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class UserRegisterRequest {

    private Long id;
    private String username;
    private String email;
    private String password;
    private String passwordConfirm;
    private String name;
    private String roleNum;
    private boolean active;

}
