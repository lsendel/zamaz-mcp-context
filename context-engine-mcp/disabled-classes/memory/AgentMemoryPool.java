package com.zamaz.adk.memory;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.pubsub.v1.*;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.*;
import com.zamaz.adk.agents.MultiAgentOrchestrator.AgentType;
import com.zamaz.adk.core.TenantContext;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Agent Memory Pool - Shared memory system with access controls
 * Enables controlled information sharing between agents while maintaining isolation
 */
public class AgentMemoryPool {
    private final Firestore firestore;
    private final Publisher eventPublisher;
    private final Map<String, MemorySegment> memorySegments = new ConcurrentHashMap<>();
    private final Map<String, AccessControlList> accessControls = new ConcurrentHashMap<>();
    private final Map<String, MemoryIndex> indices = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    // Memory configuration
    private static final int MAX_SEGMENT_SIZE = 1024 * 1024; // 1MB per segment
    private static final long SEGMENT_TTL = TimeUnit.HOURS.toMillis(24);
    private static final int MAX_SEGMENTS_PER_POOL = 1000;
    
    public AgentMemoryPool(Firestore firestore, String projectId, String topicName) {
        this.firestore = firestore;
        
        try {
            this.eventPublisher = Publisher.newBuilder(
                TopicName.of(projectId, topicName)).build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create publisher", e);
        }
        
        // Schedule cleanup
        scheduler.scheduleAtFixedRate(this::cleanupExpiredSegments, 
            0, 1, TimeUnit.HOURS);
        
        // Schedule persistence
        scheduler.scheduleAtFixedRate(this::persistMemoryState,
            0, 5, TimeUnit.MINUTES);
    }
    
    /**
     * Memory segment with versioning and metadata
     */
    public static class MemorySegment {
        private final String segmentId;
        private final String poolId;
        private final MemoryType type;
        private final Map<String, MemoryEntry> entries = new ConcurrentHashMap<>();
        private final long createdAt;
        private long lastAccessTime;
        private final Map<String, Object> metadata;
        private int version = 1;
        
        public enum MemoryType {
            SHARED_CONTEXT,      // Shared between all agents
            WORKFLOW_STATE,      // Workflow-specific state
            AGENT_KNOWLEDGE,     // Agent-specific knowledge
            CROSS_WORKFLOW,      // Shared across workflows
            TENANT_GLOBAL        // Global for tenant
        }
        
        public MemorySegment(String segmentId, String poolId, MemoryType type,
                           Map<String, Object> metadata) {
            this.segmentId = segmentId;
            this.poolId = poolId;
            this.type = type;
            this.metadata = metadata;
            this.createdAt = System.currentTimeMillis();
            this.lastAccessTime = createdAt;
        }
        
        public void addEntry(String key, Object value, String agentId,
                           Map<String, String> tags) {
            MemoryEntry entry = new MemoryEntry(key, value, agentId, tags);
            entries.put(key, entry);
            lastAccessTime = System.currentTimeMillis();
            version++;
        }
        
        public MemoryEntry getEntry(String key) {
            lastAccessTime = System.currentTimeMillis();
            return entries.get(key);
        }
        
        public Collection<MemoryEntry> searchEntries(String query, 
                                                    Map<String, String> tagFilters) {
            return entries.values().stream()
                .filter(entry -> matchesQuery(entry, query))
                .filter(entry -> matchesTags(entry, tagFilters))
                .collect(Collectors.toList());
        }
        
        private boolean matchesQuery(MemoryEntry entry, String query) {
            if (query == null || query.isEmpty()) return true;
            
            String searchText = entry.getKey() + " " + entry.getValue().toString();
            return searchText.toLowerCase().contains(query.toLowerCase());
        }
        
        private boolean matchesTags(MemoryEntry entry, Map<String, String> filters) {
            if (filters == null || filters.isEmpty()) return true;
            
            for (Map.Entry<String, String> filter : filters.entrySet()) {
                String tagValue = entry.getTags().get(filter.getKey());
                if (!filter.getValue().equals(tagValue)) {
                    return false;
                }
            }
            return true;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - createdAt > SEGMENT_TTL;
        }
        
        public int getSize() {
            // Estimate size based on entries
            return entries.values().stream()
                .mapToInt(e -> e.getEstimatedSize())
                .sum();
        }
        
