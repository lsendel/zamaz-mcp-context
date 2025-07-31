package com.zamaz.adk.model;

import java.time.Instant;

/**
 * Represents a message in the agent communication system
 */
public class Message {
    private String id;
    private String content;
    private String role;
    private Instant timestamp;
    private String agentId;
    
    public Message() {
        this.timestamp = Instant.now();
    }
    
    public Message(String content, String role) {
        this();
        this.content = content;
        this.role = role;
    }
    
    public Message(String id, String content, String role, String agentId) {
        this();
        this.id = id;
        this.content = content;
        this.role = role;
        this.agentId = agentId;
    }
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
}