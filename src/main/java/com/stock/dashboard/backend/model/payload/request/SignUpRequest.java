package com.stock.dashboard.backend.model.payload.request;

import lombok.Data;

@Data
public class SignUpRequest {
    private String email;
    private String password;
    private String name ;
    private Integer age;
    private String phoneNumber;
}