        // Getters
        public String getSegmentId() { return segmentId; }
        public String getPoolId() { return poolId; }
        public MemoryType getType() { return type; }
        public Map<String, MemoryEntry> getEntries() { return new HashMap<>(entries); }
        public long getCreatedAt() { return createdAt; }
        public long getLastAccessTime() { return lastAccessTime; }
        public Map<String, Object> getMetadata() { return metadata; }
        public int getVersion() { return version; }
    }
    
    /**
     * Memory entry with provenance
     */
    public static class MemoryEntry {
        private final String key;
        private final Object value;
        private final String sourceAgentId;
        private final long timestamp;
        private final Map<String, String> tags;
        private int accessCount = 0;
        private final List<String> accessHistory = new ArrayList<>();
        
        public MemoryEntry(String key, Object value, String sourceAgentId,
                         Map<String, String> tags) {
            this.key = key;
            this.value = value;
            this.sourceAgentId = sourceAgentId;
            this.timestamp = System.currentTimeMillis();
            this.tags = tags != null ? new HashMap<>(tags) : new HashMap<>();
        }
        
        public void recordAccess(String agentId) {
            accessCount++;
            accessHistory.add(agentId + "@" + System.currentTimeMillis());
            
            // Keep only last 100 accesses
            if (accessHistory.size() > 100) {
                accessHistory.remove(0);
            }
        }
        
        public int getEstimatedSize() {
            // Simple size estimation
            return key.length() + value.toString().length() + 
                   tags.toString().length() + 100; // Overhead
        }
        
        // Getters
        public String getKey() { return key; }
        public Object getValue() { return value; }
        public String getSourceAgentId() { return sourceAgentId; }
        public long getTimestamp() { return timestamp; }
        public Map<String, String> getTags() { return new HashMap<>(tags); }
        public int getAccessCount() { return accessCount; }
        public List<String> getAccessHistory() { return new ArrayList<>(accessHistory); }
    }
    
    /**
     * Access control list for memory segments
     */
    public static class AccessControlList {
        private final Map<String, AccessLevel> agentPermissions = new ConcurrentHashMap<>();
        private final Map<AgentType, AccessLevel> typePermissions = new ConcurrentHashMap<>();
        private AccessLevel defaultAccess = AccessLevel.NO_ACCESS;
        private final Set<String> blockedAgents = new HashSet<>();
        
        public enum AccessLevel {
            NO_ACCESS(0),
            READ_ONLY(1),
            READ_WRITE(2),
            ADMIN(3);
            
            private final int level;
            
            AccessLevel(int level) {
                this.level = level;
            }
            
            public boolean canRead() { return level >= 1; }
            public boolean canWrite() { return level >= 2; }
            public boolean canAdmin() { return level >= 3; }
        }
        
        public void grantAccess(String agentId, AccessLevel level) {
            agentPermissions.put(agentId, level);
            blockedAgents.remove(agentId);
        }
        
        public void grantTypeAccess(AgentType type, AccessLevel level) {
            typePermissions.put(type, level);
        }
        
        public void revokeAccess(String agentId) {
            agentPermissions.remove(agentId);
            blockedAgents.add(agentId);
        }
        
        public void setDefaultAccess(AccessLevel level) {
            this.defaultAccess = level;
        }
        
        public AccessLevel getAccessLevel(String agentId, AgentType agentType) {
            // Check if blocked
            if (blockedAgents.contains(agentId)) {
                return AccessLevel.NO_ACCESS;
            }
            
            // Check specific agent permission
            AccessLevel agentLevel = agentPermissions.get(agentId);
            if (agentLevel != null) {
                return agentLevel;
            }
            
            // Check agent type permission
            AccessLevel typeLevel = typePermissions.get(agentType);
            if (typeLevel != null) {
                return typeLevel;
            }
            
            // Return default
            return defaultAccess;
        }
        
        public Map<String, AccessLevel> getAllPermissions() {
            Map<String, AccessLevel> all = new HashMap<>(agentPermissions);
            typePermissions.forEach((type, level) -> 
                all.put("type:" + type.name(), level));
            all.put("default", defaultAccess);
            return all;
        }
    }
    
