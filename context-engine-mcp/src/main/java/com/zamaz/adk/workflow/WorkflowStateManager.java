package com.zamaz.adk.workflow;

import com.google.cloud.firestore.*;
import com.google.cloud.storage.*;
import com.zamaz.adk.core.TenantContext;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Workflow State Manager - Provides persistent state management across executions
 * Supports checkpointing, versioning, and state recovery
 */
public class WorkflowStateManager {
    private final Firestore firestore;
    private final Storage storage;
    private final String bucketName;
    private final Map<String, StateCache> stateCache = new ConcurrentHashMap<>();
    private final Map<String, List<StateCheckpoint>> checkpoints = new ConcurrentHashMap<>();
    
    // State size threshold for Cloud Storage (10KB)
    private static final int STORAGE_THRESHOLD = 10240;
    
    public WorkflowStateManager(Firestore firestore, Storage storage, String bucketName) {
        this.firestore = firestore;
        this.storage = storage;
        this.bucketName = bucketName;
    }
    
    /**
     * Enhanced workflow state with persistence support
     */
    public static class PersistentState extends WorkflowEngine.State {
        private final String stateId;
        private final int version;
        private final Map<String, StateVariable> variables = new ConcurrentHashMap<>();
        private final List<StateTransition> transitions = new ArrayList<>();
        private TenantContext tenantContext;
        private String storageLocation;
        private boolean isDirty = false;
        
        public PersistentState(String workflowId, String executionId, int version) {
            super(workflowId);
            this.stateId = executionId + "_v" + version;
            this.version = version;
        }
        
        public void setVariable(String key, Object value, VariableScope scope) {
            StateVariable var = new StateVariable(key, value, scope);
            variables.put(key, var);
            isDirty = true;
        }
        
        public Object getVariable(String key) {
            StateVariable var = variables.get(key);
            return var != null ? var.getValue() : null;
        }
        
        public void recordTransition(String fromNode, String toNode, String reason) {
            transitions.add(new StateTransition(fromNode, toNode, reason, 
                System.currentTimeMillis()));
            isDirty = true;
        }
        
        public PersistentState fork() {
            PersistentState forked = new PersistentState(
                getWorkflowId(), stateId + "_fork", version + 1);
            
            // Deep copy variables
            variables.forEach((k, v) -> forked.variables.put(k, v.clone()));
            forked.data.putAll(this.data);
            forked.executionPath.addAll(this.executionPath);
            forked.tenantContext = this.tenantContext;
            
            return forked;
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("stateId", stateId);
            map.put("version", version);
            map.put("workflowId", getWorkflowId());
            map.put("variables", variables);
            map.put("transitions", transitions);
            map.put("executionPath", getExecutionPath());
            map.put("data", data);
            map.put("timestamp", timestamp);
            map.put("tenantContext", tenantContext != null ? tenantContext.getTenantPath() : null);
            return map;
        }
        
        // Getters
        public String getStateId() { return stateId; }
        public int getVersion() { return version; }
        public boolean isDirty() { return isDirty; }
        public void markClean() { isDirty = false; }
        public List<StateTransition> getTransitions() { return new ArrayList<>(transitions); }
    }
    
    /**
     * State variable with scope and metadata
     */
    public static class StateVariable implements Cloneable {
        private final String name;
        private Object value;
        private final VariableScope scope;
        private final long createdAt;
        private long updatedAt;
        private final Map<String, String> metadata = new HashMap<>();
        
        public StateVariable(String name, Object value, VariableScope scope) {
            this.name = name;
            this.value = value;
            this.scope = scope;
            this.createdAt = System.currentTimeMillis();
            this.updatedAt = createdAt;
        }
        
        public void setValue(Object value) {
            this.value = value;
            this.updatedAt = System.currentTimeMillis();
        }
        
        @Override
        public StateVariable clone() {
            StateVariable cloned = new StateVariable(name, value, scope);
            cloned.metadata.putAll(this.metadata);
            return cloned;
        }
        
        // Getters
        public String getName() { return name; }
        public Object getValue() { return value; }
        public VariableScope getScope() { return scope; }
        public long getCreatedAt() { return createdAt; }
        public long getUpdatedAt() { return updatedAt; }
    }
    
    /**
     * Variable scope
     */
    public enum VariableScope {
        NODE_LOCAL,      // Only visible within current node
        WORKFLOW_LOCAL,  // Visible within workflow execution
        WORKFLOW_GLOBAL, // Visible across workflow executions
        TENANT_GLOBAL    // Visible across tenant
    }
    
    /**
     * State transition record
     */
    public static class StateTransition {
        private final String fromNode;
        private final String toNode;
        private final String reason;
        private final long timestamp;
        
