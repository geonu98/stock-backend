package com.stock.dashboard.backend.market.twelvedata.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SparklinePoint {
    private int index;
    private double close;


}
