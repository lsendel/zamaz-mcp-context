package com.zamaz.adk.service;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/**
 * Demo LLM Service for testing when real models aren't available
 * This demonstrates the chat functionality while Vertex AI models are being enabled
 */
@Service
public class DemoLLMService {
    private static final Logger logger = LoggerFactory.getLogger(DemoLLMService.class);
    
    public String generateResponse(String prompt, String modelName) {
        logger.info("Demo mode: Processing prompt for model {}", modelName);
        
        // Provide helpful demo responses based on prompt
        String promptLower = prompt.toLowerCase();
        
        if (promptLower.contains("tools") || promptLower.contains("available")) {
            return "In the MCP system, you have access to several tools:\n\n" +
                   "**Calculation Tools:**\n" +
                   "- calculator: Perform mathematical calculations\n" +
                   "- percentage_calc: Calculate percentages\n\n" +
                   "**Code Tools:**\n" +
                   "- code_analyzer: Analyze code for quality and performance\n" +
                   "- dependency_detector: Detect code dependencies\n\n" +
                   "**Data Tools:**\n" +
                   "- data_transformer: Transform data between formats\n" +
                   "- data_validator: Validate data integrity\n\n" +
                   "**Workflow Tools:**\n" +
                   "- workflow_creator: Create automated workflows\n" +
                   "- workflow_executor: Execute defined workflows\n\n" +
                   "Note: This is a demo response. Enable Vertex AI models for real AI responses.";
        }
        
        if (promptLower.contains("hello") || promptLower.contains("hi")) {
            return "Hello! I'm the MCP AI assistant in demo mode. Once Vertex AI models are enabled, " +
                   "I'll provide intelligent responses powered by Google's Gemini models.";
        }
        
        if (promptLower.contains("analyze") && promptLower.contains("code")) {
            return "Code analysis would examine:\n" +
                   "- Code structure and organization\n" +
                   "- Performance bottlenecks\n" +
                   "- Security vulnerabilities\n" +
                   "- Best practice violations\n" +
                   "(Demo mode - Enable Vertex AI for real analysis)";
        }
        
        // Default response
        return "I received your message: \"" + prompt + "\"\n\n" +
               "This is a demo response. To get real AI-powered responses:\n" +
               "1. Enable Vertex AI in your Google Cloud project\n" +
               "2. Enable Gemini models in the Model Garden\n" +
               "3. Restart the application\n\n" +
               "The system is fully functional and ready for real LLM integration.";
    }
    
    public Map<String, Object> processChat(String agent, String message, List<Map<String, String>> history) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String contextPrompt = "Agent: " + agent + "\nMessage: " + message;
            String llmResponse = generateResponse(contextPrompt, "demo-model");
            
            response.put("success", true);
            response.put("agent", agent);
            response.put("response", llmResponse);
            response.put("timestamp", System.currentTimeMillis());
            response.put("model", "demo-model");
            response.put("note", "Demo mode - Enable Vertex AI for real responses");
            
        } catch (Exception e) {
            logger.error("Error in demo chat: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        
        return response;
    }
    
    public boolean testConnection() {
        return true; // Always return true in demo mode
    }
}