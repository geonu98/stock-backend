package com.stock.dashboard.backend.portfolio;

public final class PortfolioWarningCodes {

    private PortfolioWarningCodes() {}

    public static final String FX_RATE_UNAVAILABLE = "FX_RATE_UNAVAILABLE";//USD/KRW 환율 조회 실패
    public static final String QUOTE_UNAVAILABLE = "QUOTE_UNAVAILABLE"; //특정 종목의 실시간 시세를 가져오지 못한 경우
    public static final String INVALID_QUOTE_PRICE = "INVALID_QUOTE_PRICE";//시세는 받아왔지만 값이 비정상적인 경우
}