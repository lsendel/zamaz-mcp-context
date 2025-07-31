package com.zamaz.adk.workflow;

import com.google.cloud.firestore.*;
import com.google.cloud.pubsub.v1.*;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.*;
import com.zamaz.adk.core.TenantContext;
import com.zamaz.adk.memory.AgentMemoryPool;
import com.zamaz.adk.memory.PersistentMemoryEmbeddings;
import com.zamaz.adk.workflow.WorkflowEngine.State;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.function.Predicate;

/**
 * Cross-Workflow Memory Sharing - Enables memory sharing between workflow instances
 * Supports hierarchical memory organization and access control
 */
public class CrossWorkflowMemory {
    private final Firestore firestore;
    private final Publisher publisher;
    private final Subscriber subscriber;
    private final AgentMemoryPool memoryPool;
    private final PersistentMemoryEmbeddings persistentMemory;
    
    // Memory organization
    private final Map<String, WorkflowMemorySpace> memorySpaces = new ConcurrentHashMap<>();
    private final Map<String, MemoryChannel> memoryChannels = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> workflowSubscriptions = new ConcurrentHashMap<>();
    
    // Access control
    private final Map<String, MemoryAccessPolicy> accessPolicies = new ConcurrentHashMap<>();
    
    // Event handling
    private final ExecutorService eventExecutor = Executors.newWorkStealingPool();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    public CrossWorkflowMemory(Firestore firestore, String projectId, String topicName,
                             String subscriptionName, AgentMemoryPool memoryPool,
                             PersistentMemoryEmbeddings persistentMemory) {
        this.firestore = firestore;
        this.memoryPool = memoryPool;
        this.persistentMemory = persistentMemory;
        
        try {
            // Setup Pub/Sub for memory events
            TopicName topic = TopicName.of(projectId, topicName);
            this.publisher = Publisher.newBuilder(topic).build();
            
            ProjectSubscriptionName subscription = 
                ProjectSubscriptionName.of(projectId, subscriptionName);
            
            MessageReceiver receiver = (PubsubMessage message, AckReplyConsumer consumer) -> {
                handleMemoryEvent(message);
                consumer.ack();
            };
            
            this.subscriber = Subscriber.newBuilder(subscription, receiver).build();
            this.subscriber.startAsync().awaitRunning();
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize cross-workflow memory", e);
        }
        
        // Schedule cleanup tasks
        scheduler.scheduleAtFixedRate(this::cleanupExpiredMemory,
            0, 1, TimeUnit.HOURS);
        scheduler.scheduleAtFixedRate(this::consolidateMemorySpaces,
            0, 6, TimeUnit.HOURS);
    }
    
    /**
     * Workflow memory space - Isolated memory for a workflow family
     */
    public static class WorkflowMemorySpace {
        private final String spaceId;
        private final String workflowFamily;
        private final TenantContext tenantContext;
        private final Map<String, SharedMemorySegment> segments;
        private final Map<String, Long> lastAccessTime;
        private final MemoryHierarchy hierarchy;
        private final long createdAt;
        private volatile long lastModified;
        
        public WorkflowMemorySpace(String spaceId, String workflowFamily,
                                 TenantContext tenantContext) {
            this.spaceId = spaceId;
            this.workflowFamily = workflowFamily;
            this.tenantContext = tenantContext;
            this.segments = new ConcurrentHashMap<>();
            this.lastAccessTime = new ConcurrentHashMap<>();
            this.hierarchy = new MemoryHierarchy();
            this.createdAt = System.currentTimeMillis();
            this.lastModified = createdAt;
        }
        
        public void addSegment(SharedMemorySegment segment) {
            segments.put(segment.getSegmentId(), segment);
            lastModified = System.currentTimeMillis();
        }
        
        public SharedMemorySegment getSegment(String segmentId) {
            lastAccessTime.put(segmentId, System.currentTimeMillis());
            return segments.get(segmentId);
        }
        
        public Collection<SharedMemorySegment> searchSegments(Predicate<SharedMemorySegment> filter) {
            return segments.values().stream()
                .filter(filter)
                .collect(Collectors.toList());
        }
        
