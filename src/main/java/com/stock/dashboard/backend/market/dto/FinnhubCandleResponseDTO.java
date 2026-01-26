package com.stock.dashboard.backend.market.dto;


import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class FinnhubCandleResponseDTO {
    private String s;         // "ok" or "no_data"
    private List<Long> t;     // timestamps (seconds)
    private List<Double> o;   // open
    private List<Double> h;   // high
    private List<Double> l;   // low
    private List<Double> c;   // close
    private List<Double> v;   // volume
}
