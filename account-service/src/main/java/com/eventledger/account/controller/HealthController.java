package com.eventledger.account.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {

        log.debug("Health check requested, traceId={}", traceId);

        Map<String, Object> response = new HashMap<>();
        response.put("service", "account-service");
        response.put("status", "UP");
        response.put("timestamp", Instant.now().toString());

        Map<String, Object> database = new HashMap<>();
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            database.put("status", "UP");
            database.put("type", "H2");
        } catch (Exception e) {
            log.error("Database health check failed", e);
            database.put("status", "DOWN");
            database.put("error", e.getMessage());
            response.put("status", "DEGRADED");
        }

        response.put("database", database);

        return ResponseEntity.ok(response);
    }
}
