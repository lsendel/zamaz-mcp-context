package com.zamaz.adk.model;

import java.time.Instant;
import java.util.Map;

/**
 * Represents a response from an agent
 */
public class AgentResponse {
    private String id;
    private String agentId;
    private AgentType agentType;
    private String requestId;
    private String content;
    private String status;
    private Map<String, Object> metadata;
    private Instant timestamp;
    private String error;
    
    public AgentResponse() {
        this.timestamp = Instant.now();
    }
    
    public AgentResponse(String agentId, AgentType agentType, String requestId, String content) {
        this();
        this.agentId = agentId;
        this.agentType = agentType;
        this.requestId = requestId;
        this.content = content;
        this.status = "SUCCESS";
    }
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public AgentType getAgentType() { return agentType; }
    public void setAgentType(AgentType agentType) { this.agentType = agentType; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}