    /**
     * Memory index for fast searching
     */
    private static class MemoryIndex {
        private final Map<String, Set<String>> tagIndex = new ConcurrentHashMap<>();
        private final Map<String, Set<String>> agentIndex = new ConcurrentHashMap<>();
        private final Map<Long, Set<String>> timeIndex = new TreeMap<>();
        
        public void addToIndex(String segmentId, MemoryEntry entry) {
            // Index by tags
            entry.getTags().forEach((key, value) -> {
                String tagKey = key + ":" + value;
                tagIndex.computeIfAbsent(tagKey, k -> new HashSet<>()).add(segmentId);
            });
            
            // Index by agent
            agentIndex.computeIfAbsent(entry.getSourceAgentId(), k -> new HashSet<>())
                .add(segmentId);
            
            // Index by time (hourly buckets)
            long hourBucket = entry.getTimestamp() / (1000 * 60 * 60);
            timeIndex.computeIfAbsent(hourBucket, k -> new HashSet<>()).add(segmentId);
        }
        
        public Set<String> findByTag(String key, String value) {
            return tagIndex.getOrDefault(key + ":" + value, Collections.emptySet());
        }
        
        public Set<String> findByAgent(String agentId) {
            return agentIndex.getOrDefault(agentId, Collections.emptySet());
        }
        
        public Set<String> findByTimeRange(long startTime, long endTime) {
            long startBucket = startTime / (1000 * 60 * 60);
            long endBucket = endTime / (1000 * 60 * 60);
            
            Set<String> results = new HashSet<>();
            ((TreeMap<Long, Set<String>>) timeIndex).subMap(startBucket, endBucket + 1)
                .values().forEach(results::addAll);
            
            return results;
        }
    }
    
    /**
     * Create or get memory pool
     */
    public String createMemoryPool(String poolId, TenantContext tenant,
                                 MemorySegment.MemoryType type,
                                 Map<String, Object> metadata) {
        String fullPoolId = tenant.getTenantPath() + "/" + poolId;
        
        // Create initial segment
        String segmentId = fullPoolId + "/segment_" + UUID.randomUUID().toString();
        MemorySegment segment = new MemorySegment(segmentId, fullPoolId, type, metadata);
        memorySegments.put(segmentId, segment);
        
        // Create default ACL
        AccessControlList acl = new AccessControlList();
        acl.setDefaultAccess(AccessControlList.AccessLevel.READ_ONLY);
        accessControls.put(fullPoolId, acl);
        
        // Create index
        indices.put(fullPoolId, new MemoryIndex());
        
        // Publish creation event
        publishMemoryEvent("pool_created", fullPoolId, Map.of(
            "type", type.name(),
            "metadata", metadata
        ));
        
        return fullPoolId;
    }
    
    /**
     * Store memory with access control
     */
    public void storeMemory(String poolId, String key, Object value,
                          String agentId, AgentType agentType,
                          Map<String, String> tags) {
        // Check write permission
        AccessControlList acl = accessControls.get(poolId);
        if (acl == null) {
            throw new SecurityException("Memory pool not found: " + poolId);
        }
        
        AccessControlList.AccessLevel access = acl.getAccessLevel(agentId, agentType);
        if (!access.canWrite()) {
            throw new SecurityException("Agent " + agentId + " lacks write permission");
        }
        
        // Find or create segment
        MemorySegment segment = findOrCreateSegment(poolId);
        
        // Add entry
        segment.addEntry(key, value, agentId, tags);
        
        // Update index
        MemoryIndex index = indices.get(poolId);
        if (index != null) {
            index.addToIndex(segment.getSegmentId(), segment.getEntry(key));
        }
        
        // Publish event
        publishMemoryEvent("memory_stored", poolId, Map.of(
            "key", key,
            "agent", agentId,
            "tags", tags
        ));
    }
    
    /**
     * Retrieve memory with access control
     */
    public Object retrieveMemory(String poolId, String key,
                               String agentId, AgentType agentType) {
        // Check read permission
        AccessControlList acl = accessControls.get(poolId);
        if (acl == null) {
            return null;
        }
        
        AccessControlList.AccessLevel access = acl.getAccessLevel(agentId, agentType);
        if (!access.canRead()) {
            throw new SecurityException("Agent " + agentId + " lacks read permission");
        }
        
        // Search segments
        for (MemorySegment segment : getPoolSegments(poolId)) {
            MemoryEntry entry = segment.getEntry(key);
            if (entry != null) {
                entry.recordAccess(agentId);
                return entry.getValue();
            }
        }
        
        return null;
    }
    
