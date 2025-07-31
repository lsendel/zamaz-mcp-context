package com.zamaz.adk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.zamaz.adk.config.ADKConfigurationProperties;

/**
 * Main Spring Boot Application for Context Engine MCP
 */
@SpringBootApplication
@EnableConfigurationProperties(ADKConfigurationProperties.class)
public class ContextEngineMCPApplication {
    
    public static void main(String[] args) {
        System.out.println("🚀 Starting Zamaz Context Engine MCP Server...");
        System.out.println("============================================");
        
        SpringApplication app = new SpringApplication(ContextEngineMCPApplication.class);
        
        // Set default properties
        app.setDefaultProperties(java.util.Map.of(
            "server.port", "8080",
            "logging.level.com.zamaz", "INFO",
            "spring.main.banner-mode", "console"
        ));
        
        try {
            app.run(args);
            System.out.println("✅ Context Engine MCP Server started successfully!");
            System.out.println("📡 Server available at: http://localhost:8080");
            System.out.println("🔍 Health check: http://localhost:8080/api/health");
            System.out.println("📊 Metrics: http://localhost:8080/api/metrics");
        } catch (Exception e) {
            System.err.println("❌ Failed to start Context Engine MCP Server:");
            System.err.println("   " + e.getMessage());
            System.exit(1);
        }
    }
}