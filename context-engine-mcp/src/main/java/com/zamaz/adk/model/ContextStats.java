package com.zamaz.adk.model;

/**
 * Statistics about context usage and performance
 */
public class ContextStats {
    private int totalMessages;
    private int totalTokens;
    private int averageMessageLength;
    private double compressionRatio;
    private long lastUpdated;
    
    public ContextStats() {
        this.lastUpdated = System.currentTimeMillis();
    }
    
    public ContextStats(int totalMessages, int totalTokens) {
        this();
        this.totalMessages = totalMessages;
        this.totalTokens = totalTokens;
        if (totalMessages > 0) {
            this.averageMessageLength = totalTokens / totalMessages;
        }
    }
    
    public int getTotalMessages() { return totalMessages; }
    public void setTotalMessages(int totalMessages) { this.totalMessages = totalMessages; }
    public int getTotalTokens() { return totalTokens; }
    public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }
    public int getAverageMessageLength() { return averageMessageLength; }
    public void setAverageMessageLength(int averageMessageLength) { this.averageMessageLength = averageMessageLength; }
    public double getCompressionRatio() { return compressionRatio; }
    public void setCompressionRatio(double compressionRatio) { this.compressionRatio = compressionRatio; }
    public long getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }
}