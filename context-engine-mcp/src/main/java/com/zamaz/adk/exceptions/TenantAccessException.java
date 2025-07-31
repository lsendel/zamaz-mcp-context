package com.zamaz.adk.exceptions;

import com.zamaz.adk.core.TenantContext;

/**
 * Exception thrown when tenant access violations occur
 */
public class TenantAccessException extends ADKException {
    
    public enum TenantErrorCode {
        TENANT_NOT_FOUND("TN001", "Tenant not found"),
        ACCESS_DENIED("TN002", "Access denied for tenant"),
        INSUFFICIENT_PERMISSIONS("TN003", "Insufficient tenant permissions"),
        TENANT_QUOTA_EXCEEDED("TN004", "Tenant quota exceeded"),
        TENANT_SUSPENDED("TN005", "Tenant suspended"),
        INVALID_TENANT_CONTEXT("TN006", "Invalid tenant context"),
        TENANT_ISOLATION_VIOLATION("TN007", "Tenant isolation violation"),
        TENANT_CONFIGURATION_ERROR("TN008", "Tenant configuration error");
        
        private final String code;
        private final String description;
        
        TenantErrorCode(String code, String description) {
            this.code = code;
            this.description = description;
        }
        
        public String getCode() { return code; }
        public String getDescription() { return description; }
    }
    
    private final TenantContext tenantContext;
    private final String requestedResource;
    private final String requiredPermission;
    
    public TenantAccessException(TenantErrorCode errorCode, TenantContext tenant, 
                               String requestedResource, String requiredPermission, 
                               String additionalInfo) {
        super(
            String.format("%s: %s (Tenant: %s, Resource: %s, Permission: %s)", 
                errorCode.getDescription(), additionalInfo, 
                tenant != null ? tenant.getTenantPath() : "unknown",
                requestedResource, requiredPermission),
            errorCode.getCode(),
            ErrorSeverity.WARNING,
            "TenantAccessControl",
            new TenantAccessContext(tenant, requestedResource, requiredPermission, additionalInfo)
        );
        this.tenantContext = tenant;
        this.requestedResource = requestedResource;
        this.requiredPermission = requiredPermission;
    }
    
    public TenantContext getTenantContext() { return tenantContext; }
    public String getRequestedResource() { return requestedResource; }
    public String getRequiredPermission() { return requiredPermission; }
    
    public static class TenantAccessContext {
        private final TenantContext tenantContext;
        private final String requestedResource;
        private final String requiredPermission;
        private final String additionalInfo;
        
        public TenantAccessContext(TenantContext tenantContext, String requestedResource, 
                                 String requiredPermission, String additionalInfo) {
            this.tenantContext = tenantContext;
            this.requestedResource = requestedResource;
            this.requiredPermission = requiredPermission;
            this.additionalInfo = additionalInfo;
        }
        
        // Getters
        public TenantContext getTenantContext() { return tenantContext; }
        public String getRequestedResource() { return requestedResource; }
        public String getRequiredPermission() { return requiredPermission; }
        public String getAdditionalInfo() { return additionalInfo; }
    }
}