        // Getters
        public String getSpaceId() { return spaceId; }
        public String getWorkflowFamily() { return workflowFamily; }
        public TenantContext getTenantContext() { return tenantContext; }
        public Map<String, SharedMemorySegment> getSegments() { return segments; }
        public MemoryHierarchy getHierarchy() { return hierarchy; }
        public long getCreatedAt() { return createdAt; }
        public long getLastModified() { return lastModified; }
    }
    
    /**
     * Shared memory segment
     */
    public static class SharedMemorySegment {
        private final String segmentId;
        private final String sourceWorkflowId;
        private final MemoryScope scope;
        private final Map<String, Object> data;
        private final Map<String, String> metadata;
        private final Set<String> tags;
        private final long timestamp;
        private final long ttl;
        private int readCount;
        private int writeCount;
        
        public enum MemoryScope {
            INSTANCE,          // Single workflow instance
            FAMILY,            // All instances of workflow family
            TENANT,            // All workflows in tenant
            GLOBAL,            // All workflows (with permission)
            HIERARCHICAL       // Follow hierarchy rules
        }
        
        public SharedMemorySegment(String segmentId, String sourceWorkflowId,
                                 MemoryScope scope, Map<String, Object> data,
                                 Map<String, String> metadata, Set<String> tags,
                                 long ttl) {
            this.segmentId = segmentId;
            this.sourceWorkflowId = sourceWorkflowId;
            this.scope = scope;
            this.data = new ConcurrentHashMap<>(data);
            this.metadata = new ConcurrentHashMap<>(metadata);
            this.tags = new HashSet<>(tags);
            this.timestamp = System.currentTimeMillis();
            this.ttl = ttl;
            this.readCount = 0;
            this.writeCount = 0;
        }
        
        public Object read(String key) {
            readCount++;
            return data.get(key);
        }
        
        public void write(String key, Object value) {
            writeCount++;
            data.put(key, value);
        }
        
        public boolean isExpired() {
            return ttl > 0 && (System.currentTimeMillis() - timestamp) > ttl;
        }
        
        // Getters
        public String getSegmentId() { return segmentId; }
        public String getSourceWorkflowId() { return sourceWorkflowId; }
        public MemoryScope getScope() { return scope; }
        public Map<String, Object> getData() { return new HashMap<>(data); }
        public Map<String, String> getMetadata() { return metadata; }
        public Set<String> getTags() { return tags; }
        public long getTimestamp() { return timestamp; }
        public long getTtl() { return ttl; }
        public int getReadCount() { return readCount; }
        public int getWriteCount() { return writeCount; }
    }
    
    /**
     * Memory hierarchy for organizing shared memory
     */
    public static class MemoryHierarchy {
        private final Map<String, List<String>> parentToChildren = new HashMap<>();
        private final Map<String, String> childToParent = new HashMap<>();
        private final Map<String, Integer> levelMap = new HashMap<>();
        
        public void addRelationship(String parentId, String childId, int level) {
            parentToChildren.computeIfAbsent(parentId, k -> new ArrayList<>()).add(childId);
            childToParent.put(childId, parentId);
            levelMap.put(childId, level);
        }
        
        public List<String> getChildren(String parentId) {
            return parentToChildren.getOrDefault(parentId, Collections.emptyList());
        }
        
        public String getParent(String childId) {
            return childToParent.get(childId);
        }
        
        public int getLevel(String nodeId) {
            return levelMap.getOrDefault(nodeId, 0);
        }
        
        public List<String> getAncestors(String nodeId) {
            List<String> ancestors = new ArrayList<>();
            String current = nodeId;
            while ((current = childToParent.get(current)) != null) {
                ancestors.add(current);
            }
            return ancestors;
        }
        
        public List<String> getDescendants(String nodeId) {
            List<String> descendants = new ArrayList<>();
            Queue<String> queue = new LinkedList<>();
            queue.offer(nodeId);
            
            while (!queue.isEmpty()) {
                String current = queue.poll();
                List<String> children = parentToChildren.get(current);
                if (children != null) {
                    descendants.addAll(children);
                    queue.addAll(children);
                }
            }
            
            return descendants;
        }
    }
    
    /**
     * Memory channel for pub/sub communication
     */
    public static class MemoryChannel {
        private final String channelId;
        private final String channelName;
        private final Set<String> subscribers;
        private final ChannelType type;
        private final Map<String, Object> configuration;
        private final Queue<MemoryMessage> messageBuffer;
        private volatile boolean active;
        
