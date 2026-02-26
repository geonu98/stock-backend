package com.stock.dashboard.backend.controller;

import com.stock.dashboard.backend.portfolio.PortfolioService;
import com.stock.dashboard.backend.portfolio.dto.PortfolioResponse;
import com.stock.dashboard.backend.security.model.CustomUserDetails;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
public class PortfolioController {

    private final PortfolioService portfolioService;

    @GetMapping
    public PortfolioResponse getPortfolio(@AuthenticationPrincipal CustomUserDetails user) {
        return portfolioService.getPortfolio(user.getId());
    }
}