package com.zamaz.adk.model;

/**
 * Represents a Vertex AI endpoint configuration
 */
public class VertexAIEndpoint {
    private String projectId;
    private String location;
    private String endpointId;
    private String modelName;
    private boolean enabled;
    
    public VertexAIEndpoint() {
        this.enabled = true;
    }
    
    public VertexAIEndpoint(String projectId, String location, String modelName) {
        this();
        this.projectId = projectId;
        this.location = location;
        this.modelName = modelName;
    }
    
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getEndpointId() { return endpointId; }
    public void setEndpointId(String endpointId) { this.endpointId = endpointId; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}