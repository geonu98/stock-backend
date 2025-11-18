package com.stock.dashboard.backend.event;

import com.stock.dashboard.backend.model.User; // 🛑 경로 수정
import org.springframework.context.ApplicationEvent;

public class OnUserAccountChangeEvent extends ApplicationEvent {

    private final User user; // final 키워드 추가 (모범 사례)
    private final String action; // final 키워드 추가
    private final String actionStatus; // final 키워드 추가

    public OnUserAccountChangeEvent(User user, String action, String actionStatus) {
        super(user);
        this.user = user;
        this.action = action;
        this.actionStatus = actionStatus;
    }

    public User getUser() {
        return user;
    }

    /* 이벤트 클래스는 일반적으로 생성 후 수정되지 않으므로 Setter는 제거합니다.
    public void setUser(User user) {
        this.user = user;
    }*/

    public String getAction() {
        return action;
    }

    /*
    public void setAction(String action) {
        this.action = action;
    }
    */

    public String getActionStatus() {
        return actionStatus;
    }

    /*
    public void setActionStatus(String actionStatus) {
        this.actionStatus = actionStatus;
    }
    */
}
