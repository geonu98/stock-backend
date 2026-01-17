package com.stock.dashboard.backend.home.vo;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsItemVO {
    private String headline;
    private String source;
    private long datetime;   // millis
    private String url;
    private String summary;
    private String image;
}