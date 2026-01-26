package com.stock.dashboard.backend.market.twelvedata.dto;

import lombok.Data;
import java.util.List;

@Data
public class TwelveDataTimeSeriesResponse {

    private List<Value> values;

    @Data
    public static class Value {
        private String datetime;
        private String close;
    }
}
