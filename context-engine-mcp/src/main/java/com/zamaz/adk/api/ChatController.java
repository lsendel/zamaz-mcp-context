package com.zamaz.adk.api;

import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import com.zamaz.adk.service.SimpleLLMService;
import com.zamaz.adk.service.DemoLLMService;
import com.zamaz.adk.service.GeminiAPIService;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chat controller for agent interactions with real LLMs
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ChatController {
    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    
    @Autowired(required = false)
    private SimpleLLMService simpleLLMService;
    
    @Autowired(required = false)
    private GeminiAPIService geminiService;
    
    @Autowired
    private DemoLLMService demoService;
    
    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody ChatRequest request) {
        logger.info("Received chat request for agent: {} with message: {}", 
                    request.getAgent(), 
                    request.getMessage().substring(0, Math.min(50, request.getMessage().length())));
        
        Map<String, Object> response = null;
        
        // Try Gemini API service first
        if (geminiService != null) {
            response = geminiService.processChat(
                request.getAgent(), 
                request.getMessage(), 
                request.getHistory()
            );
            
            // If Gemini works and doesn't have API key issues, return response
            if (response.get("response") != null && 
                !response.get("response").toString().contains("API key")) {
                return response;
            }
        }
        
        // Try Vertex AI if Gemini API didn't work
        if (simpleLLMService != null) {
            response = simpleLLMService.processChat(
                request.getAgent(), 
                request.getMessage(), 
                request.getHistory()
            );
            
            // If Vertex AI works, return response
            if (response.get("response") != null && 
                !response.get("response").toString().contains("404")) {
                return response;
            }
        }
        
        // Fallback to demo service
        logger.info("Using demo service as fallback");
        return demoService.processChat(
            request.getAgent(),
            request.getMessage(),
            request.getHistory()
        );
    }
    
    @GetMapping("/test-llm")
    public Map<String, Object> testLLMConnection() {
        Map<String, Object> result = new HashMap<>();
        boolean connected = false;
        String service = "none";
        
        // Test Gemini API first
        if (geminiService != null && geminiService.testConnection()) {
            connected = true;
            service = "Gemini API";
        }
        // Test Vertex AI
        else if (simpleLLMService != null && simpleLLMService.testConnection()) {
            connected = true;
            service = "Vertex AI";
        }
        
        result.put("connected", connected);
        result.put("service", service);
        result.put("message", connected ? 
            "Successfully connected to " + service + "!" : 
            "No LLM service available. Using demo mode.");
        
        return result;
    }
    
    static class ChatRequest {
        private String agent;
        private String message;
        private List<Map<String, String>> history;
        
        public String getAgent() { return agent; }
        public void setAgent(String agent) { this.agent = agent; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public List<Map<String, String>> getHistory() { return history; }
        public void setHistory(List<Map<String, String>> history) { this.history = history; }
    }
}