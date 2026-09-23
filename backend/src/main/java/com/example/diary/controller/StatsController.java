package com.example.diary.controller;

import com.example.diary.common.result.Result;
import com.example.diary.service.StatsService;
import com.example.diary.vo.DayCountVO;
import com.example.diary.vo.StatsOverviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 统计接口。所有指标均限定当前登录用户，且只统计未删除的日记。
 */
@Tag(name = "统计", description = "写作热力图与心情分布")
@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @Operation(summary = "写作热力图：指定年份每天的篇数，不传则取当前年份")
    @GetMapping("/calendar")
    public Result<List<DayCountVO>> calendar(@RequestParam(required = false) Integer year) {
        return Result.ok(statsService.calendar(year == null ? LocalDate.now().getYear() : year));
    }

    @Operation(summary = "统计概览：总篇数、本月篇数、连续打卡与心情分布")
    @GetMapping("/overview")
    public Result<StatsOverviewVO> overview() {
        return Result.ok(statsService.overview());
    }
}
