package com.stock.dashboard.backend.config;

/**
 * 애플리케이션 전반에 걸쳐 사용되는 상수를 정의합니다.
 */
public final class AppConstants {

    // 페이지네이션 기본값 (10으로 변경)
    public static final String DEFAULT_PAGE_NUMBER = "0";
    public static final String DEFAULT_PAGE_SIZE = "10";

    // 최대 페이지 크기
    public static final int MAX_PAGE_SIZE = 50;

    // 알림톡 (Alimtok) 발신자 키 (실제 값은 숨김)
    public static final String ALIMTOK_SENDER_KEY = "";

    // 파일 첨부 물리적 경로
    public static final String ATTACH_PHYSICS_PATH = "C:/WAS_DATA/upload_file/";

    // 정렬 기본값
    public static final String DEFAULT_SORT_BY = "id";
    public static final String DEFAULT_SORT_DIRECTION = "asc";

    private AppConstants() {
        // 유틸리티 클래스 인스턴스화 방지
        throw new UnsupportedOperationException("Cannot instantiate AppConstants class");
    }
}
