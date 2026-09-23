package com.example.diary.controller;

import com.example.diary.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查接口，用于验证「前端 → Vite proxy → 后端」链路是否打通。
 */
@Tag(name = "健康检查")
@RestController
@RequestMapping("/api/v1")
public class PingController {

    @Operation(summary = "服务连通性探测")
    @GetMapping("/ping")
    public Result<Map<String, Object>> ping() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("service", "diary-backend");
        data.put("status", "UP");
        data.put("timestamp", LocalDateTime.now().toString());
        return Result.ok(data);
    }
}
