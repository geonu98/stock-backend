package com.stock.dashboard.backend.market.twelvedata.dto;

import lombok.Data;
import java.util.List;

@Data
public class TwelveDataTimeSeriesResponse {

    private List<Value> values;

    @Data
    public static class Value {
        private String datetime;
        private String open;
        private String high;
        private String low;
        private String close;
        private String volume;
    }
}
