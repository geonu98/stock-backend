package com.stock.dashboard.backend.model.payload.request;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Setter
@Getter
public class CreateStudyGroupRequest {

    private String title;
    private String description;
    private Integer maxMember;
    private Date deadline;
    private Long creatorId;

    public CreateStudyGroupRequest() {
    }

    public CreateStudyGroupRequest(String title, String description, Integer maxMember, Date deadline , Long creatorId) {
        this.title = title;
        this.description = description;
        this.maxMember = maxMember;
        this.deadline = deadline;
        this.creatorId = creatorId;
    }

}
