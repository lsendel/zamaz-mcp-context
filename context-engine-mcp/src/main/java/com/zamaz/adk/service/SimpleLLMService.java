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
 * Simplified LLM Service that uses REST API directly
 */
@Service
@Primary  // This will be used for direct LLM service injection
public class SimpleLLMService {
    private static final Logger logger = LoggerFactory.getLogger(SimpleLLMService.class);
    
    @Value("${adk.google.project-id:${GOOGLE_CLOUD_PROJECT:}}")
    private String projectId;
    
    @Value("${adk.google.location:us-central1}")
    private String location;
    
    @Value("${adk.ai.model.default:gemini-1.5-flash}")
    private String defaultModel;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Send a message to the LLM and get a response using REST API
     */
    public String generateResponse(String prompt, String modelName) {
        if (projectId == null || projectId.isEmpty()) {
            logger.warn("Google Cloud project not configured. Set GOOGLE_CLOUD_PROJECT environment variable.");
            return "Error: Google Cloud project not configured.";
        }
        
        try {
            String model = (modelName != null && !modelName.isEmpty()) ? modelName : defaultModel;
            String token = getAccessToken();
            
            if (token == null) {
                return "Error: Unable to get access token. Check Google Cloud credentials.";
            }
            
            // Build the API URL for Vertex AI models
            String apiUrl;
            if (model.startsWith("text-") || model.startsWith("code-")) {
                // PaLM 2 models use predict endpoint
                apiUrl = String.format(
                    "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/google/models/%s:predict",
                    location, projectId, location, model
                );
            } else {
                // Gemini models use generateContent endpoint
                apiUrl = String.format(
                    "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/google/models/%s:generateContent",
                    location, projectId, location, model
                );
            }
            
            logger.info("Calling Vertex AI API: {}", apiUrl);
            
            // Build request body
            Map<String, Object> request = new HashMap<>();
            
            if (model.startsWith("text-") || model.startsWith("code-")) {
                // PaLM 2 models use different request format
                Map<String, Object> instance = new HashMap<>();
                instance.put("prompt", prompt);
                request.put("instances", List.of(instance));
                
                Map<String, Object> parameters = new HashMap<>();
                parameters.put("temperature", 0.7);
                parameters.put("maxOutputTokens", 2048);
                request.put("parameters", parameters);
            } else {
                // Gemini models use contents format
                Map<String, Object> content = new HashMap<>();
                Map<String, Object> part = new HashMap<>();
                part.put("text", prompt);
                content.put("parts", List.of(part));
                request.put("contents", List.of(content));
                
                Map<String, Object> generationConfig = new HashMap<>();
                generationConfig.put("temperature", 0.7);
                generationConfig.put("maxOutputTokens", 2048);
                request.put("generationConfig", generationConfig);
            }
            
            // Make HTTP request
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + token);
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
                return "Error: API request failed - " + responseCode + " - " + errorResponse.toString();
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
            
            // Get response from LLM
            String llmResponse = generateResponse(contextPrompt.toString(), getModelForAgent(agent));
            
            response.put("success", true);
            response.put("agent", agent);
            response.put("response", llmResponse);
            response.put("timestamp", System.currentTimeMillis());
            response.put("model", getModelForAgent(agent));
            
        } catch (Exception e) {
            logger.error("Error processing chat: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("response", "I encountered an error processing your request. Please try again.");
        }
        
        return response;
    }
    
    /**
     * Get access token for Google Cloud
     */
    private String getAccessToken() {
        try {
            ProcessBuilder pb = new ProcessBuilder("gcloud", "auth", "application-default", "print-access-token");
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                return reader.readLine().trim();
            }
        } catch (Exception e) {
            logger.error("Failed to get access token: {}", e.getMessage());
            return null;
        }
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
                // Use Pro model for complex tasks
                return "gemini-1.5-pro";
                
            case "planner":
            case "quality":
            case "general":
            default:
                // Use Flash model for general tasks (cost-effective)
                return "gemini-1.5-flash";
        }
    }
    
    /**
     * Test the LLM connection
     */
    public boolean testConnection() {
        try {
            String response = generateResponse("Say 'Hello, I'm connected!' if you can hear me.", defaultModel);
            return response.contains("Hello") && response.contains("connected");
        } catch (Exception e) {
            logger.error("Connection test failed: {}", e.getMessage());
            return false;
        }
    }
}