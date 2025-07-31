package com.zamaz.adk.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Home controller for HTML responses
 */
@Controller
public class HomeController {
    
    @GetMapping("/home")
    @ResponseBody
    public String home() {
        return """
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
                </div>
            </body>
            </html>
            """;
    }
}