    /**
     * Search memory across segments
     */
    public List<MemorySearchResult> searchMemory(String poolId, String query,
                                                Map<String, String> tagFilters,
                                                String agentId, AgentType agentType,
                                                int maxResults) {
        // Check read permission
        AccessControlList acl = accessControls.get(poolId);
        if (acl == null) {
            return Collections.emptyList();
        }
        
        AccessControlList.AccessLevel access = acl.getAccessLevel(agentId, agentType);
        if (!access.canRead()) {
            return Collections.emptyList();
        }
        
        List<MemorySearchResult> results = new ArrayList<>();
        
        // Use index for tag-based search
        Set<String> candidateSegments = null;
        if (tagFilters != null && !tagFilters.isEmpty()) {
            MemoryIndex index = indices.get(poolId);
            if (index != null) {
                for (Map.Entry<String, String> filter : tagFilters.entrySet()) {
                    Set<String> segments = index.findByTag(filter.getKey(), filter.getValue());
                    if (candidateSegments == null) {
                        candidateSegments = new HashSet<>(segments);
                    } else {
                        candidateSegments.retainAll(segments);
                    }
                }
            }
        }
        
        // Search segments
        for (MemorySegment segment : getPoolSegments(poolId)) {
            // Skip if not in candidate set
            if (candidateSegments != null && !candidateSegments.contains(segment.getSegmentId())) {
                continue;
            }
            
            Collection<MemoryEntry> entries = segment.searchEntries(query, tagFilters);
            
            for (MemoryEntry entry : entries) {
                entry.recordAccess(agentId);
                
                results.add(new MemorySearchResult(
                    entry.getKey(),
                    entry.getValue(),
                    entry.getSourceAgentId(),
                    entry.getTimestamp(),
                    entry.getTags(),
                    segment.getSegmentId(),
                    calculateRelevance(entry, query)
                ));
                
                if (results.size() >= maxResults) {
                    break;
                }
            }
            
            if (results.size() >= maxResults) {
                break;
            }
        }
        
        // Sort by relevance
        results.sort((a, b) -> Double.compare(b.getRelevance(), a.getRelevance()));
        
        return results;
    }
    
    /**
     * Memory search result
     */
    public static class MemorySearchResult {
        private final String key;
        private final Object value;
        private final String sourceAgentId;
        private final long timestamp;
        private final Map<String, String> tags;
        private final String segmentId;
        private final double relevance;
        
        public MemorySearchResult(String key, Object value, String sourceAgentId,
                                long timestamp, Map<String, String> tags,
                                String segmentId, double relevance) {
            this.key = key;
            this.value = value;
            this.sourceAgentId = sourceAgentId;
            this.timestamp = timestamp;
            this.tags = tags;
            this.segmentId = segmentId;
            this.relevance = relevance;
        }
        
        // Getters
        public String getKey() { return key; }
        public Object getValue() { return value; }
        public String getSourceAgentId() { return sourceAgentId; }
        public long getTimestamp() { return timestamp; }
        public Map<String, String> getTags() { return tags; }
        public String getSegmentId() { return segmentId; }
        public double getRelevance() { return relevance; }
    }
    
    /**
     * Grant access to memory pool
     */
    public void grantAccess(String poolId, String agentId, 
                          AccessControlList.AccessLevel level) {
        AccessControlList acl = accessControls.get(poolId);
        if (acl == null) {
            throw new IllegalArgumentException("Memory pool not found: " + poolId);
        }
        
        acl.grantAccess(agentId, level);
        
        // Publish event
        publishMemoryEvent("access_granted", poolId, Map.of(
            "agent", agentId,
            "level", level.name()
        ));
    }
    
    /**
     * Grant type-based access
     */
    public void grantTypeAccess(String poolId, AgentType agentType,
                              AccessControlList.AccessLevel level) {
        AccessControlList acl = accessControls.get(poolId);
        if (acl == null) {
            throw new IllegalArgumentException("Memory pool not found: " + poolId);
        }
        
        acl.grantTypeAccess(agentType, level);
        
        publishMemoryEvent("type_access_granted", poolId, Map.of(
            "agent_type", agentType.name(),
            "level", level.name()
        ));
    }
    
