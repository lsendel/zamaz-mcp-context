package com.zamaz.adk.agents;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.pubsub.v1.TopicName;
import com.zamaz.adk.core.TenantAwareService;
import com.zamaz.adk.core.TenantContext;
import com.zamaz.adk.proto.AgentType;

import java.util.*;
import java.util.concurrent.*;

/**
 * Tenant-aware Multi-Agent Orchestrator
 * Provides isolated agent execution per tenant with quota management
 */
public class TenantAwareMultiAgentOrchestrator extends TenantAwareService {
    private final Map<String, TenantAgentRegistry> tenantRegistries = new ConcurrentHashMap<>();
    private final ExecutorService agentExecutor;
    
    public TenantAwareMultiAgentOrchestrator(Firestore firestore, String projectId, String location) {
        super(projectId, location, firestore);
        this.agentExecutor = Executors.newWorkStealingPool();
    }
    
    /**
     * Registry for tenant-specific agents
     */
    private static class TenantAgentRegistry {
        private final Map<AgentType, MultiAgentOrchestrator.Agent> agents = new ConcurrentHashMap<>();
        private final Map<String, MultiAgentOrchestrator.ContextWindow> contextWindows = new ConcurrentHashMap<>();
        
        public void registerAgent(MultiAgentOrchestrator.Agent agent) {
            agents.put(agent.getType(), agent);
            contextWindows.put(agent.getId(), agent.getContext());
        }
        
        public MultiAgentOrchestrator.Agent getAgent(AgentType type) {
            return agents.get(type);
        }
        
        public Collection<MultiAgentOrchestrator.Agent> getAllAgents() {
            return agents.values();
        }
        
        public void clearAgentContext(AgentType type) {
            MultiAgentOrchestrator.Agent agent = agents.get(type);
            if (agent != null) {
                agent.clearContext();
            }
        }
    }
    
    /**
     * Get or create tenant registry
     */
    private TenantAgentRegistry getTenantRegistry(TenantContext tenant) {
        return tenantRegistries.computeIfAbsent(tenant.getTenantPath(), k -> {
            TenantAgentRegistry registry = new TenantAgentRegistry();
            initializeTenantAgents(tenant, registry);
            return registry;
        });
    }
    
    /**
     * Initialize agents for a tenant
     */
    private void initializeTenantAgents(TenantContext tenant, TenantAgentRegistry registry) {
        String tenantProjectId = getTenantProjectId(tenant);
        
        // Create tenant-specific publisher
        Publisher eventPublisher = null;
        try {
            String topicName = tenant.getTopicName("agent-events");
            eventPublisher = Publisher.newBuilder(
                TopicName.of(tenantProjectId, topicName)
            ).build();
        } catch (Exception e) {
            logger.warn("Failed to create event publisher for tenant {}", tenant, e);
        }
        
        // Initialize all agent types for tenant
        for (AgentType type : AgentType.values()) {
            MultiAgentOrchestrator.Agent agent = new MultiAgentOrchestrator.Agent(
                tenant.getTenantPath() + "_" + type.name().toLowerCase(),
                type,
                tenantProjectId,
                location
            );
            registry.registerAgent(agent);
        }
        
        auditLog(tenant, "agents.initialized", 
            String.format("Initialized %d agents", AgentType.values().length));
    }
    
