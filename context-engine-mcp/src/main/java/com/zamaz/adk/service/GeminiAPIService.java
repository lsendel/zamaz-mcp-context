package com.zamaz.adk.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Primary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Gemini API Service using Google AI Studio API key
 * This uses the generativelanguage API instead of Vertex AI
 */
@Service
public class GeminiAPIService {
    private static final Logger logger = LoggerFactory.getLogger(GeminiAPIService.class);
    
    @Value("${gemini.api.key:${GEMINI_API_KEY:}}")
    private String apiKey;
    
    @Value("${gemini.model:gemini-2.5-flash-lite}")
    private String defaultModel;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Send a message to Gemini and get a response
     */
    public String generateResponse(String prompt, String modelName) {
        // Check if API key is set
        if (apiKey == null || apiKey.isEmpty()) {
            logger.info("Gemini API key not set. To enable real AI responses:");
            logger.info("1. Go to https://makersuite.google.com/app/apikey");
            logger.info("2. Create an API key");
            logger.info("3. Set environment variable: export GEMINI_API_KEY=your-key-here");
            logger.info("4. Restart the application");
            return "Gemini API key not configured. Using demo mode. See logs for setup instructions.";
        }
        
        try {
            String model = (modelName != null && !modelName.isEmpty()) ? modelName : defaultModel;
            
            // Build the API URL for Google AI Gemini
            String apiUrl = String.format(
                "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s",
                model, apiKey
            );
            
            logger.info("Calling Gemini API with model: {}", model);
            
            // Build request body
            Map<String, Object> request = new HashMap<>();
            Map<String, Object> content = new HashMap<>();
            Map<String, Object> part = new HashMap<>();
            part.put("text", prompt);
            content.put("parts", List.of(part));
            request.put("contents", List.of(content));
            
            // Optional: Add generation config
            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("temperature", 0.7);
            generationConfig.put("maxOutputTokens", 2048);
            generationConfig.put("topP", 0.8);
            generationConfig.put("topK", 10);
            request.put("generationConfig", generationConfig);
            
            // Make HTTP request
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            
            // Write request body
            try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream())) {
                objectMapper.writeValue(writer, request);
            }
            
            // Read response
            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }
                
                // Parse response
                Map<String, Object> responseMap = objectMapper.readValue(response.toString(), Map.class);
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseMap.get("candidates");
                if (candidates != null && !candidates.isEmpty()) {
                    Map<String, Object> candidate = candidates.get(0);
                    Map<String, Object> candidateContent = (Map<String, Object>) candidate.get("content");
                    List<Map<String, Object>> parts = (List<Map<String, Object>>) candidateContent.get("parts");
                    if (parts != null && !parts.isEmpty()) {
                        return (String) parts.get(0).get("text");
                    }
                }
                
                return "No response generated";
            } else {
                // Read error response
                StringBuilder errorResponse = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorResponse.append(line);
                    }
                }
                logger.error("API request failed with code: {} - Response: {}", responseCode, errorResponse.toString());
                
                // If API key issue, provide helpful message
                if (responseCode == 403 || responseCode == 401) {
                    return "API key issue. Please check your Gemini API key is valid.";
                }
                
                return "Error calling Gemini API. Check logs for details.";
            }
            
        } catch (Exception e) {
            logger.error("Error calling Gemini API: {}", e.getMessage());
            return "Error: " + e.getMessage();
        }
    }
    
    /**
     * Process chat with context and history
     */
    public Map<String, Object> processChat(String agent, String message, List<Map<String, String>> history) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Build conversation context
            StringBuilder contextPrompt = new StringBuilder();
            
            // Add agent-specific system prompt
            contextPrompt.append(getAgentSystemPrompt(agent)).append("\n\n");
            
            // Add conversation history
            if (history != null && !history.isEmpty()) {
                contextPrompt.append("Previous conversation:\n");
                for (Map<String, String> msg : history) {
                    String role = msg.get("role");
                    String content = msg.get("content");
                    if ("user".equals(role)) {
                        contextPrompt.append("User: ").append(content).append("\n");
                    } else {
                        contextPrompt.append("Assistant: ").append(content).append("\n");
                    }
                }
                contextPrompt.append("\n");
            }
            
            // Add current message
            contextPrompt.append("User: ").append(message).append("\n");
            contextPrompt.append("Assistant: ");
            
            // Get response from Gemini
            String llmResponse = generateResponse(contextPrompt.toString(), getModelForAgent(agent));
            
            response.put("success", true);
            response.put("agent", agent);
            response.put("response", llmResponse);
            response.put("timestamp", System.currentTimeMillis());
            response.put("model", getModelForAgent(agent));
            
            // Add note if in fallback mode
            if (llmResponse.contains("demo mode") || llmResponse.contains("API key")) {
                response.put("note", "Set GEMINI_API_KEY environment variable for real AI responses");
            }
            
        } catch (Exception e) {
            logger.error("Error processing chat: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("response", "I encountered an error processing your request. Please try again.");
        }
        
        return response;
    }
    
    /**
     * Get agent-specific system prompt
     */
    private String getAgentSystemPrompt(String agent) {
        switch (agent) {
            case "code":
                return "You are a Code Analyzer agent specializing in code quality, performance analysis, and best practices. " +
                       "Analyze code thoroughly, identify issues, suggest improvements, and explain complex code structures clearly.";
                       
            case "data":
                return "You are a Data Processing agent expert in data transformation, analysis, and processing. " +
                       "Help users with data formatting, ETL operations, data validation, and analytical insights.";
                       
            case "planner":
                return "You are a Planning agent that creates detailed execution plans and strategies. " +
                       "Break down complex tasks into manageable steps, identify dependencies, and provide clear roadmaps.";
                       
            case "quality":
                return "You are a Quality Checker agent focused on validation, testing, and quality assurance. " +
                       "Verify outputs, check for errors, ensure standards compliance, and provide quality metrics.";
                       
            default:
                return "You are a helpful AI assistant that can help with various tasks. " +
                       "Provide accurate, helpful, and detailed responses to user queries.";
        }
    }
    
    /**
     * Get appropriate model for each agent type
     */
    private String getModelForAgent(String agent) {
        switch (agent) {
            case "code":
            case "data":
                // Use Flash Lite model for all tasks
                return defaultModel;
                
            case "planner":
            case "quality":
            case "general":
            default:
                // Use Flash Lite model for all tasks
                return defaultModel;
        }
    }
    
    /**
     * Test the Gemini connection
     */
    public boolean testConnection() {
        if (apiKey == null || apiKey.isEmpty()) {
            return false;
        }
        
        try {
            String response = generateResponse("Say 'Hello, I'm connected!' if you can hear me.", defaultModel);
            return !response.contains("Error") && !response.contains("API key");
        } catch (Exception e) {
            logger.error("Connection test failed: {}", e.getMessage());
            return false;
        }
    }
}