    /**
     * Share memory between pools
     */
    public void shareMemoryBetweenPools(String sourcePoolId, String targetPoolId,
                                      String key, String sharingAgentId) {
        // Retrieve from source
        Object value = retrieveMemory(sourcePoolId, key, sharingAgentId, null);
        if (value == null) {
            throw new IllegalArgumentException("Memory not found: " + key);
        }
        
        // Store in target with sharing metadata
        Map<String, String> tags = new HashMap<>();
        tags.put("shared_from", sourcePoolId);
        tags.put("shared_by", sharingAgentId);
        tags.put("share_time", String.valueOf(System.currentTimeMillis()));
        
        storeMemory(targetPoolId, key, value, sharingAgentId, null, tags);
    }
    
    /**
     * Get memory statistics
     */
    public MemoryPoolStatistics getStatistics(String poolId) {
        List<MemorySegment> segments = getPoolSegments(poolId);
        
        int totalEntries = segments.stream()
            .mapToInt(s -> s.getEntries().size())
            .sum();
        
        int totalSize = segments.stream()
            .mapToInt(MemorySegment::getSize)
            .sum();
        
        Map<String, Integer> entriesByAgent = new HashMap<>();
        Map<String, Integer> accessesByAgent = new HashMap<>();
        
        for (MemorySegment segment : segments) {
            for (MemoryEntry entry : segment.getEntries().values()) {
                entriesByAgent.merge(entry.getSourceAgentId(), 1, Integer::sum);
                
                // Count accesses
                for (String access : entry.getAccessHistory()) {
                    String agentId = access.split("@")[0];
                    accessesByAgent.merge(agentId, 1, Integer::sum);
                }
            }
        }
        
        return new MemoryPoolStatistics(
            poolId,
            segments.size(),
            totalEntries,
            totalSize,
            entriesByAgent,
            accessesByAgent
        );
    }
    
    /**
     * Memory pool statistics
     */
    public static class MemoryPoolStatistics {
        private final String poolId;
        private final int segmentCount;
        private final int entryCount;
        private final int totalSize;
        private final Map<String, Integer> entriesByAgent;
        private final Map<String, Integer> accessesByAgent;
        
        public MemoryPoolStatistics(String poolId, int segmentCount, int entryCount,
                                  int totalSize, Map<String, Integer> entriesByAgent,
                                  Map<String, Integer> accessesByAgent) {
            this.poolId = poolId;
            this.segmentCount = segmentCount;
            this.entryCount = entryCount;
            this.totalSize = totalSize;
            this.entriesByAgent = entriesByAgent;
            this.accessesByAgent = accessesByAgent;
        }
        
        // Getters
        public String getPoolId() { return poolId; }
        public int getSegmentCount() { return segmentCount; }
        public int getEntryCount() { return entryCount; }
        public int getTotalSize() { return totalSize; }
        public Map<String, Integer> getEntriesByAgent() { return entriesByAgent; }
        public Map<String, Integer> getAccessesByAgent() { return accessesByAgent; }
    }
    
    /**
     * Helper methods
     */
    
    private MemorySegment findOrCreateSegment(String poolId) {
        List<MemorySegment> segments = getPoolSegments(poolId);
        
        // Find segment with space
        for (MemorySegment segment : segments) {
            if (segment.getSize() < MAX_SEGMENT_SIZE) {
                return segment;
            }
        }
        
        // Create new segment if under limit
        if (segments.size() < MAX_SEGMENTS_PER_POOL) {
            String segmentId = poolId + "/segment_" + UUID.randomUUID().toString();
            MemorySegment newSegment = new MemorySegment(
                segmentId, poolId, 
                segments.isEmpty() ? MemorySegment.MemoryType.SHARED_CONTEXT : 
                    segments.get(0).getType(),
                new HashMap<>()
            );
            memorySegments.put(segmentId, newSegment);
            return newSegment;
        }
        
        throw new IllegalStateException("Memory pool segment limit reached");
    }
    
