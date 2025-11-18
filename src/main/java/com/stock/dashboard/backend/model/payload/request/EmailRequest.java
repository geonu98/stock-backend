package com.stock.dashboard.backend.model.payload.request;

import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class EmailRequest {

    private String mailTo;
    private String mailType;
    private String username;
    private String passwordAuth;
    private String authCode;

}