        public enum ChannelType {
            BROADCAST,      // One-to-many
            QUEUE,          // Load balanced
            TOPIC,          // Pub/sub topic
            DIRECT,         // Point-to-point
            HIERARCHICAL    // Follow hierarchy
        }
        
        public MemoryChannel(String channelId, String channelName, ChannelType type,
                           Map<String, Object> configuration) {
            this.channelId = channelId;
            this.channelName = channelName;
            this.subscribers = new ConcurrentSkipListSet<>();
            this.type = type;
            this.configuration = configuration;
            this.messageBuffer = new ConcurrentLinkedQueue<>();
            this.active = true;
        }
        
        public void subscribe(String workflowId) {
            subscribers.add(workflowId);
        }
        
        public void unsubscribe(String workflowId) {
            subscribers.remove(workflowId);
        }
        
        public void publish(MemoryMessage message) {
            if (active) {
                messageBuffer.offer(message);
            }
        }
        
        public MemoryMessage receive() {
            return messageBuffer.poll();
        }
        
        // Getters
        public String getChannelId() { return channelId; }
        public String getChannelName() { return channelName; }
        public Set<String> getSubscribers() { return new HashSet<>(subscribers); }
        public ChannelType getType() { return type; }
        public Map<String, Object> getConfiguration() { return configuration; }
        public boolean isActive() { return active; }
        
        public void deactivate() { active = false; }
    }
    
    /**
     * Memory message
     */
    public static class MemoryMessage {
        private final String messageId;
        private final String sourceWorkflowId;
        private final String channelId;
        private final MessageType type;
        private final Map<String, Object> payload;
        private final Map<String, String> headers;
        private final long timestamp;
        private final int priority;
        
        public enum MessageType {
            DATA_UPDATE,      // Memory data updated
            STATE_CHANGE,     // Workflow state changed
            EVENT,            // General event
            REQUEST,          // Request for data
            RESPONSE,         // Response to request
            SYNC              // Synchronization message
        }
        
        public MemoryMessage(String sourceWorkflowId, String channelId,
                           MessageType type, Map<String, Object> payload,
                           Map<String, String> headers, int priority) {
            this.messageId = UUID.randomUUID().toString();
            this.sourceWorkflowId = sourceWorkflowId;
            this.channelId = channelId;
            this.type = type;
            this.payload = payload;
            this.headers = headers;
            this.timestamp = System.currentTimeMillis();
            this.priority = priority;
        }
        
        // Getters
        public String getMessageId() { return messageId; }
        public String getSourceWorkflowId() { return sourceWorkflowId; }
        public String getChannelId() { return channelId; }
        public MessageType getType() { return type; }
        public Map<String, Object> getPayload() { return payload; }
        public Map<String, String> getHeaders() { return headers; }
        public long getTimestamp() { return timestamp; }
        public int getPriority() { return priority; }
    }
    
    /**
     * Memory access policy
     */
    public static class MemoryAccessPolicy {
        private final String policyId;
        private final PolicyType type;
        private final Map<String, AccessRule> rules;
        private final Set<String> allowedWorkflows;
        private final Set<String> deniedWorkflows;
        private final Map<String, Object> constraints;
        
        public enum PolicyType {
            WHITELIST,      // Only allowed workflows
            BLACKLIST,      // All except denied
            ROLE_BASED,     // Based on workflow roles
            ATTRIBUTE_BASED,// Based on attributes
            DYNAMIC         // Evaluated at runtime
        }
        
        public enum AccessLevel {
            NONE, READ, WRITE, ADMIN
        }
        
        public static class AccessRule {
            private final String ruleId;
            private final String pattern;
            private final AccessLevel level;
            private final Map<String, Object> conditions;
            
            public AccessRule(String ruleId, String pattern, AccessLevel level,
                            Map<String, Object> conditions) {
                this.ruleId = ruleId;
                this.pattern = pattern;
                this.level = level;
                this.conditions = conditions;
            }
            
            public boolean matches(String resource) {
                return resource.matches(pattern);
            }
            
