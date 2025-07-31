package com.zamaz.adk.exceptions;

import com.zamaz.adk.core.TenantContext;

/**
 * Exception thrown during workflow execution failures
 */
public class WorkflowExecutionException extends ADKException {
    
    public enum WorkflowErrorCode {
        WORKFLOW_NOT_FOUND("WF001", "Workflow not found"),
        INVALID_WORKFLOW_STATE("WF002", "Invalid workflow state"),
        NODE_EXECUTION_FAILED("WF003", "Node execution failed"),
        CONDITION_EVALUATION_FAILED("WF004", "Condition evaluation failed"),
        WORKFLOW_TIMEOUT("WF005", "Workflow execution timeout"),
        INSUFFICIENT_RESOURCES("WF006", "Insufficient resources"),
        TENANT_ACCESS_DENIED("WF007", "Tenant access denied"),
        WORKFLOW_VALIDATION_FAILED("WF008", "Workflow validation failed");
        
        private final String code;
        private final String description;
        
        WorkflowErrorCode(String code, String description) {
            this.code = code;
            this.description = description;
        }
        
        public String getCode() { return code; }
        public String getDescription() { return description; }
    }
    
    private final String workflowId;
    private final String executionId;
    private final String nodeId;
    private final TenantContext tenantContext;
    
    public WorkflowExecutionException(WorkflowErrorCode errorCode, String workflowId, 
                                    String executionId, String nodeId, 
                                    TenantContext tenant, String additionalInfo) {
        super(
            String.format("%s: %s (Workflow: %s, Execution: %s, Node: %s, Tenant: %s)", 
                errorCode.getDescription(), additionalInfo, workflowId, executionId, 
                nodeId, tenant != null ? tenant.getTenantPath() : "unknown"),
            errorCode.getCode(),
            ErrorSeverity.ERROR,
            "WorkflowEngine",
            new WorkflowContext(workflowId, executionId, nodeId, tenant, additionalInfo)
        );
        this.workflowId = workflowId;
        this.executionId = executionId;
        this.nodeId = nodeId;
        this.tenantContext = tenant;
    }
    
    public WorkflowExecutionException(WorkflowErrorCode errorCode, String workflowId, 
                                    String executionId, TenantContext tenant, 
                                    Throwable cause) {
        super(
            String.format("%s (Workflow: %s, Execution: %s, Tenant: %s)", 
                errorCode.getDescription(), workflowId, executionId, 
                tenant != null ? tenant.getTenantPath() : "unknown"),
            cause,
            errorCode.getCode(),
            ErrorSeverity.ERROR,
            "WorkflowEngine",
            new WorkflowContext(workflowId, executionId, null, tenant, cause.getMessage())
        );
        this.workflowId = workflowId;
        this.executionId = executionId;
        this.nodeId = null;
        this.tenantContext = tenant;
    }
    
    public String getWorkflowId() { return workflowId; }
    public String getExecutionId() { return executionId; }
    public String getNodeId() { return nodeId; }
    public TenantContext getTenantContext() { return tenantContext; }
    
    public static class WorkflowContext {
        private final String workflowId;
        private final String executionId;
        private final String nodeId;
        private final TenantContext tenantContext;
        private final String additionalInfo;
        
        public WorkflowContext(String workflowId, String executionId, String nodeId, 
                             TenantContext tenantContext, String additionalInfo) {
            this.workflowId = workflowId;
            this.executionId = executionId;
            this.nodeId = nodeId;
            this.tenantContext = tenantContext;
            this.additionalInfo = additionalInfo;
        }
        
        // Getters
        public String getWorkflowId() { return workflowId; }
        public String getExecutionId() { return executionId; }
        public String getNodeId() { return nodeId; }
        public TenantContext getTenantContext() { return tenantContext; }
        public String getAdditionalInfo() { return additionalInfo; }
    }
}