package com.stock.dashboard.backend.home.controller;

import com.stock.dashboard.backend.home.dto.RecommendationsResponse;
import com.stock.dashboard.backend.home.service.HomeRecommendationService;
import com.stock.dashboard.backend.home.service.HomeService;
import com.stock.dashboard.backend.home.vo.HomeResponseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;
    private final HomeRecommendationService homeRecommendationService;

    @GetMapping("/recommendations")
    public RecommendationsResponse recommendations(@RequestParam(defaultValue = "0") int offset) {
        return homeRecommendationService.getRecommendations(offset);
    }

    @GetMapping
    public HomeResponseVO getHome() {
        return homeService.getHome();
    }
}