            public boolean checkConditions(Map<String, Object> context) {
                // Simple condition checking - would be more sophisticated in production
                for (Map.Entry<String, Object> condition : conditions.entrySet()) {
                    if (!condition.getValue().equals(context.get(condition.getKey()))) {
                        return false;
                    }
                }
                return true;
            }
            
            // Getters
            public String getRuleId() { return ruleId; }
            public String getPattern() { return pattern; }
            public AccessLevel getLevel() { return level; }
            public Map<String, Object> getConditions() { return conditions; }
        }
        
        public MemoryAccessPolicy(String policyId, PolicyType type) {
            this.policyId = policyId;
            this.type = type;
            this.rules = new HashMap<>();
            this.allowedWorkflows = new HashSet<>();
            this.deniedWorkflows = new HashSet<>();
            this.constraints = new HashMap<>();
        }
        
        public void addRule(AccessRule rule) {
            rules.put(rule.getRuleId(), rule);
        }
        
        public void allowWorkflow(String workflowId) {
            allowedWorkflows.add(workflowId);
            deniedWorkflows.remove(workflowId);
        }
        
        public void denyWorkflow(String workflowId) {
            deniedWorkflows.add(workflowId);
            allowedWorkflows.remove(workflowId);
        }
        
        public AccessLevel checkAccess(String workflowId, String resource,
                                     Map<String, Object> context) {
            // Check deny list first
            if (deniedWorkflows.contains(workflowId)) {
                return AccessLevel.NONE;
            }
            
            // Check policy type
            switch (type) {
                case WHITELIST:
                    if (!allowedWorkflows.contains(workflowId)) {
                        return AccessLevel.NONE;
                    }
                    break;
                    
                case BLACKLIST:
                    // Already checked deny list
                    break;
                    
                case ROLE_BASED:
                    String role = (String) context.get("workflow_role");
                    if (role == null || !checkRoleAccess(role, resource)) {
                        return AccessLevel.NONE;
                    }
                    break;
                    
                case ATTRIBUTE_BASED:
                    if (!checkAttributeAccess(context, resource)) {
                        return AccessLevel.NONE;
                    }
                    break;
                    
                case DYNAMIC:
                    // Would evaluate dynamic rules
                    break;
            }
            
            // Check specific rules
            AccessLevel maxLevel = AccessLevel.NONE;
            for (AccessRule rule : rules.values()) {
                if (rule.matches(resource) && rule.checkConditions(context)) {
                    if (rule.getLevel().ordinal() > maxLevel.ordinal()) {
                        maxLevel = rule.getLevel();
                    }
                }
            }
            
            return maxLevel;
        }
        
        private boolean checkRoleAccess(String role, String resource) {
            // Simple role checking - would be more sophisticated in production
            return role.equals("admin") || 
                   (role.equals("writer") && !resource.contains("system")) ||
                   (role.equals("reader"));
        }
        
        private boolean checkAttributeAccess(Map<String, Object> context, String resource) {
            // Simple attribute checking
            return context.containsKey("authorized") && 
                   Boolean.TRUE.equals(context.get("authorized"));
        }
        
        // Getters
        public String getPolicyId() { return policyId; }
        public PolicyType getType() { return type; }
        public Map<String, AccessRule> getRules() { return rules; }
        public Set<String> getAllowedWorkflows() { return allowedWorkflows; }
        public Set<String> getDeniedWorkflows() { return deniedWorkflows; }
        public Map<String, Object> getConstraints() { return constraints; }
    }
    
    /**
     * Memory sharing request
     */
    public static class MemorySharingRequest {
        private final String sourceWorkflowId;
        private final String targetPattern;
        private final SharedMemorySegment.MemoryScope scope;
        private final Map<String, Object> data;
        private final Map<String, String> metadata;
        private final Set<String> tags;
        private final long ttl;
        private final boolean persistent;
        
        public MemorySharingRequest(String sourceWorkflowId, String targetPattern,
                                  SharedMemorySegment.MemoryScope scope,
                                  Map<String, Object> data, Map<String, String> metadata,
                                  Set<String> tags, long ttl, boolean persistent) {
            this.sourceWorkflowId = sourceWorkflowId;
            this.targetPattern = targetPattern;
            this.scope = scope;
            this.data = data;
            this.metadata = metadata;
            this.tags = tags;
            this.ttl = ttl;
            this.persistent = persistent;
        }
        
