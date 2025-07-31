package com.zamaz.adk.exceptions;

import com.zamaz.adk.core.TenantContext;

/**
 * Exception thrown during agent orchestration failures
 */
public class AgentOrchestrationException extends ADKException {
    
    public enum AgentErrorCode {
        AGENT_NOT_FOUND("AG001", "Agent not found"),
        AGENT_INITIALIZATION_FAILED("AG002", "Agent initialization failed"),
        AGENT_COMMUNICATION_FAILED("AG003", "Agent communication failed"),
        INVALID_AGENT_CONFIGURATION("AG004", "Invalid agent configuration"),
        AGENT_CAPACITY_EXCEEDED("AG005", "Agent capacity exceeded"),
        ORCHESTRATION_TIMEOUT("AG006", "Orchestration timeout"),
        AGENT_CONTEXT_CORRUPTION("AG007", "Agent context corruption"),
        SUPERVISOR_FAILURE("AG008", "Supervisor agent failure");
        
        private final String code;
        private final String description;
        
        AgentErrorCode(String code, String description) {
            this.code = code;
            this.description = description;
        }
        
        public String getCode() { return code; }
        public String getDescription() { return description; }
    }
    
    private final String agentId;
    private final String requestId;
    private final TenantContext tenantContext;
    
    public AgentOrchestrationException(AgentErrorCode errorCode, String agentId, 
                                     String requestId, TenantContext tenant, 
                                     String additionalInfo) {
        super(
            String.format("%s: %s (Agent: %s, Request: %s, Tenant: %s)", 
                errorCode.getDescription(), additionalInfo, agentId, requestId, 
                tenant != null ? tenant.getTenantPath() : "unknown"),
            errorCode.getCode(),
            ErrorSeverity.ERROR,
            "AgentOrchestrator",
            new AgentContext(agentId, requestId, tenant, additionalInfo)
        );
        this.agentId = agentId;
        this.requestId = requestId;
        this.tenantContext = tenant;
    }
    
    public AgentOrchestrationException(AgentErrorCode errorCode, String agentId, 
                                     TenantContext tenant, Throwable cause) {
        super(
            String.format("%s (Agent: %s, Tenant: %s)", 
                errorCode.getDescription(), agentId, 
                tenant != null ? tenant.getTenantPath() : "unknown"),
            cause,
            errorCode.getCode(),
            ErrorSeverity.ERROR,
            "AgentOrchestrator",
            new AgentContext(agentId, null, tenant, cause.getMessage())
        );
        this.agentId = agentId;
        this.requestId = null;
        this.tenantContext = tenant;
    }
    
    public String getAgentId() { return agentId; }
    public String getRequestId() { return requestId; }
    public TenantContext getTenantContext() { return tenantContext; }
    
    public static class AgentContext {
        private final String agentId;
        private final String requestId;
        private final TenantContext tenantContext;
        private final String additionalInfo;
        
        public AgentContext(String agentId, String requestId, TenantContext tenantContext, 
                          String additionalInfo) {
            this.agentId = agentId;
            this.requestId = requestId;
            this.tenantContext = tenantContext;
            this.additionalInfo = additionalInfo;
        }
        
        // Getters
        public String getAgentId() { return agentId; }
        public String getRequestId() { return requestId; }
        public TenantContext getTenantContext() { return tenantContext; }
        public String getAdditionalInfo() { return additionalInfo; }
    }
}