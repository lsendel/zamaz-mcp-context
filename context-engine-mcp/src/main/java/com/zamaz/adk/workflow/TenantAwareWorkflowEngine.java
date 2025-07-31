package com.zamaz.adk.workflow;

import com.google.cloud.aiplatform.v1.*;
import com.google.cloud.firestore.Firestore;
import com.google.protobuf.Value;
import com.zamaz.adk.core.TenantAwareService;
import com.zamaz.adk.core.TenantContext;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Tenant-aware Workflow Engine for Google ADK
 * Provides isolated workflow execution per organization/project/subproject
 */
public class TenantAwareWorkflowEngine extends TenantAwareService {
    private final Map<String, TenantWorkflowRegistry> tenantRegistries = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    
    public TenantAwareWorkflowEngine(Firestore firestore, String projectId, String location) {
        super(projectId, location, firestore);
        this.executor = Executors.newWorkStealingPool();
    }
    
    /**
     * Registry for tenant-specific workflows
     */
    private static class TenantWorkflowRegistry {
        private final Map<String, Map<String, WorkflowNode>> workflows = new ConcurrentHashMap<>();
        private final Map<String, Map<String, List<Edge>>> workflowEdges = new ConcurrentHashMap<>();
        
        public void registerWorkflow(String workflowId, Map<String, WorkflowNode> nodes, 
                                   Map<String, List<Edge>> edges) {
            workflows.put(workflowId, nodes);
            workflowEdges.put(workflowId, edges);
        }
        
        public Map<String, WorkflowNode> getNodes(String workflowId) {
            return workflows.get(workflowId);
        }
        
        public Map<String, List<Edge>> getEdges(String workflowId) {
            return workflowEdges.get(workflowId);
        }
        
        public boolean hasWorkflow(String workflowId) {
            return workflows.containsKey(workflowId);
        }
        
        public Set<String> listWorkflows() {
            return new HashSet<>(workflows.keySet());
        }
    }
    
    /**
     * Get or create tenant registry
     */
    private TenantWorkflowRegistry getTenantRegistry(TenantContext tenant) {
        return tenantRegistries.computeIfAbsent(tenant.getTenantPath(), 
            k -> new TenantWorkflowRegistry());
    }
    
    /**
     * Register a workflow for a tenant
     */
    public String registerWorkflow(TenantContext tenant, String workflowName,
                                 Map<String, WorkflowNode> nodes,
                                 Map<String, List<Edge>> edges) {
        // Validate tenant quota
        validateTenantQuota(tenant);
        
        // Generate workflow ID
        String workflowId = String.format("%s_%s_%s", 
            tenant.getTenantPath().replace("/", "_"), 
            workflowName, 
            UUID.randomUUID().toString().substring(0, 8));
        
        // Register in tenant registry
        TenantWorkflowRegistry registry = getTenantRegistry(tenant);
        registry.registerWorkflow(workflowId, nodes, edges);
        
        // Store workflow definition in Firestore
        storeWorkflowDefinition(tenant, workflowId, workflowName, nodes, edges);
        
        // Audit log
        auditLog(tenant, "workflow.register", 
            String.format("Registered workflow: %s (ID: %s)", workflowName, workflowId));
        
        return workflowId;
    }
    
    /**
     * Execute workflow with tenant isolation
     */
    public CompletableFuture<State> execute(TenantContext tenant, String workflowId, 
                                          String startNode, State initialState) {
        // Validate access
        validateWorkflowAccess(tenant, workflowId);
        
        // Get tenant resources
        TenantWorkflowRegistry registry = getTenantRegistry(tenant);
        if (!registry.hasWorkflow(workflowId)) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Workflow not found: " + workflowId));
        }
        
        // Get workflow components
        Map<String, WorkflowNode> nodes = registry.getNodes(workflowId);
        Map<String, List<Edge>> edges = registry.getEdges(workflowId);
        
        // Get tenant-specific clients
        StateStore stateStore = getTenantResource(tenant, "stateStore", 
            t -> new TenantStateStore(firestore, t));
        VertexAIClient vertexClient = getTenantResource(tenant, "vertexClient",
            t -> new VertexAIClient(getTenantProjectId(t), location));
        
        // Set tenant context in state
        initialState.setTenantContext(tenant);
        initialState.setWorkflowId(workflowId);
        
