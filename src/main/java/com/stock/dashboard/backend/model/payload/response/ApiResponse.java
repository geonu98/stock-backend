package com.stock.dashboard.backend.model.payload.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.stock.dashboard.backend.util.Util; // 경로 수정
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse {

    private final Object data; // 범용성을 위해 String 대신 Object로 변경
    private final Boolean success;
    private final String timestamp;
    private final String cause;
    private final String path;

    public ApiResponse(Boolean success, Object data, String cause, String path) {
        this.timestamp = Util.InstantToString();
        this.data = data;
        this.success = success;
        this.cause = cause;
        this.path = path;
    }

    public ApiResponse(Boolean success, Object data) {
        this.timestamp = Util.InstantToString();
        this.data = data;
        this.success = success;
        this.cause = null;
        this.path = null;
    }
}