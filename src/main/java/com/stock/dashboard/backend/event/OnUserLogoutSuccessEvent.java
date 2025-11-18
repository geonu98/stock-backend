package com.stock.dashboard.backend.event;

import com.stock.dashboard.backend.model.payload.request.LogOutRequest; // 🛑 현재 프로젝트 패키지로 수정
import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.Date;

public class OnUserLogoutSuccessEvent extends ApplicationEvent {

    private final String userIdentifier; // userId 또는 email 등 식별자
    private final String token;
    private final transient LogOutRequest logOutRequest;
    private final Date eventTime;

    public OnUserLogoutSuccessEvent(String userIdentifier, String token, LogOutRequest logOutRequest) {
        super(userIdentifier);
        this.userIdentifier  = userIdentifier ;
        this.token = token;
        this.logOutRequest = logOutRequest;
        this.eventTime = Date.from(Instant.now());
    }

    public String getUserIdentifier() {
        return userIdentifier;
    }

    public String getToken() {
        return token;
    }

    public LogOutRequest getLogOutRequest() {
        return logOutRequest;
    }

    public Date getEventTime() {
        return eventTime;
    }
}
