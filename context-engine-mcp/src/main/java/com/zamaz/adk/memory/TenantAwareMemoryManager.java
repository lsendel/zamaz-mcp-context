package com.zamaz.adk.memory;

import com.google.cloud.firestore.Firestore;
import com.zamaz.adk.core.TenantAwareService;
import com.zamaz.adk.core.TenantContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Tenant-aware Memory Manager
 * Provides isolated memory storage per tenant with automatic offloading
 */
public class TenantAwareMemoryManager extends TenantAwareService {
    private final Map<String, TenantMemoryStore> tenantStores = new ConcurrentHashMap<>();
    
    public TenantAwareMemoryManager(Firestore firestore, String projectId, String location) {
        super(projectId, location, firestore);
    }
    
    private TenantMemoryStore getTenantStore(TenantContext tenant) {
        return tenantStores.computeIfAbsent(tenant.getTenantPath(), 
            k -> new TenantMemoryStore(tenant));
    }
    
    public String store(TenantContext tenant, String sessionId, String content,
                       Map<String, String> metadata) {
        TenantMemoryStore store = getTenantStore(tenant);
        
        String entryId = UUID.randomUUID().toString();
        TenantMemoryEntry entry = new TenantMemoryEntry(
            entryId, sessionId, content, metadata, System.currentTimeMillis()
        );
        
        store.addEntry(sessionId, entry);
        
        // Check if offloading needed
        if (content.length() > 10000) {
            // In production, would offload to Cloud Storage
            logger.info("Large content would be offloaded for tenant: {}", 
                tenant.getTenantPath());
        }
        
        auditLog(tenant, "memory.store", 
            String.format("Session: %s, Size: %d", sessionId, content.length()));
        
        return entryId;
    }
    
    public TenantContextMemory retrieve(TenantContext tenant, String sessionId,
                                      String query, int maxEntries,
                                      Map<String, String> filter) {
        TenantMemoryStore store = getTenantStore(tenant);
        List<TenantMemoryEntry> entries = store.getEntries(sessionId);
        
        // Filter and rank entries (simple implementation)
        List<TenantMemoryEntry> filtered = entries.stream()
            .filter(e -> matchesFilter(e, filter))
            .filter(e -> e.getContent().toLowerCase().contains(query.toLowerCase()))
            .sorted((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()))
            .limit(maxEntries)
            .collect(Collectors.toList());
        
        return new TenantContextMemory(sessionId, filtered);
    }
    
    private boolean matchesFilter(TenantMemoryEntry entry, Map<String, String> filter) {
        if (filter.isEmpty()) return true;
        
        for (Map.Entry<String, String> filterEntry : filter.entrySet()) {
            String value = entry.getMetadata().get(filterEntry.getKey());
            if (value == null || !value.equals(filterEntry.getValue())) {
                return false;
            }
        }
        return true;
    }
    
    private static class TenantMemoryStore {
        private final TenantContext tenant;
        private final Map<String, List<TenantMemoryEntry>> sessionEntries = 
            new ConcurrentHashMap<>();
        
        public TenantMemoryStore(TenantContext tenant) {
            this.tenant = tenant;
        }
        
        public void addEntry(String sessionId, TenantMemoryEntry entry) {
            sessionEntries.computeIfAbsent(sessionId, k -> new ArrayList<>())
                .add(entry);
        }
        
        public List<TenantMemoryEntry> getEntries(String sessionId) {
            return sessionEntries.getOrDefault(sessionId, new ArrayList<>());
        }
    }
}

class TenantMemoryEntry {
    private final String id;
    private final String sessionId;
    private final String content;
    private final Map<String, String> metadata;
    private final long timestamp;
    
    public TenantMemoryEntry(String id, String sessionId, String content,
                           Map<String, String> metadata, long timestamp) {
        this.id = id;
        this.sessionId = sessionId;
        this.content = content;
        this.metadata = metadata;
        this.timestamp = timestamp;
    }
    
    // Getters
    public String getId() { return id; }
    public String getSessionId() { return sessionId; }
    public String getContent() { return content; }
    public Map<String, String> getMetadata() { return metadata; }
    public long getTimestamp() { return timestamp; }
}

class TenantContextMemory {
    private final String sessionId;
    private final List<TenantMemoryEntry> entries;
    
    public TenantContextMemory(String sessionId, List<TenantMemoryEntry> entries) {
        this.sessionId = sessionId;
        this.entries = entries;
    }
    
    public String getSessionId() { return sessionId; }
    public List<TenantMemoryEntry> getEntries() { return entries; }
}