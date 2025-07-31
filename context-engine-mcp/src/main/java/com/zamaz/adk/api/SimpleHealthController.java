package com.zamaz.adk.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

/**
 * Simple health check controller for initial testing
 */
@RestController
@RequestMapping("/api")
public class SimpleHealthController {
    
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "healthy",
            "timestamp", System.currentTimeMillis(),
            "service", "zamaz-context-engine-mcp"
        );
    }
    
    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
            "service", "Zamaz Context Engine MCP",
            "version", "1.0.0",
            "status", "running",
            "uptime", System.currentTimeMillis()
        );
    }
    
    @GetMapping("/")
    public Map<String, Object> root() {
        return Map.of(
            "message", "Zamaz Context Engine MCP Server is running",
            "timestamp", System.currentTimeMillis(),
            "endpoints", Map.of(
                "health", "/api/health",
                "status", "/api/status"
            )
        );
    }
}