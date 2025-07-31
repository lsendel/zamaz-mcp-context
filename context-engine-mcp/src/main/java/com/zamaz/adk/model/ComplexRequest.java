package com.zamaz.adk.model;

import java.util.List;
import java.util.Map;

/**
 * Represents a complex request that requires multi-agent processing
 */
public class ComplexRequest {
    private String id;
    private String description;
    private String type;
    private Map<String, Object> parameters;
    private List<String> requiredCapabilities;
    private int priority;
    private long timeoutMs;
    
    public ComplexRequest() {}
    
    public ComplexRequest(String id, String description, String type) {
        this.id = id;
        this.description = description;
        this.type = type;
        this.priority = 5; // Default priority
        this.timeoutMs = 300000L; // 5 minutes default
    }
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
    public List<String> getRequiredCapabilities() { return requiredCapabilities; }
    public void setRequiredCapabilities(List<String> requiredCapabilities) { this.requiredCapabilities = requiredCapabilities; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
}