    private List<MemorySegment> getPoolSegments(String poolId) {
        return memorySegments.values().stream()
            .filter(s -> s.getPoolId().equals(poolId))
            .sorted(Comparator.comparingLong(MemorySegment::getCreatedAt))
            .collect(Collectors.toList());
    }
    
    private double calculateRelevance(MemoryEntry entry, String query) {
        if (query == null || query.isEmpty()) {
            return 1.0;
        }
        
        String content = entry.getKey() + " " + entry.getValue().toString();
        String[] queryTerms = query.toLowerCase().split("\\s+");
        String[] contentTerms = content.toLowerCase().split("\\s+");
        
        // Simple term frequency
        int matches = 0;
        for (String queryTerm : queryTerms) {
            for (String contentTerm : contentTerms) {
                if (contentTerm.contains(queryTerm)) {
                    matches++;
                }
            }
        }
        
        // Factor in recency
        long age = System.currentTimeMillis() - entry.getTimestamp();
        double recencyFactor = Math.exp(-age / (24.0 * 60 * 60 * 1000)); // Decay over days
        
        // Factor in access frequency
        double popularityFactor = Math.min(1.0, entry.getAccessCount() / 10.0);
        
        return (matches / (double) queryTerms.length) * 0.6 +
               recencyFactor * 0.3 +
               popularityFactor * 0.1;
    }
    
    private void publishMemoryEvent(String eventType, String poolId,
                                  Map<String, Object> data) {
        try {
            Map<String, Object> event = new HashMap<>(data);
            event.put("event_type", eventType);
            event.put("pool_id", poolId);
            event.put("timestamp", System.currentTimeMillis());
            
            PubsubMessage message = PubsubMessage.newBuilder()
                .setData(ByteString.copyFromUtf8(event.toString()))
                .putAttributes("event_type", eventType)
                .putAttributes("pool_id", poolId)
                .build();
            
            eventPublisher.publish(message);
        } catch (Exception e) {
            System.err.println("Failed to publish memory event: " + e.getMessage());
        }
    }
    
    private void cleanupExpiredSegments() {
        List<String> toRemove = new ArrayList<>();
        
        for (Map.Entry<String, MemorySegment> entry : memorySegments.entrySet()) {
            if (entry.getValue().isExpired()) {
                toRemove.add(entry.getKey());
            }
        }
        
        toRemove.forEach(memorySegments::remove);
        
        if (!toRemove.isEmpty()) {
            System.out.println("Cleaned up " + toRemove.size() + " expired memory segments");
        }
    }
    
    private void persistMemoryState() {
        // Persist memory segments to Firestore
        for (MemorySegment segment : memorySegments.values()) {
            if (segment.getVersion() > 0) { // Only persist if changed
                Map<String, Object> data = new HashMap<>();
                data.put("segment_id", segment.getSegmentId());
                data.put("pool_id", segment.getPoolId());
                data.put("type", segment.getType().name());
                data.put("created_at", segment.getCreatedAt());
                data.put("last_access", segment.getLastAccessTime());
                data.put("version", segment.getVersion());
                data.put("metadata", segment.getMetadata());
                data.put("entry_count", segment.getEntries().size());
                
                firestore.collection("memory_segments")
                    .document(segment.getSegmentId())
                    .set(data);
            }
        }
    }
    
    /**
     * Load memory state from persistence
     */
    public void loadMemoryState() {
        try {
            firestore.collection("memory_segments")
                .get()
                .get()
                .getDocuments()
                .forEach(doc -> {
                    // Reconstruct segment from persisted data
                    String segmentId = doc.getString("segment_id");
                    String poolId = doc.getString("pool_id");
                    MemorySegment.MemoryType type = MemorySegment.MemoryType.valueOf(
                        doc.getString("type"));
                    Map<String, Object> metadata = (Map<String, Object>) doc.get("metadata");
                    
                    MemorySegment segment = new MemorySegment(segmentId, poolId, type, metadata);
                    memorySegments.put(segmentId, segment);
                    
                    // Load entries would happen here
                });
        } catch (Exception e) {
            System.err.println("Failed to load memory state: " + e.getMessage());
        }
    }
    
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            
            // Final persistence
            persistMemoryState();
            
            eventPublisher.shutdown();
            eventPublisher.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}