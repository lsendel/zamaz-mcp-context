package com.zamaz.adk.model;

/**
 * Enumeration of different agent types in the system
 */
public enum AgentType {
    CODE_ANALYZER("gemini-1.5-pro", "Specialized in code analysis and review"),
    DOCUMENT_WRITER("gemini-1.5-pro", "Creates technical documentation"),
    DATA_PROCESSOR("gemini-1.5-flash", "Processes and transforms data"),
    SEARCH_AGENT("gemini-1.5-flash", "Searches and retrieves information"),
    PLANNING_AGENT("gemini-1.5-pro", "Creates execution plans and strategies"),
    QUALITY_CHECKER("gemini-1.5-flash", "Validates outputs and checks quality"),
    CREATIVE_WRITER("gemini-1.5-pro", "Generates creative content"),
    CLASSIFIER("gemini-1.5-flash", "Classifies and categorizes content"),
    KEYWORD_EXTRACTOR("gemini-1.5-flash", "Extracts keywords and key phrases"),
    DECISION_MAKER("gemini-1.5-flash", "Makes decisions based on criteria");
    
    private final String preferredModel;
    private final String description;
    
    AgentType(String preferredModel, String description) {
        this.preferredModel = preferredModel;
        this.description = description;
    }
    
    public String getPreferredModel() { return preferredModel; }
    public String getDescription() { return description; }
}