        public StateTransition(String fromNode, String toNode, String reason, long timestamp) {
            this.fromNode = fromNode;
            this.toNode = toNode;
            this.reason = reason;
            this.timestamp = timestamp;
        }
        
        // Getters
        public String getFromNode() { return fromNode; }
        public String getToNode() { return toNode; }
        public String getReason() { return reason; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * State checkpoint for recovery
     */
    public static class StateCheckpoint {
        private final String checkpointId;
        private final String executionId;
        private final String nodeId;
        private final int stateVersion;
        private final long timestamp;
        private final String storageLocation;
        private final CheckpointType type;
        
        public enum CheckpointType {
            AUTO,      // Automatic checkpoint
            MANUAL,    // Manual checkpoint
            ERROR,     // Checkpoint before error
            BRANCH     // Checkpoint before branching
        }
        
        public StateCheckpoint(String executionId, String nodeId, int stateVersion,
                             String storageLocation, CheckpointType type) {
            this.checkpointId = UUID.randomUUID().toString();
            this.executionId = executionId;
            this.nodeId = nodeId;
            this.stateVersion = stateVersion;
            this.timestamp = System.currentTimeMillis();
            this.storageLocation = storageLocation;
            this.type = type;
        }
        
        // Getters
        public String getCheckpointId() { return checkpointId; }
        public String getExecutionId() { return executionId; }
        public String getNodeId() { return nodeId; }
        public int getStateVersion() { return stateVersion; }
        public long getTimestamp() { return timestamp; }
        public String getStorageLocation() { return storageLocation; }
        public CheckpointType getType() { return type; }
    }
    
    /**
     * State cache for performance
     */
    private static class StateCache {
        private final PersistentState state;
        private final long loadTime;
        private long lastAccess;
        
        public StateCache(PersistentState state) {
            this.state = state;
            this.loadTime = System.currentTimeMillis();
            this.lastAccess = loadTime;
        }
        
        public PersistentState getState() {
            lastAccess = System.currentTimeMillis();
            return state;
        }
        
        public boolean isStale() {
            return System.currentTimeMillis() - lastAccess > TimeUnit.MINUTES.toMillis(5);
        }
    }
    
    /**
     * Save state to persistent storage
     */
    public void saveState(PersistentState state) {
        if (!state.isDirty()) {
            return; // No changes to save
        }
        
        String key = state.getStateId();
        
        // Update cache
        stateCache.put(key, new StateCache(state));
        
        // Serialize state
        Map<String, Object> stateMap = state.toMap();
        String serialized = serializeState(stateMap);
        
        // Determine storage location
        if (serialized.length() > STORAGE_THRESHOLD) {
            // Store in Cloud Storage
            String blobName = String.format("states/%s/%s.json",
                state.tenantContext != null ? state.tenantContext.getTenantPath() : "default",
                key);
            
            BlobId blobId = BlobId.of(bucketName, blobName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
            storage.create(blobInfo, serialized.getBytes(StandardCharsets.UTF_8));
            
            // Store reference in Firestore
            firestore.document("workflow_states/" + key).set(Map.of(
                "storageLocation", blobName,
                "size", serialized.length(),
                "version", state.getVersion(),
                "timestamp", System.currentTimeMillis()
            ));
            
            state.storageLocation = blobName;
        } else {
            // Store directly in Firestore
            firestore.document("workflow_states/" + key).set(stateMap);
        }
        
        state.markClean();
    }
    
    /**
     * Load state from persistent storage
     */
    public PersistentState loadState(String executionId, int version) {
        String key = executionId + "_v" + version;
        
        // Check cache first
        StateCache cached = stateCache.get(key);
        if (cached != null && !cached.isStale()) {
            return cached.getState();
        }
        
        try {
            // Load from Firestore
            DocumentSnapshot doc = firestore.document("workflow_states/" + key)
                .get().get();
            
            if (!doc.exists()) {
                return null;
            }
            
            Map<String, Object> data = doc.getData();
            
            // Check if stored in Cloud Storage
            if (data.containsKey("storageLocation")) {
                String blobName = (String) data.get("storageLocation");
                Blob blob = storage.get(bucketName, blobName);
                
                if (blob != null) {
                    String serialized = new String(blob.getContent(), StandardCharsets.UTF_8);
                    data = deserializeState(serialized);
                }
            }
            
            // Reconstruct state
            PersistentState state = reconstructState(data);
            
            // Update cache
            stateCache.put(key, new StateCache(state));
            
            return state;
            
        } catch (Exception e) {
            System.err.println("Failed to load state: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Create checkpoint
     */
    public StateCheckpoint createCheckpoint(PersistentState state, String nodeId,
                                          StateCheckpoint.CheckpointType type) {
        // Save current state
        saveState(state);
        
        // Create checkpoint record
        StateCheckpoint checkpoint = new StateCheckpoint(
            state.getStateId().split("_v")[0],
            nodeId,
            state.getVersion(),
            state.storageLocation,
            type
        );
        
        // Store checkpoint
        checkpoints.computeIfAbsent(checkpoint.getExecutionId(), k -> new ArrayList<>())
            .add(checkpoint);
        
        // Persist checkpoint info
        firestore.document("workflow_checkpoints/" + checkpoint.getCheckpointId())
            .set(checkpoint);
        
        return checkpoint;
    }
    
    /**
     * Restore from checkpoint
     */
    public PersistentState restoreFromCheckpoint(String checkpointId) {
        try {
            DocumentSnapshot doc = firestore.document("workflow_checkpoints/" + checkpointId)
                .get().get();
            
            if (!doc.exists()) {
                return null;
            }
            
            StateCheckpoint checkpoint = doc.toObject(StateCheckpoint.class);
            
            // Load the state version from checkpoint
            return loadState(checkpoint.getExecutionId(), checkpoint.getStateVersion());
            
        } catch (Exception e) {
            System.err.println("Failed to restore from checkpoint: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * List checkpoints for execution
     */
    public List<StateCheckpoint> listCheckpoints(String executionId) {
        List<StateCheckpoint> cached = checkpoints.get(executionId);
        if (cached != null) {
            return new ArrayList<>(cached);
        }
        
        try {
            return firestore.collection("workflow_checkpoints")
                .whereEqualTo("executionId", executionId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .get()
                .getDocuments()
                .stream()
                .map(doc -> doc.toObject(StateCheckpoint.class))
                .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("Failed to list checkpoints: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Clean old states and checkpoints
     */
    public void cleanOldStates(long retentionMillis) {
        long cutoffTime = System.currentTimeMillis() - retentionMillis;
        
        // Clean from cache
        stateCache.entrySet().removeIf(entry -> 
            entry.getValue().loadTime < cutoffTime);
        
        // Clean from storage
        try {
            // Clean old states
            firestore.collection("workflow_states")
                .whereLessThan("timestamp", cutoffTime)
                .get()
                .get()
                .getDocuments()
                .forEach(doc -> {
                    // Delete from Cloud Storage if needed
                    Map<String, Object> data = doc.getData();
                    if (data.containsKey("storageLocation")) {
                        storage.delete(bucketName, (String) data.get("storageLocation"));
                    }
                    doc.getReference().delete();
                });
            
            // Clean old checkpoints
            firestore.collection("workflow_checkpoints")
                .whereLessThan("timestamp", cutoffTime)
                .get()
                .get()
                .getDocuments()
                .forEach(doc -> doc.getReference().delete());
                
        } catch (Exception e) {
            System.err.println("Failed to clean old states: " + e.getMessage());
        }
    }
    
    /**
     * Get state history for debugging
     */
    public List<StateTransition> getStateHistory(String executionId) {
        List<StateTransition> history = new ArrayList<>();
        
        try {
            // Load all versions of state
            firestore.collection("workflow_states")
                .whereGreaterThanOrEqualTo("stateId", executionId + "_v")
                .whereLessThan("stateId", executionId + "_w")
                .orderBy("stateId")
                .get()
                .get()
                .getDocuments()
                .forEach(doc -> {
                    PersistentState state = reconstructState(doc.getData());
                    if (state != null) {
                        history.addAll(state.getTransitions());
                    }
                });
            
            // Sort by timestamp
            history.sort(Comparator.comparingLong(StateTransition::getTimestamp));
            
        } catch (Exception e) {
            System.err.println("Failed to get state history: " + e.getMessage());
        }
        
        return history;
    }
    
    private String serializeState(Map<String, Object> state) {
        // In production, use proper JSON serialization
        return state.toString();
    }
    
    private Map<String, Object> deserializeState(String serialized) {
        // In production, use proper JSON deserialization
        return new HashMap<>();
    }
    
    private PersistentState reconstructState(Map<String, Object> data) {
        // Reconstruct state from map
        String workflowId = (String) data.get("workflowId");
        String stateId = (String) data.get("stateId");
        int version = ((Number) data.get("version")).intValue();
        
        PersistentState state = new PersistentState(workflowId, 
            stateId.substring(0, stateId.lastIndexOf("_v")), version);
        
        // Restore other fields
        // Implementation details omitted for brevity
        
        return state;
    }
}