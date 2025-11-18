package com.stock.dashboard.backend.model.payload.request;

import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class BoardDeleteRequest {

    private List<Long> id;

}
