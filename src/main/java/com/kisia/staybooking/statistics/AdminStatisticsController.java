package com.kisia.staybooking.statistics;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping("/payment-summary")
    public AdminPaymentSummary paymentSummary(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return statistics.paymentSummary(from, to);
    }

    @GetMapping("/payment-daily")
    public List<AdminDailyPaymentSummary> paymentDaily(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return statistics.paymentDaily(from, to);
    }
}