        // Execute workflow
        return executeWorkflow(tenant, nodes, edges, startNode, initialState, 
            stateStore, vertexClient);
    }
    
    /**
     * Internal workflow execution
     */
    private CompletableFuture<State> executeWorkflow(
            TenantContext tenant,
            Map<String, WorkflowNode> nodes,
            Map<String, List<Edge>> edges,
            String currentNodeId,
            State state,
            StateStore stateStore,
            VertexAIClient vertexClient) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Save initial state
                stateStore.saveState(state);
                
                // Execute nodes
                State currentState = state;
                String nodeId = currentNodeId;
                
                while (nodeId != null && !nodeId.equals("end")) {
                    // Check tenant quota
                    checkAndUpdateQuota(tenant);
                    
                    // Get node
                    WorkflowNode node = nodes.get(nodeId);
                    if (node == null) {
                        throw new IllegalStateException("Node not found: " + nodeId);
                    }
                    
                    // Execute node
                    currentState.recordExecution(nodeId);
                    CompletableFuture<State> nodeFuture = node.process(currentState, vertexClient);
                    currentState = nodeFuture.get(30, TimeUnit.SECONDS);
                    
                    // Save intermediate state
                    stateStore.saveState(currentState);
                    
                    // Find next node
                    List<Edge> nodeEdges = edges.getOrDefault(nodeId, Collections.emptyList());
                    nodeId = selectNextNode(nodeEdges, currentState, vertexClient);
                    
                    // Audit progress
                    auditLog(tenant, "workflow.node.complete", 
                        String.format("Node: %s, Next: %s", node.getNodeId(), nodeId));
                }
                
                // Save final state
                currentState.setStatus("completed");
                stateStore.saveState(currentState);
                
                // Final audit
                auditLog(tenant, "workflow.complete", 
                    String.format("Workflow: %s, Duration: %dms", 
                        workflowId, 
                        System.currentTimeMillis() - state.getTimestamp()));
                
                return currentState;
                
            } catch (Exception e) {
                // Handle failure
                state.setStatus("failed");
                state.setError(e.getMessage());
                stateStore.saveState(state);
                
                auditLog(tenant, "workflow.failed", 
                    String.format("Workflow: %s, Error: %s", workflowId, e.getMessage()));
                
                throw new RuntimeException("Workflow execution failed", e);
            }
        }, executor);
    }
    
    /**
     * Select next node based on conditions
     */
    private String selectNextNode(List<Edge> edges, State state, VertexAIClient client) {
        if (edges.isEmpty()) {
            return null;
        }
        
        // Evaluate conditions
        List<Edge> validEdges = edges.stream()
            .filter(edge -> edge.getCondition().test(state))
            .collect(Collectors.toList());
        
        if (validEdges.isEmpty()) {
            return null;
        }
        
        if (validEdges.size() == 1) {
            return validEdges.get(0).getTo();
        }
        
        // Multiple valid edges - use AI to help decide
        String prompt = String.format(
            "Given the current state and multiple valid paths, select the best next node:\n" +
            "State: %s\n" +
            "Options: %s\n" +
            "Return only the node name.",
            state.toString(),
            validEdges.stream().map(Edge::getTo).collect(Collectors.joining(", "))
        );
        
        String decision = client.generateContent("gemini-1.5-flash", prompt, 
            Map.of("temperature", 0.7, "maxOutputTokens", 50));
        
        // Validate AI decision
        for (Edge edge : validEdges) {
            if (decision.contains(edge.getTo())) {
                return edge.getTo();
            }
        }
        
        // Fallback to first valid edge
        return validEdges.get(0).getTo();
    }
    
    /**
     * List workflows for a tenant
     */
    public List<WorkflowInfo> listWorkflows(TenantContext tenant) {
        TenantWorkflowRegistry registry = getTenantRegistry(tenant);
        List<WorkflowInfo> workflows = new ArrayList<>();
        
        for (String workflowId : registry.listWorkflows()) {
            workflows.add(getWorkflowInfo(tenant, workflowId));
        }
        
        return workflows;
    }
    
    /**
     * Get workflow info
     */
    private WorkflowInfo getWorkflowInfo(TenantContext tenant, String workflowId) {
        // Load from Firestore
        try {
            return getTenantCollection(tenant, "workflows")
                .document(workflowId)
                .get()
                .get()
                .toObject(WorkflowInfo.class);
        } catch (Exception e) {
            logger.error("Failed to load workflow info", e);
            return new WorkflowInfo(workflowId, "Unknown", "Error loading info");
        }
    }
    
    /**
     * Validate tenant quota
     */
    private void validateTenantQuota(TenantContext tenant) {
        TenantConfiguration config = getTenantConfiguration(tenant);
        
        // Check workflow limit based on tier
        long currentWorkflows = getTenantRegistry(tenant).listWorkflows().size();
        long limit = getWorkflowLimitForTier(config.getTier());
        
        if (currentWorkflows >= limit) {
            throw new QuotaExceededException(String.format(
                "Workflow limit exceeded for tenant %s (limit: %d)",
                tenant.getTenantPath(), limit
            ));
        }
    }
    
    /**
     * Check and update execution quota
     */
    private void checkAndUpdateQuota(TenantContext tenant) {
        // Implementation would track and limit executions per tenant
        // For now, just log
        logger.debug("Checking quota for tenant: {}", tenant.getTenantPath());
    }
    
    /**
     * Validate workflow access
     */
    private void validateWorkflowAccess(TenantContext tenant, String workflowId) {
        // Extract tenant from workflow ID
        String workflowTenantPath = workflowId.split("_")[0].replace("_", "/");
        TenantContext workflowTenant = TenantContext.builder()
            .fromPath(workflowTenantPath)
            .build();
        
        validateTenantAccess(tenant, workflowTenant);
    }
    
    /**
     * Store workflow definition
     */
    private void storeWorkflowDefinition(TenantContext tenant, String workflowId, 
                                       String name, Map<String, WorkflowNode> nodes,
                                       Map<String, List<Edge>> edges) {
        WorkflowInfo info = new WorkflowInfo(workflowId, name, 
            String.format("%d nodes, %d edges", nodes.size(), 
                edges.values().stream().mapToInt(List::size).sum()));
        
        getTenantCollection(tenant, "workflows")
            .document(workflowId)
            .set(info);
    }
    
    /**
     * Get workflow limit by tier
     */
    private long getWorkflowLimitForTier(String tier) {
        switch (tier) {
            case "enterprise":
                return 1000;
            case "standard":
                return 100;
            case "free":
                return 10;
            default:
                return 5;
        }
    }
    
    /**
     * Workflow state with tenant context
     */
    public static class State extends WorkflowEngine.State {
        private TenantContext tenantContext;
        private String status = "running";
        private String error;
        
        public State(String workflowId) {
            super(workflowId);
        }
        
        // Additional getters/setters
        public TenantContext getTenantContext() { return tenantContext; }
        public void setTenantContext(TenantContext context) { this.tenantContext = context; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        
        public long getTimestamp() { return super.timestamp; }
        public Map<String, Object> getData() { return super.data; }
    }
    
    /**
     * Workflow info for storage
     */
    public static class WorkflowInfo {
        private String id;
        private String name;
        private String description;
        private long createdAt;
        
        public WorkflowInfo() {}
        
        public WorkflowInfo(String id, String name, String description) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.createdAt = System.currentTimeMillis();
        }
        
        // Getters/setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    }
    
    /**
     * Tenant-aware state store
     */
    private static class TenantStateStore implements StateStore {
        private final Firestore firestore;
        private final TenantContext tenant;
        
        public TenantStateStore(Firestore firestore, TenantContext tenant) {
            this.firestore = firestore;
            this.tenant = tenant;
        }
        
        @Override
        public void saveState(WorkflowEngine.State state) {
            String path = tenant.getFirestorePath("workflow_states");
            firestore.document(path + "/" + state.getWorkflowId() + "_" + System.currentTimeMillis())
                .set(state);
        }
        
        @Override
        public WorkflowEngine.State loadState(String stateId) {
            try {
                String path = tenant.getFirestorePath("workflow_states");
                return firestore.document(path + "/" + stateId)
                    .get()
                    .get()
                    .toObject(State.class);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load state", e);
            }
        }
        
        @Override
        public List<WorkflowEngine.State> queryByMetadata(Map<String, String> criteria) {
            // Implementation would query by metadata
            return new ArrayList<>();
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
    
    /**
     * Workflow edge with tenant validation
     */
    public static class Edge extends WorkflowEngine.Edge {
        public Edge(String from, String to, Predicate<WorkflowEngine.State> condition) {
            super(from, to, state -> {
                // Cast to tenant-aware state and validate
                if (state instanceof State) {
                    State tenantState = (State) state;
                    if (tenantState.getTenantContext() == null) {
                        throw new IllegalStateException("State missing tenant context");
                    }
                }
                return condition.test(state);
            });
        }
        
        public String getTo() { return super.to; }
        public Predicate<WorkflowEngine.State> getCondition() { return super.condition; }
    }
    
    /**
     * Builder for tenant-aware workflows
     */
    public static class Builder {
        private final TenantContext tenant;
        private final TenantAwareWorkflowEngine engine;
        private final String workflowName;
        private final Map<String, WorkflowNode> nodes = new HashMap<>();
        private final Map<String, List<Edge>> edges = new HashMap<>();
        
        public Builder(TenantContext tenant, TenantAwareWorkflowEngine engine, String workflowName) {
            this.tenant = tenant;
            this.engine = engine;
            this.workflowName = workflowName;
        }
        
        public Builder addNode(WorkflowNode node) {
            nodes.put(node.getNodeId(), node);
            return this;
        }
        
        public Builder addEdge(String from, String to, Predicate<WorkflowEngine.State> condition) {
            edges.computeIfAbsent(from, k -> new ArrayList<>())
                .add(new Edge(from, to, condition));
            return this;
        }
        
        public String build() {
            return engine.registerWorkflow(tenant, workflowName, nodes, edges);
        }
    }
    
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}