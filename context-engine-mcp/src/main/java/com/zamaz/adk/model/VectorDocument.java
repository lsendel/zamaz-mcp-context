package com.zamaz.adk.model;

import java.util.Map;

/**
 * Represents a document in the vector store
 */
public class VectorDocument {
    private String id;
    private String content;
    private float[] embedding;
    private Map<String, Object> metadata;
    private String tenantId;
    private long timestamp;
    
    public VectorDocument() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public VectorDocument(String id, String content, float[] embedding) {
        this();
        this.id = id;
        this.content = content;
        this.embedding = embedding;
    }
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public float[] getEmbedding() { return embedding; }
    public void setEmbedding(float[] embedding) { this.embedding = embedding; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}