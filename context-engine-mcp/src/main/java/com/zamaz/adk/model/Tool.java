package com.zamaz.adk.model;

import java.util.Map;

/**
 * Represents a tool that can be used by agents
 */
public class Tool {
    private String name;
    private String description;
    private String category;
    private Map<String, Object> inputSchema;
    private Map<String, Object> metadata;
    private double popularity;
    private boolean enabled;
    
    public Tool() {
        this.enabled = true;
        this.popularity = 0.5;
    }
    
    public Tool(String name, String description, String category) {
        this();
        this.name = name;
        this.description = description;
        this.category = category;
    }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Map<String, Object> getInputSchema() { return inputSchema; }
    public void setInputSchema(Map<String, Object> inputSchema) { this.inputSchema = inputSchema; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public double getPopularity() { return popularity; }
    public void setPopularity(double popularity) { this.popularity = popularity; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}