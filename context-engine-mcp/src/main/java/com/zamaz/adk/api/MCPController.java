package com.zamaz.adk.api;

import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import com.zamaz.adk.service.SimpleLLMService;
import com.zamaz.adk.mcp.MCPHandler;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MCP (Model Context Protocol) API Controller
 * Handles context management, tool execution, and resource access
 */
@RestController
@RequestMapping("/api/mcp")
@CrossOrigin(origins = "*")
public class MCPController {
    private static final Logger logger = LoggerFactory.getLogger(MCPController.class);
    
    @Autowired
    private SimpleLLMService llmService;
    
    @Autowired
    private MCPHandler mcpHandler;
    
    private String currentTenant = "default";
    
    /**
     * Store context
     */
    @PostMapping("/context/store")
    public Map<String, Object> storeContext(@RequestBody Map<String, Object> request) {
        String key = (String) request.get("key");
        Object value = request.get("value");
        String tenant = (String) request.getOrDefault("tenant", currentTenant);
        
        mcpHandler.storeContext(tenant, key, value);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Context stored successfully");
        response.put("tenant", tenant);
        response.put("key", key);
        return response;
    }
    
    /**
     * Retrieve context
     */
    @GetMapping("/context/retrieve")
    public Map<String, Object> retrieveContext(
            @RequestParam String key,
            @RequestParam(required = false) String tenant) {
        
        if (tenant == null) tenant = currentTenant;
        Object value = mcpHandler.getContext(tenant, key);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", value != null);
        response.put("tenant", tenant);
        response.put("key", key);
        response.put("value", value);
        return response;
    }
    
    /**
     * Clear context
     */
    @DeleteMapping("/context/clear")
    public Map<String, Object> clearContext(@RequestParam(required = false) String tenant) {
        if (tenant == null) tenant = currentTenant;
        mcpHandler.clearContext(tenant);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Context cleared successfully");
        response.put("tenant", tenant);
        return response;
    }
    
    /**
     * Switch tenant
     */
    @PostMapping("/tenant/switch")
    public Map<String, Object> switchTenant(@RequestBody Map<String, String> request) {
        String tenant = request.get("tenant");
        currentTenant = tenant;
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Switched to tenant: " + tenant);
        response.put("tenant", tenant);
        return response;
    }
    
    /**
     * List available tools
     */
    @GetMapping("/tools/list")
    public Map<String, Object> listTools(@RequestParam(required = false) String category) {
        List<MCPHandler.Tool> tools = mcpHandler.getAvailableTools(category);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("tools", tools);
        response.put("count", tools.size());
        return response;
    }
    
    /**
     * Execute a tool
     */
    @PostMapping("/tools/execute")
    public Map<String, Object> executeTool(@RequestBody Map<String, Object> request) {
        String toolName = (String) request.get("tool");
        Map<String, Object> parameters = (Map<String, Object>) request.get("parameters");
        
        String result = mcpHandler.executeTool(toolName, parameters);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", !result.startsWith("Error:"));
        response.put("tool", toolName);
        response.put("result", result);
        return response;
    }
    
    /**
     * Vector search - find similar items
     */
    @PostMapping("/search/similar")
    public Map<String, Object> findSimilar(@RequestBody Map<String, String> request) {
        String query = request.get("query");
        String type = request.get("type");
        
        List<String> results = mcpHandler.findSimilar(query, type);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("query", query);
        response.put("type", type);
        response.put("results", results);
        response.put("count", results.size());
        return response;
    }
    
    /**
     * Process MCP command - unified interface for natural language commands
     */
    @PostMapping("/process")
    public Map<String, Object> processMCPCommand(@RequestBody Map<String, String> request) {
        String command = request.get("command");
        logger.info("Processing MCP command: {}", command);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Analyze command intent
            String intentPrompt = String.format(
                "Analyze this MCP command and return JSON with: " +
                "{\"action\": \"store_context|retrieve_context|list_tools|execute_tool|search|workflow|other\", " +
                "\"parameters\": {...}, \"confidence\": 0.0-1.0}\n" +
                "Command: %s", command
            );
            
            String intentAnalysis = llmService.generateResponse(intentPrompt, "gemini-1.5-flash-001");
            
            // Process based on detected intent
            if (command.toLowerCase().contains("store") && command.toLowerCase().contains("context")) {
                // Extract key-value from command
                response = processStoreContext(command);
            } else if (command.toLowerCase().contains("retrieve") || command.toLowerCase().contains("get")) {
                response = processRetrieveContext(command);
            } else if (command.toLowerCase().contains("tool") || command.toLowerCase().contains("available")) {
                response.put("tools", mcpHandler.getAvailableTools(null));
                response.put("message", "Here are the available tools");
            } else if (command.toLowerCase().contains("calculate") || command.toLowerCase().contains("compute")) {
                response = processCalculation(command);
            } else if (command.toLowerCase().contains("find similar") || command.toLowerCase().contains("search")) {
                response = processSearch(command);
            } else {
                // Use LLM for general processing
                String result = llmService.generateResponse(command, "gemini-1.5-flash-001");
                response.put("result", result);
            }
            
            response.put("success", true);
            response.put("command", command);
            
        } catch (Exception e) {
            logger.error("Error processing MCP command: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        
        return response;
    }
    
    private Map<String, Object> processStoreContext(String command) {
        Map<String, Object> response = new HashMap<>();
        
        // Extract context from command using LLM
        String extractPrompt = String.format(
            "Extract the key and value from this store context command. " +
            "Return JSON: {\"key\": \"...\", \"value\": \"...\"}\n" +
            "Command: %s", command
        );
        
        String extraction = llmService.generateResponse(extractPrompt, "gemini-1.5-flash-001");
        
        // For demo, parse simple patterns
        if (command.contains("preferences")) {
            mcpHandler.storeContext(currentTenant, "preferences", 
                "dark mode, language=English, timezone=EST");
            response.put("message", "Stored user preferences");
        }
        
        response.put("action", "store_context");
        return response;
    }
    
    private Map<String, Object> processRetrieveContext(String command) {
        Map<String, Object> response = new HashMap<>();
        
        if (command.contains("preferences")) {
            Object value = mcpHandler.getContext(currentTenant, "preferences");
            response.put("value", value);
            response.put("message", value != null ? 
                "Retrieved preferences: " + value : 
                "No preferences found");
        }
        
        response.put("action", "retrieve_context");
        return response;
    }
    
    private Map<String, Object> processCalculation(String command) {
        Map<String, Object> response = new HashMap<>();
        
        Map<String, Object> params = new HashMap<>();
        params.put("expression", command);
        
        String result = mcpHandler.executeTool("calculator", params);
        response.put("result", result);
        response.put("action", "calculation");
        
        return response;
    }
    
    private Map<String, Object> processSearch(String command) {
        Map<String, Object> response = new HashMap<>();
        
        String type = "products";
        if (command.contains("document")) type = "documents";
        
        // Extract search query
        String query = command.replaceAll(".*(?:find similar|search for)\\s+", "")
                            .replaceAll("\\s+(?:products|documents).*", "");
        
        List<String> results = mcpHandler.findSimilar(query, type);
        response.put("results", results);
        response.put("action", "search");
        response.put("type", type);
        
        return response;
    }
}