        // Getters
        public String getSourceWorkflowId() { return sourceWorkflowId; }
        public String getTargetPattern() { return targetPattern; }
        public SharedMemorySegment.MemoryScope getScope() { return scope; }
        public Map<String, Object> getData() { return data; }
        public Map<String, String> getMetadata() { return metadata; }
        public Set<String> getTags() { return tags; }
        public long getTtl() { return ttl; }
        public boolean isPersistent() { return persistent; }
    }
    
    /**
     * Create or get memory space
     */
    public WorkflowMemorySpace createMemorySpace(String workflowFamily,
                                                TenantContext tenantContext) {
        String spaceId = tenantContext.getTenantPath() + "/" + workflowFamily;
        
        return memorySpaces.computeIfAbsent(spaceId, k -> {
            WorkflowMemorySpace space = new WorkflowMemorySpace(
                spaceId, workflowFamily, tenantContext);
            
            // Initialize with default policy
            MemoryAccessPolicy defaultPolicy = new MemoryAccessPolicy(
                spaceId + "_policy", MemoryAccessPolicy.PolicyType.WHITELIST);
            accessPolicies.put(spaceId, defaultPolicy);
            
            // Persist space
            persistMemorySpace(space);
            
            return space;
        });
    }
    
    /**
     * Share memory between workflows
     */
    public CompletableFuture<String> shareMemory(MemorySharingRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Create memory segment
                String segmentId = UUID.randomUUID().toString();
                SharedMemorySegment segment = new SharedMemorySegment(
                    segmentId,
                    request.getSourceWorkflowId(),
                    request.getScope(),
                    request.getData(),
                    request.getMetadata(),
                    request.getTags(),
                    request.getTtl()
                );
                
                // Determine target memory spaces
                List<WorkflowMemorySpace> targetSpaces = findTargetSpaces(
                    request.getTargetPattern(), request.getScope());
                
                // Add to each target space
                for (WorkflowMemorySpace space : targetSpaces) {
                    // Check access policy
                    MemoryAccessPolicy policy = accessPolicies.get(space.getSpaceId());
                    if (policy != null) {
                        MemoryAccessPolicy.AccessLevel access = policy.checkAccess(
                            request.getSourceWorkflowId(),
                            segmentId,
                            Map.of("operation", "share")
                        );
                        
                        if (access.ordinal() >= MemoryAccessPolicy.AccessLevel.WRITE.ordinal()) {
                            space.addSegment(segment);
                        }
                    }
                }
                
                // Store in persistent memory if requested
                if (request.isPersistent()) {
                    storePersistentMemory(segment);
                }
                
                // Publish memory event
                publishMemoryEvent(new MemoryMessage(
                    request.getSourceWorkflowId(),
                    "memory_shared",
                    MemoryMessage.MessageType.DATA_UPDATE,
                    Map.of("segment_id", segmentId, "scope", request.getScope()),
                    Map.of("timestamp", String.valueOf(System.currentTimeMillis())),
                    5
                ));
                
                return segmentId;
                
            } catch (Exception e) {
                throw new RuntimeException("Failed to share memory", e);
            }
        }, eventExecutor);
    }
    
    /**
     * Access shared memory
     */
    public CompletableFuture<Map<String, Object>> accessMemory(String workflowId,
                                                              String spaceId,
                                                              String segmentId) {
        return CompletableFuture.supplyAsync(() -> {
            WorkflowMemorySpace space = memorySpaces.get(spaceId);
            if (space == null) {
                return Collections.emptyMap();
            }
            
            // Check access policy
            MemoryAccessPolicy policy = accessPolicies.get(spaceId);
            if (policy != null) {
                MemoryAccessPolicy.AccessLevel access = policy.checkAccess(
                    workflowId, segmentId, Map.of("operation", "read"));
                
                if (access.ordinal() < MemoryAccessPolicy.AccessLevel.READ.ordinal()) {
                    throw new SecurityException("Access denied to memory segment");
                }
            }
            
            SharedMemorySegment segment = space.getSegment(segmentId);
            if (segment == null || segment.isExpired()) {
                return Collections.emptyMap();
            }
            
            return segment.getData();
        });
    }
    
    /**
     * Search shared memory
     */
    public CompletableFuture<List<SharedMemorySegment>> searchMemory(
            String workflowId, String spacePattern, Set<String> tags,
            Map<String, String> metadataFilters) {
        return CompletableFuture.supplyAsync(() -> {
            List<SharedMemorySegment> results = new ArrayList<>();
            
            // Find matching spaces
            List<WorkflowMemorySpace> spaces = memorySpaces.values().stream()
                .filter(space -> space.getSpaceId().matches(spacePattern))
                .collect(Collectors.toList());
            
            // Search each space
            for (WorkflowMemorySpace space : spaces) {
                // Check access
                MemoryAccessPolicy policy = accessPolicies.get(space.getSpaceId());
                if (policy != null) {
                    MemoryAccessPolicy.AccessLevel access = policy.checkAccess(
                        workflowId, "*", Map.of("operation", "search"));
                    
                    if (access.ordinal() < MemoryAccessPolicy.AccessLevel.READ.ordinal()) {
                        continue;
                    }
                }
                
                // Search segments
                Collection<SharedMemorySegment> segments = space.searchSegments(
                    segment -> {
                        // Check tags
                        if (!tags.isEmpty() && !segment.getTags().containsAll(tags)) {
                            return false;
                        }
                        
                        // Check metadata
                        for (Map.Entry<String, String> filter : metadataFilters.entrySet()) {
                            if (!filter.getValue().equals(
                                segment.getMetadata().get(filter.getKey()))) {
                                return false;
                            }
                        }
                        
                        return !segment.isExpired();
                    }
                );
                
                results.addAll(segments);
            }
            
            return results;
        });
    }
    
    /**
     * Create memory channel
     */
    public String createChannel(String channelName, MemoryChannel.ChannelType type,
                              Map<String, Object> configuration) {
        String channelId = UUID.randomUUID().toString();
        MemoryChannel channel = new MemoryChannel(channelId, channelName, type, configuration);
        
        memoryChannels.put(channelId, channel);
        
        // Publish channel creation event
        publishMemoryEvent(new MemoryMessage(
            "system",
            channelId,
            MemoryMessage.MessageType.EVENT,
            Map.of("event", "channel_created", "channel_name", channelName),
            Map.of("channel_type", type.name()),
            1
        ));
        
        return channelId;
    }
    
    /**
     * Subscribe to memory channel
     */
    public void subscribeToChannel(String workflowId, String channelId) {
        MemoryChannel channel = memoryChannels.get(channelId);
        if (channel != null) {
            channel.subscribe(workflowId);
            workflowSubscriptions.computeIfAbsent(workflowId, k -> new HashSet<>())
                .add(channelId);
        }
    }
    
    /**
     * Publish to memory channel
     */
    public void publishToChannel(String channelId, MemoryMessage message) {
        MemoryChannel channel = memoryChannels.get(channelId);
        if (channel != null && channel.isActive()) {
            channel.publish(message);
            
            // Process based on channel type
            switch (channel.getType()) {
                case BROADCAST:
                    broadcastToSubscribers(channel, message);
                    break;
                case QUEUE:
                    queueToNextSubscriber(channel, message);
                    break;
                case DIRECT:
                    sendDirectMessage(channel, message);
                    break;
                default:
                    // Store in channel buffer
            }
        }
    }
    
    /**
     * Update memory access policy
     */
    public void updateAccessPolicy(String spaceId, MemoryAccessPolicy policy) {
        accessPolicies.put(spaceId, policy);
        
        // Persist policy
        persistAccessPolicy(spaceId, policy);
    }
    
    /**
     * Get memory statistics
     */
    public MemoryStatistics getStatistics() {
        int totalSegments = memorySpaces.values().stream()
            .mapToInt(space -> space.getSegments().size())
            .sum();
        
        Map<SharedMemorySegment.MemoryScope, Integer> segmentsByScope = new HashMap<>();
        memorySpaces.values().stream()
            .flatMap(space -> space.getSegments().values().stream())
            .forEach(segment -> segmentsByScope.merge(segment.getScope(), 1, Integer::sum));
        
        int activeChannels = (int) memoryChannels.values().stream()
            .filter(MemoryChannel::isActive)
            .count();
        
        return new MemoryStatistics(
            memorySpaces.size(),
            totalSegments,
            segmentsByScope,
            activeChannels,
            workflowSubscriptions.size(),
            calculateMemoryUsage()
        );
    }
    
    /**
     * Memory statistics
     */
    public static class MemoryStatistics {
        private final int spaceCount;
        private final int segmentCount;
        private final Map<SharedMemorySegment.MemoryScope, Integer> segmentsByScope;
        private final int activeChannels;
        private final int subscriberCount;
        private final long memoryUsageBytes;
        
        public MemoryStatistics(int spaceCount, int segmentCount,
                              Map<SharedMemorySegment.MemoryScope, Integer> segmentsByScope,
                              int activeChannels, int subscriberCount, long memoryUsageBytes) {
            this.spaceCount = spaceCount;
            this.segmentCount = segmentCount;
            this.segmentsByScope = segmentsByScope;
            this.activeChannels = activeChannels;
            this.subscriberCount = subscriberCount;
            this.memoryUsageBytes = memoryUsageBytes;
        }
        
        // Getters
        public int getSpaceCount() { return spaceCount; }
        public int getSegmentCount() { return segmentCount; }
        public Map<SharedMemorySegment.MemoryScope, Integer> getSegmentsByScope() { 
            return segmentsByScope; 
        }
        public int getActiveChannels() { return activeChannels; }
        public int getSubscriberCount() { return subscriberCount; }
        public long getMemoryUsageBytes() { return memoryUsageBytes; }
    }
    
    /**
     * Helper methods
     */
    
    private List<WorkflowMemorySpace> findTargetSpaces(String targetPattern,
                                                      SharedMemorySegment.MemoryScope scope) {
        return memorySpaces.values().stream()
            .filter(space -> {
                switch (scope) {
                    case FAMILY:
                        return space.getWorkflowFamily().matches(targetPattern);
                    case TENANT:
                        return space.getTenantContext() != null &&
                               targetPattern.equals(space.getTenantContext().getTenantId());
                    case GLOBAL:
                        return true;
                    default:
                        return space.getSpaceId().matches(targetPattern);
                }
            })
            .collect(Collectors.toList());
    }
    
    private void storePersistentMemory(SharedMemorySegment segment) {
        // Convert to persistent memory format
        PersistentMemoryEmbeddings.MemoryStorageRequest storageRequest =
            new PersistentMemoryEmbeddings.MemoryStorageRequest(
                segment.getData().toString(),
                PersistentMemoryEmbeddings.MemoryChunk.MemoryType.CONTEXTUAL,
                segment.getSourceWorkflowId(),
                null, // Use default tenant
                Map.of("segment_id", segment.getSegmentId()),
                null,
                false,
                0.5
            );
        
        persistentMemory.storeMemory(storageRequest);
    }
    
    private void publishMemoryEvent(MemoryMessage message) {
        try {
            // Convert to Pub/Sub message
            Map<String, String> attributes = new HashMap<>();
            attributes.put("message_id", message.getMessageId());
            attributes.put("source_workflow", message.getSourceWorkflowId());
            attributes.put("message_type", message.getType().name());
            attributes.put("timestamp", String.valueOf(message.getTimestamp()));
            attributes.putAll(message.getHeaders());
            
            PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
                .setData(ByteString.copyFromUtf8(message.getPayload().toString()))
                .putAllAttributes(attributes)
                .build();
            
            publisher.publish(pubsubMessage);
            
        } catch (Exception e) {
            System.err.println("Failed to publish memory event: " + e.getMessage());
        }
    }
    
    private void handleMemoryEvent(PubsubMessage message) {
        try {
            Map<String, String> attributes = message.getAttributesMap();
            String messageType = attributes.get("message_type");
            
            if (messageType != null) {
                MemoryMessage.MessageType type = MemoryMessage.MessageType.valueOf(messageType);
                
                // Process based on type
                switch (type) {
                    case SYNC:
                        handleSyncMessage(message);
                        break;
                    case REQUEST:
                        handleMemoryRequest(message);
                        break;
                    default:
                        // Process other message types
                }
            }
        } catch (Exception e) {
            System.err.println("Error handling memory event: " + e.getMessage());
        }
    }
    
    private void broadcastToSubscribers(MemoryChannel channel, MemoryMessage message) {
        for (String subscriberId : channel.getSubscribers()) {
            // Send to each subscriber
            Set<String> channels = workflowSubscriptions.get(subscriberId);
            if (channels != null && channels.contains(channel.getChannelId())) {
                // In production, would send via appropriate mechanism
            }
        }
    }
    
    private void queueToNextSubscriber(MemoryChannel channel, MemoryMessage message) {
        // Round-robin or other queue strategy
        List<String> subscribers = new ArrayList<>(channel.getSubscribers());
        if (!subscribers.isEmpty()) {
            // Select next subscriber
            int index = (int) (System.currentTimeMillis() % subscribers.size());
            String selectedSubscriber = subscribers.get(index);
            // Send to selected subscriber
        }
    }
    
    private void sendDirectMessage(MemoryChannel channel, MemoryMessage message) {
        // Extract target from message headers
        String targetWorkflow = message.getHeaders().get("target_workflow");
        if (targetWorkflow != null && channel.getSubscribers().contains(targetWorkflow)) {
            // Send directly to target
        }
    }
    
    private void handleSyncMessage(PubsubMessage message) {
        // Handle memory synchronization
    }
    
    private void handleMemoryRequest(PubsubMessage message) {
        // Handle memory access requests
    }
    
    private void cleanupExpiredMemory() {
        int cleanedCount = 0;
        
        for (WorkflowMemorySpace space : memorySpaces.values()) {
            List<String> toRemove = new ArrayList<>();
            
            for (SharedMemorySegment segment : space.getSegments().values()) {
                if (segment.isExpired()) {
                    toRemove.add(segment.getSegmentId());
                }
            }
            
            toRemove.forEach(id -> space.getSegments().remove(id));
            cleanedCount += toRemove.size();
        }
        
        if (cleanedCount > 0) {
            System.out.println("Cleaned up " + cleanedCount + " expired memory segments");
        }
    }
    
    private void consolidateMemorySpaces() {
        // Identify unused memory spaces
        List<String> toRemove = new ArrayList<>();
        long cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7);
        
        for (WorkflowMemorySpace space : memorySpaces.values()) {
            if (space.getSegments().isEmpty() && space.getLastModified() < cutoffTime) {
                toRemove.add(space.getSpaceId());
            }
        }
        
        // Remove unused spaces
        toRemove.forEach(memorySpaces::remove);
    }
    
    private void persistMemorySpace(WorkflowMemorySpace space) {
        Map<String, Object> data = new HashMap<>();
        data.put("space_id", space.getSpaceId());
        data.put("workflow_family", space.getWorkflowFamily());
        data.put("tenant_id", space.getTenantContext() != null ? 
            space.getTenantContext().getTenantId() : null);
        data.put("created_at", space.getCreatedAt());
        data.put("last_modified", space.getLastModified());
        data.put("segment_count", space.getSegments().size());
        
        firestore.collection("memory_spaces")
            .document(space.getSpaceId())
            .set(data);
    }
    
    private void persistAccessPolicy(String spaceId, MemoryAccessPolicy policy) {
        Map<String, Object> data = new HashMap<>();
        data.put("policy_id", policy.getPolicyId());
        data.put("space_id", spaceId);
        data.put("type", policy.getType().name());
        data.put("allowed_workflows", new ArrayList<>(policy.getAllowedWorkflows()));
        data.put("denied_workflows", new ArrayList<>(policy.getDeniedWorkflows()));
        data.put("constraints", policy.getConstraints());
        
        firestore.collection("access_policies")
            .document(policy.getPolicyId())
            .set(data);
    }
    
    private long calculateMemoryUsage() {
        // Estimate memory usage
        return memorySpaces.values().stream()
            .mapToLong(space -> space.getSegments().size() * 1024L) // 1KB per segment estimate
            .sum();
    }
    
    public void shutdown() {
        eventExecutor.shutdown();
        scheduler.shutdown();
        
        try {
            if (!eventExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                eventExecutor.shutdownNow();
            }
            
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            
            // Persist all memory spaces
            memorySpaces.values().forEach(this::persistMemorySpace);
            
            publisher.shutdown();
            publisher.awaitTermination(30, TimeUnit.SECONDS);
            
            subscriber.stopAsync().awaitTerminated();
            
        } catch (Exception e) {
            eventExecutor.shutdownNow();
            scheduler.shutdownNow();
        }
    }
}