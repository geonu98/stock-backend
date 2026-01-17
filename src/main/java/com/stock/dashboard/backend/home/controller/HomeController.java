package com.stock.dashboard.backend.home.controller;

import com.stock.dashboard.backend.home.service.HomeService;
import com.stock.dashboard.backend.home.vo.HomeResponseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @GetMapping
    public HomeResponseVO getHome() {
        return homeService.getHome();
    }
}