    /**
     * Orchestrate a complex request for a tenant
     */
    public CompletableFuture<TenantFinalResponse> orchestrate(
            TenantContext tenant, TenantComplexRequest request) {
        
        // Validate tenant quota
        validateTenantQuota(tenant, "agent_requests");
        
        // Get tenant registry
        TenantAgentRegistry registry = getTenantRegistry(tenant);
        
        // Create supervisor for this tenant
        TenantSupervisorAgent supervisor = new TenantSupervisorAgent(
            tenant, getTenantProjectId(tenant), location);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Analyze request and create execution plan
                MultiAgentOrchestrator.ExecutionPlan plan = supervisor.analyzeTasks(request);
                
                // Execute tasks in parallel with tenant isolation
                Map<String, CompletableFuture<MultiAgentOrchestrator.AgentResponse>> futures = 
                    new HashMap<>();
                
                for (MultiAgentOrchestrator.Task task : plan.getTasks()) {
                    MultiAgentOrchestrator.Agent agent = registry.getAgent(task.getAgentType());
                    if (agent == null) {
                        logger.warn("Agent not found for type: {} in tenant: {}", 
                            task.getAgentType(), tenant);
                        continue;
                    }
                    
                    // Create isolated context for this task
                    Map<String, Object> taskContext = new HashMap<>(request.getContext());
                    taskContext.put("task_id", task.getId());
                    taskContext.put("tenant", tenant.getTenantPath());
                    
                    futures.put(task.getId(), agent.execute(task, taskContext));
                }
                
                // Wait for all tasks to complete
                CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0]))
                    .get(30, TimeUnit.SECONDS);
                
                // Collect responses
                Map<String, MultiAgentOrchestrator.AgentResponse> agentResponses = new HashMap<>();
                futures.forEach((taskId, future) -> {
                    try {
                        agentResponses.put(taskId, future.get());
                    } catch (Exception e) {
                        logger.error("Task {} failed for tenant {}", taskId, tenant, e);
                    }
                });
                
                // Combine results
                TenantFinalResponse finalResponse = supervisor.combineResults(
                    agentResponses, plan.getCombinationStrategy());
                
                // Update quota usage
                updateQuotaUsage(tenant, "agent_requests", 1);
                
                // Audit log
                auditLog(tenant, "orchestration.complete", 
                    String.format("Request: %s, Agents: %d, Latency: %dms",
                        request.getRequestId(),
                        agentResponses.size(),
                        finalResponse.getTotalLatency()));
                
                return finalResponse;
                
            } catch (Exception e) {
                logger.error("Orchestration failed for tenant {}", tenant, e);
                throw new RuntimeException("Orchestration failed", e);
            }
        }, agentExecutor);
    }
    
    /**
     * Get agent info for a tenant
     */
    public MultiAgentOrchestrator.Agent getAgent(TenantContext tenant, AgentType type) {
        TenantAgentRegistry registry = getTenantRegistry(tenant);
        return registry.getAgent(type);
    }
    
    /**
     * Clear agent context for a tenant
     */
    public void clearAgentContext(TenantContext tenant, AgentType type) {
        TenantAgentRegistry registry = getTenantRegistry(tenant);
        registry.clearAgentContext(type);
        
        auditLog(tenant, "agent.context.cleared", 
            String.format("Agent: %s", type.name()));
    }
    
    /**
     * Validate tenant quota for agent operations
     */
    private void validateTenantQuota(TenantContext tenant, String quotaType) {
        TenantConfiguration config = getTenantConfiguration(tenant);
        
        // Check request limit based on tier
        long dailyLimit = getAgentRequestLimitForTier(config.getTier());
        long currentUsage = getCurrentQuotaUsage(tenant, quotaType);
        
        if (currentUsage >= dailyLimit) {
            throw new QuotaExceededException(String.format(
                "Daily agent request limit exceeded for tenant %s (limit: %d)",
                tenant.getTenantPath(), dailyLimit
            ));
        }
    }
    
    /**
     * Get current quota usage
     */
    private long getCurrentQuotaUsage(TenantContext tenant, String quotaType) {
        // In production, would query from Firestore
        return 0L;
    }
    
    /**
     * Update quota usage
     */
    private void updateQuotaUsage(TenantContext tenant, String quotaType, long increment) {
        // In production, would update in Firestore
        logger.debug("Updated quota {} for tenant {} by {}", 
            quotaType, tenant.getTenantPath(), increment);
    }
    
    /**
     * Get agent request limit by tier
     */
    private long getAgentRequestLimitForTier(String tier) {
        switch (tier) {
            case "enterprise":
                return 100000;
            case "standard":
                return 10000;
            case "free":
                return 1000;
            default:
                return 100;
        }
    }
    
    public void shutdown() {
        agentExecutor.shutdown();
        try {
            if (!agentExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                agentExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            agentExecutor.shutdownNow();
        }
    }
    
    /**
     * Quota exceeded exception
     */
    public static class QuotaExceededException extends RuntimeException {
        public QuotaExceededException(String message) {
            super(message);
        }
    }
}

/**
 * Tenant-aware complex request
 */
class TenantComplexRequest extends MultiAgentOrchestrator.ComplexRequest {
    private final TenantContext tenant;
    
    public TenantComplexRequest(TenantContext tenant, String requestId, 
                               String description, Map<String, Object> context,
                               List<AgentType> preferredAgents) {
        super(requestId, description, context, preferredAgents);
        this.tenant = tenant;
    }
    
    public TenantContext getTenant() {
        return tenant;
    }
}

/**
 * Tenant-aware final response
 */
class TenantFinalResponse extends MultiAgentOrchestrator.FinalResponse {
    private final TenantContext tenant;
    private final long totalLatency;
    
    public TenantFinalResponse(TenantContext tenant, String content, 
                              Map<String, MultiAgentOrchestrator.AgentResponse> agentResponses,
                              double confidence) {
        super(content, agentResponses, confidence);
        this.tenant = tenant;
        this.totalLatency = agentResponses.values().stream()
            .mapToLong(MultiAgentOrchestrator.AgentResponse::getLatencyMs)
            .sum();
    }
    
    public TenantContext getTenant() {
        return tenant;
    }
    
    public long getTotalLatency() {
        return totalLatency;
    }
}

/**
 * Tenant-aware supervisor agent
 */
class TenantSupervisorAgent {
    private final TenantContext tenant;
    private final MultiAgentOrchestrator.VertexAIEndpoint endpoint;
    
    public TenantSupervisorAgent(TenantContext tenant, String projectId, String location) {
        this.tenant = tenant;
        // TODO: Make model configurable through ADKConfigurationProperties
        // For now using default model, but should be: config.getAi().getModels().getGemini().getPro().getName()
        this.endpoint = new MultiAgentOrchestrator.VertexAIEndpoint(projectId, location, "gemini-1.5-pro");
    }
    
    public MultiAgentOrchestrator.ExecutionPlan analyzeTasks(TenantComplexRequest request) {
        // Implementation would analyze request and create execution plan
        return new MultiAgentOrchestrator.ExecutionPlan(
            UUID.randomUUID().toString(),
            new ArrayList<>(),
            new HashMap<>(),
            "weighted_average"
        );
    }
    
    public TenantFinalResponse combineResults(
            Map<String, MultiAgentOrchestrator.AgentResponse> responses,
            String strategy) {
        // Implementation would combine results based on strategy
        String combined = responses.values().stream()
            .map(MultiAgentOrchestrator.AgentResponse::getResponse)
            .reduce("", (a, b) -> a + "\n" + b);
        
        double avgConfidence = responses.values().stream()
            .mapToDouble(MultiAgentOrchestrator.AgentResponse::getConfidence)
            .average()
            .orElse(0.5);
        
        return new TenantFinalResponse(tenant, combined, responses, avgConfidence);
    }
}