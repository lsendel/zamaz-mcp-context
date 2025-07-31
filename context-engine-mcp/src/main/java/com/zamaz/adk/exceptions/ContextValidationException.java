package com.zamaz.adk.exceptions;

import com.zamaz.adk.core.TenantContext;

/**
 * Exception thrown when context validation failures occur
 */
public class ContextValidationException extends ADKException {
    
    public enum ContextErrorCode {
        INVALID_CONTEXT_FORMAT("CX001", "Invalid context format"),
        CONTEXT_SIZE_EXCEEDED("CX002", "Context size exceeded limits"),
        CONTEXT_QUALITY_TOO_LOW("CX003", "Context quality below threshold"),
        MISSING_REQUIRED_CONTEXT("CX004", "Missing required context"),
        CONTEXT_CORRUPTION_DETECTED("CX005", "Context corruption detected"),
        CONTEXT_SECURITY_VIOLATION("CX006", "Context security violation"),
        CONTEXT_EMBEDDING_FAILED("CX007", "Context embedding generation failed"),
        CONTEXT_RETRIEVAL_FAILED("CX008", "Context retrieval failed");
        
        private final String code;
        private final String description;
        
        ContextErrorCode(String code, String description) {
            this.code = code;
            this.description = description;
        }
        
        public String getCode() { return code; }
        public String getDescription() { return description; }
    }
    
    private final String contextId;
    private final TenantContext tenantContext;
    private final String validationRule;
    private final double qualityScore;
    
    public ContextValidationException(ContextErrorCode errorCode, String contextId, 
                                    TenantContext tenant, String validationRule, 
                                    double qualityScore, String additionalInfo) {
        super(
            String.format("%s: %s (Context: %s, Tenant: %s, Rule: %s, Quality: %.2f)", 
                errorCode.getDescription(), additionalInfo, contextId, 
                tenant != null ? tenant.getTenantPath() : "unknown",
                validationRule, qualityScore),
            errorCode.getCode(),
            determineContextSeverity(errorCode, qualityScore),
            "ContextValidator",
            new ContextValidationContext(contextId, tenant, validationRule, qualityScore, additionalInfo)
        );
        this.contextId = contextId;
        this.tenantContext = tenant;
        this.validationRule = validationRule;
        this.qualityScore = qualityScore;
    }
    
    public ContextValidationException(ContextErrorCode errorCode, String contextId, 
                                    TenantContext tenant, Throwable cause) {
        super(
            String.format("%s (Context: %s, Tenant: %s)", 
                errorCode.getDescription(), contextId, 
                tenant != null ? tenant.getTenantPath() : "unknown"),
            cause,
            errorCode.getCode(),
            ErrorSeverity.ERROR,
            "ContextValidator",
            new ContextValidationContext(contextId, tenant, null, -1.0, cause.getMessage())
        );
        this.contextId = contextId;
        this.tenantContext = tenant;
        this.validationRule = null;
        this.qualityScore = -1.0;
    }
    
    private static ErrorSeverity determineContextSeverity(ContextErrorCode errorCode, double qualityScore) {
        switch (errorCode) {
            case CONTEXT_SECURITY_VIOLATION:
            case CONTEXT_CORRUPTION_DETECTED:
                return ErrorSeverity.CRITICAL;
            case CONTEXT_SIZE_EXCEEDED:
            case INVALID_CONTEXT_FORMAT:
                return ErrorSeverity.ERROR;
            case CONTEXT_QUALITY_TOO_LOW:
                return qualityScore < 0.3 ? ErrorSeverity.ERROR : ErrorSeverity.WARNING;
            default:
                return ErrorSeverity.WARNING;
        }
    }
    
    public String getContextId() { return contextId; }
    public TenantContext getTenantContext() { return tenantContext; }
    public String getValidationRule() { return validationRule; }
    public double getQualityScore() { return qualityScore; }
    
    public static class ContextValidationContext {
        private final String contextId;
        private final TenantContext tenantContext;
        private final String validationRule;
        private final double qualityScore;
        private final String additionalInfo;
        
        public ContextValidationContext(String contextId, TenantContext tenantContext, 
                                      String validationRule, double qualityScore, 
                                      String additionalInfo) {
            this.contextId = contextId;
            this.tenantContext = tenantContext;
            this.validationRule = validationRule;
            this.qualityScore = qualityScore;
            this.additionalInfo = additionalInfo;
        }
        
        // Getters
        public String getContextId() { return contextId; }
        public TenantContext getTenantContext() { return tenantContext; }
        public String getValidationRule() { return validationRule; }
        public double getQualityScore() { return qualityScore; }
        public String getAdditionalInfo() { return additionalInfo; }
    }
}