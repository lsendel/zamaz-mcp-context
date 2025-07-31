package com.zamaz.adk.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.util.Map;

/**
 * Root controller for basic endpoints
 */
@RestController
public class RootController {
    
    @GetMapping(value = "/", produces = {MediaType.TEXT_HTML_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<?> root(@RequestHeader(value = "Accept", defaultValue = "application/json") String acceptHeader) {
        if (acceptHeader.contains("text/html")) {
            String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Zamaz Context Engine MCP</title>
                    <style>
                        body {
                            font-family: Arial, sans-serif;
                            margin: 40px;
                            background-color: #f5f5f5;
                        }
                        .container {
                            background-color: white;
                            padding: 30px;
                            border-radius: 10px;
                            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                            max-width: 800px;
                            margin: 0 auto;
                        }
                        h1 {
                            color: #333;
                            border-bottom: 2px solid #007bff;
                            padding-bottom: 10px;
                        }
                        .status {
                            background-color: #d4edda;
                            color: #155724;
                            padding: 10px;
                            border-radius: 5px;
                            margin: 20px 0;
                        }
                        .endpoints {
                            margin-top: 30px;
                        }
                        .endpoint {
                            background-color: #f8f9fa;
                            padding: 10px;
                            margin: 10px 0;
                            border-radius: 5px;
                            font-family: monospace;
                        }
                        a {
                            color: #007bff;
                            text-decoration: none;
                        }
                        a:hover {
                            text-decoration: underline;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <h1>🚀 Zamaz Context Engine MCP Server</h1>
                        <div class="status">✅ Server is running and healthy!</div>
                        
                        <h2>Available Endpoints:</h2>
                        <div class="endpoints">
                            <div class="endpoint">
                                <strong>GET</strong> <a href="/api/health">/api/health</a> - Health check endpoint
                            </div>
                            <div class="endpoint">
                                <strong>GET</strong> <a href="/api/status">/api/status</a> - Service status and version
                            </div>
                            <div class="endpoint">
                                <strong>GET</strong> <a href="/ping">/ping</a> - Simple ping endpoint
                            </div>
                        </div>
                        
                        <h2>Configuration:</h2>
                        <ul>
                            <li>Spring Boot Version: 3.2.0</li>
                            <li>Java Version: 17+</li>
                            <li>Port: 8080</li>
                        </ul>
                        
                        <p style="margin-top: 30px; color: #666;">
                            <small>Timestamp: <span id="timestamp"></span></small>
                        </p>
                    </div>
                    <script>
                        document.getElementById('timestamp').textContent = new Date().toLocaleString();
                    </script>
                </body>
                </html>
                """;
            return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
        } else {
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                    "message", "Zamaz Context Engine MCP Server is running",
                    "timestamp", System.currentTimeMillis(),
                    "status", "healthy"
                ));
        }
    }
    
    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of("pong", true, "timestamp", System.currentTimeMillis());
    }
}