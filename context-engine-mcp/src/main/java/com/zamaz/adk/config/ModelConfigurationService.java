package com.zamaz.adk.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Centralized service for model configuration to eliminate hardcoded model names
 */
@Service
public class ModelConfigurationService {
    
    private final ADKConfigurationProperties config;
    
    @Autowired
    public ModelConfigurationService(ADKConfigurationProperties config) {
        this.config = config;
    }
    
    /**
     * Get the appropriate model based on task complexity and requirements
     */
    public String getModelForTask(TaskType taskType, ComplexityLevel complexity) {
        switch (taskType) {
            case CODE_ANALYSIS:
            case DOCUMENT_WRITING:
            case PLANNING:
                return complexity == ComplexityLevel.HIGH ? 
                    config.getAi().getModels().getGemini().getPro().getName() :
                    config.getAi().getModels().getGemini().getFlash().getName();
                    
            case DATA_PROCESSING:
            case SEARCH:
            case QUALITY_CHECK:
                return config.getAi().getModels().getGemini().getFlash().getName();
                
            case DECISION_MAKING:
                return config.getAi().getModels().getGemini().getDecision().getName();
                
            case CLASSIFICATION:
                return config.getAi().getModels().getGemini().getClassification().getName();
                
            case KEYWORD_EXTRACTION:
                return config.getAi().getModels().getGemini().getKeywordExtraction().getName();
                
            case CREATIVE_WRITING:
                return config.getAi().getModels().getGemini().getCreative().getName();
                
            default:
                return config.getAi().getModels().getGemini().getFlash().getName();
        }
    }
    
    /**
     * Get model by specific use case
     */
    public String getFlashModel() {
        return config.getAi().getModels().getGemini().getFlash().getName();
    }
    
    public String getProModel() {
        return config.getAi().getModels().getGemini().getPro().getName();
    }
    
    public String getDecisionModel() {
        return config.getAi().getModels().getGemini().getDecision().getName();
    }
    
    public String getClassificationModel() {
        return config.getAi().getModels().getGemini().getClassification().getName();
    }
    
    public String getCreativeModel() {
        return config.getAi().getModels().getGemini().getCreative().getName();
    }
    
    public String getKeywordExtractionModel() {
        return config.getAi().getModels().getGemini().getKeywordExtraction().getName();
    }
    
    /**
     * Get model configuration parameters
     */
    public Double getTemperatureForModel(String modelName) {
        ADKConfigurationProperties.Ai.Models.Gemini gemini = config.getAi().getModels().getGemini();
        
        if (modelName.equals(gemini.getPro().getName())) {
            return gemini.getPro().getTemperature();
        } else if (modelName.equals(gemini.getFlash().getName())) {
            return gemini.getFlash().getTemperature();
        } else if (modelName.equals(gemini.getDecision().getName())) {
            return gemini.getDecision().getTemperature();
        } else if (modelName.equals(gemini.getClassification().getName())) {
            return gemini.getClassification().getTemperature();
        } else if (modelName.equals(gemini.getCreative().getName())) {
            return gemini.getCreative().getTemperature();
        } else if (modelName.equals(gemini.getKeywordExtraction().getName())) {
            return gemini.getKeywordExtraction().getTemperature();
        } else {
            return gemini.getFlash().getTemperature(); // Default
        }
    }
    
    public Integer getMaxTokensForModel(String modelName) {
        ADKConfigurationProperties.Ai.Models.Gemini gemini = config.getAi().getModels().getGemini();
        
        if (modelName.equals(gemini.getPro().getName())) {
            return gemini.getPro().getMaxOutputTokens();
        } else if (modelName.equals(gemini.getFlash().getName())) {
            return gemini.getFlash().getMaxOutputTokens();
        } else if (modelName.equals(gemini.getDecision().getName())) {
            return gemini.getDecision().getMaxOutputTokens();
        } else if (modelName.equals(gemini.getClassification().getName())) {
            return gemini.getClassification().getMaxOutputTokens();
        } else if (modelName.equals(gemini.getCreative().getName())) {
            return gemini.getCreative().getMaxOutputTokens();
        } else if (modelName.equals(gemini.getKeywordExtraction().getName())) {
            return gemini.getKeywordExtraction().getMaxOutputTokens();
        } else {
            return gemini.getFlash().getMaxOutputTokens(); // Default
        }
    }
    
    /**
     * Optimize model selection based on request characteristics
     */
    public String optimizeModelSelection(String prompt, int estimatedOutputLength, double requiredAccuracy) {
        // Simple decision logic - can be enhanced with ML-based selection
        if (estimatedOutputLength > 1000 || requiredAccuracy > 0.9) {
            return getProModel();
        } else if (prompt.toLowerCase().contains("decision") || 
                   prompt.toLowerCase().contains("choose") ||
                   prompt.toLowerCase().contains("select")) {
            return getDecisionModel();
        } else if (prompt.toLowerCase().contains("classify") ||
                   prompt.toLowerCase().contains("categorize")) {
            return getClassificationModel();
        } else if (prompt.toLowerCase().contains("creative") ||
                   prompt.toLowerCase().contains("story") ||
                   prompt.toLowerCase().contains("poem")) {
            return getCreativeModel();
        } else {
            return getFlashModel(); // Default for fast, simple queries
        }
    }
    
    public enum TaskType {
        CODE_ANALYSIS,
        DOCUMENT_WRITING,
        DATA_PROCESSING,
        SEARCH,
        PLANNING,
        QUALITY_CHECK,
        DECISION_MAKING,
        CLASSIFICATION,
        KEYWORD_EXTRACTION,
        CREATIVE_WRITING
    }
    
    public enum ComplexityLevel {
        LOW,
        MEDIUM,
        HIGH
    }
}