package com.zamaz.adk.service;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.*;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Service for connecting to real LLMs (Gemini/Vertex AI)
 */
@Service
public class LLMService {
    private static final Logger logger = LoggerFactory.getLogger(LLMService.class);
    
    @Value("${adk.google.project-id:${GOOGLE_CLOUD_PROJECT:}}")
    private String projectId;
    
    @Value("${adk.google.location:us-central1}")
    private String location;
    
    @Value("${adk.ai.model.default:gemini-1.5-flash-001}")
    private String defaultModel;
    
    private VertexAI vertexAI;
    
    /**
     * Initialize Vertex AI client
     */
    private void initializeVertexAI() {
        if (vertexAI == null && projectId != null && !projectId.isEmpty()) {
            try {
                vertexAI = new VertexAI(projectId, location);
                logger.info("Initialized Vertex AI with project: {} in location: {}", projectId, location);
            } catch (Exception e) {
                logger.error("Failed to initialize Vertex AI: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Send a message to the LLM and get a response
     */
    public String generateResponse(String prompt, String modelName) {
        initializeVertexAI();
        
        if (vertexAI == null) {
            logger.warn("Vertex AI not initialized. Check your Google Cloud credentials.");
            return "Error: LLM service not properly configured. Please check Google Cloud credentials.";
        }
        
        try {
            String model = (modelName != null && !modelName.isEmpty()) ? modelName : defaultModel;
            GenerativeModel generativeModel = new GenerativeModel(model, vertexAI);
            
            // Configure generation parameters
            generativeModel = generativeModel.withGenerationConfig(
                GenerationConfig.newBuilder()
                    .setTemperature(0.7f)
                    .setMaxOutputTokens(2048)
                    .setTopP(0.95f)
                    .setTopK(40)
                    .build()
            );
            
            // Generate content
            var response = generativeModel.generateContent(prompt);
            
            String result = ResponseHandler.getText(response);
            logger.debug("Generated response for prompt: {}", prompt.substring(0, Math.min(50, prompt.length())));
            return result;
            
        } catch (IOException e) {
            logger.error("Error calling Gemini API: {}", e.getMessage());
            return "Error: Failed to generate response - " + e.getMessage();
        } catch (Exception e) {
            logger.error("Unexpected error: {}", e.getMessage());
            return "Error: Unexpected error - " + e.getMessage();
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
                return "gemini-1.5-pro-001";
                
            case "planner":
            case "quality":
            case "general":
            default:
                // Use Flash model for general tasks (cost-effective)
                return "gemini-1.5-flash-001";
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