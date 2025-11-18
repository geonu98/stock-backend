package com.stock.dashboard.backend.model.payload.request;

import com.stock.dashboard.backend.validation.annotation.NullOrNotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class FileDeleteRequest {

    @NullOrNotBlank(message = "id는 필수입니다.")
    private Long id;
    private String fileTarget;

}
