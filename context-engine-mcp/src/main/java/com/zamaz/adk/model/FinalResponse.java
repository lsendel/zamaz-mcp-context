package com.zamaz.adk.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Represents the final consolidated response from multi-agent processing
 */
public class FinalResponse {
    private String id;
    private String requestId;
    private String content;
    private String status;
    private List<AgentResponse> agentResponses;
    private Map<String, Object> metadata;
    private Instant timestamp;
    private String error;
    private long processingTimeMs;
    
    public FinalResponse() {
        this.timestamp = Instant.now();
    }
    
    public FinalResponse(String requestId, String content, String status) {
        this();
        this.requestId = requestId;
        this.content = content;
        this.status = status;
    }
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<AgentResponse> getAgentResponses() { return agentResponses; }
    public void setAgentResponses(List<AgentResponse> agentResponses) { this.agentResponses = agentResponses; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public long getProcessingTimeMs() { return processingTimeMs; }
    public void setProcessingTimeMs(long processingTimeMs) { this.processingTimeMs = processingTimeMs; }
}