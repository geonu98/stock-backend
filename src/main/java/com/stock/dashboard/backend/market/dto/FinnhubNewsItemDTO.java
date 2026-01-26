package com.stock.dashboard.backend.market.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FinnhubNewsItemDTO {
    private long datetime;   // seconds
    private String headline;
    private String source;
    private String summary;
    private String url;
    private String image;
}