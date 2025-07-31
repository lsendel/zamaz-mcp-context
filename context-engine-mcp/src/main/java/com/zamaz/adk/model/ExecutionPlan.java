package com.zamaz.adk.model;

import java.util.List;
import java.util.Map;

/**
 * Represents an execution plan for processing a complex request
 */
public class ExecutionPlan {
    private String id;
    private String requestId;
    private List<ExecutionStep> steps;
    private Map<String, Object> context;
    private String status;
    private long estimatedDurationMs;
    
    public ExecutionPlan() {}
    
    public ExecutionPlan(String id, String requestId, List<ExecutionStep> steps) {
        this.id = id;
        this.requestId = requestId;
        this.steps = steps;
        this.status = "CREATED";
    }
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public List<ExecutionStep> getSteps() { return steps; }
    public void setSteps(List<ExecutionStep> steps) { this.steps = steps; }
    public Map<String, Object> getContext() { return context; }
    public void setContext(Map<String, Object> context) { this.context = context; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getEstimatedDurationMs() { return estimatedDurationMs; }
    public void setEstimatedDurationMs(long estimatedDurationMs) { this.estimatedDurationMs = estimatedDurationMs; }
    
    public static class ExecutionStep {
        private String id;
        private AgentType agentType;
        private String action;
        private Map<String, Object> parameters;
        private List<String> dependencies;
        private String status;
        
        public ExecutionStep() {}
        
        public ExecutionStep(String id, AgentType agentType, String action) {
            this.id = id;
            this.agentType = agentType;
            this.action = action;
            this.status = "PENDING";
        }
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public AgentType getAgentType() { return agentType; }
        public void setAgentType(AgentType agentType) { this.agentType = agentType; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
        public List<String> getDependencies() { return dependencies; }
        public void setDependencies(List<String> dependencies) { this.dependencies = dependencies; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}