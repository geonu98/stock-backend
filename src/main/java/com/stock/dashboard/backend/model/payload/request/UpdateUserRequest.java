package com.stock.dashboard.backend.model.payload.request;

import lombok.Data;

@Data
public class UpdateUserRequest {
    private String nickname;
    private String name;
    private Integer age;
}