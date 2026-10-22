package com.kisia.staybooking.statistics;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/statistics")
public class AdminStatisticsController {
    private final AdminStatisticsService statistics;

    public AdminStatisticsController(AdminStatisticsService statistics) {
        this.statistics = statistics;
    }

    @GetMapping("/overview")
    public AdminOverview overview() {
        return statistics.overview();
    }
}
