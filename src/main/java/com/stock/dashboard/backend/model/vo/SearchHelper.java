package com.stock.dashboard.backend.model.vo;

import com.stock.dashboard.backend.config.AppConstants; // 경로 수정
import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class SearchHelper {

    private String searchCode;
    private String searchKeyword;
    private String searchType;

    // AppConstants 경로를 수정하여 기본값을 설정합니다.
    private int size = Integer.parseInt(AppConstants.DEFAULT_PAGE_SIZE);
    private int page = Integer.parseInt(AppConstants.DEFAULT_PAGE_NUMBER);

    @Builder
    public SearchHelper(String searchCode, String searchKeyword, String searchType, int size, int page) {
        this.searchCode = searchCode;
        this.searchKeyword = searchKeyword;
        this.searchType = searchType;
        this.size = size;
        this.page = page;
    }
}