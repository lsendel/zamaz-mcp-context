package com.zamaz.adk.tools;

import com.google.cloud.firestore.Firestore;
import com.zamaz.adk.core.TenantAwareService;
import com.zamaz.adk.core.TenantContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tenant-aware Dynamic Tool Selector
 * Provides isolated tool selection per tenant with embeddings
 */
public class TenantAwareDynamicToolSelector extends TenantAwareService {
    private final Map<String, TenantToolRegistry> tenantRegistries = new ConcurrentHashMap<>();
    
    public TenantAwareDynamicToolSelector(Firestore firestore, String projectId, String location) {
        super(projectId, location, firestore);
    }
    
    private TenantToolRegistry getTenantRegistry(TenantContext tenant) {
        return tenantRegistries.computeIfAbsent(tenant.getTenantPath(), 
            k -> new TenantToolRegistry());
    }
    
    public List<TenantToolMatch> selectTools(TenantContext tenant, String query, 
                                            int maxTools, List<String> categories, 
                                            double minSimilarity) {
        TenantToolRegistry registry = getTenantRegistry(tenant);
        
        // In production, would use Vertex AI embeddings for similarity
        List<TenantToolMatch> matches = new ArrayList<>();
        
        for (TenantTool tool : registry.getTools()) {
            // Simple category matching for now
            if (!categories.isEmpty() && 
                Collections.disjoint(tool.getCategories(), categories)) {
                continue;
            }
            
            // Calculate similarity (mock for now)
            double similarity = calculateSimilarity(query, tool.getDescription());
            if (similarity >= minSimilarity) {
                matches.add(new TenantToolMatch(tool, similarity, 
                    "Matched on: " + tool.getCategories()));
            }
        }
        
        // Sort by similarity and limit
        matches.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
        return matches.subList(0, Math.min(matches.size(), maxTools));
    }
    
    public String indexTool(TenantContext tenant, String name, String description,
                          List<String> categories, Map<String, Object> inputSchema,
                          Map<String, String> metadata) {
        TenantToolRegistry registry = getTenantRegistry(tenant);
        
        String toolId = tenant.getTenantPath() + "_" + name + "_" + UUID.randomUUID();
        TenantTool tool = new TenantTool(toolId, name, description, categories, metadata);
        
        registry.addTool(tool);
        
        auditLog(tenant, "tool.indexed", "Tool: " + name);
        
        return toolId;
    }
    
    public int getTotalTools() {
        return tenantRegistries.values().stream()
            .mapToInt(r -> r.getTools().size())
            .sum();
    }
    
    private double calculateSimilarity(String query, String description) {
        // Mock similarity - in production would use embeddings
        return query.toLowerCase().contains(description.toLowerCase().substring(0, 
            Math.min(5, description.length()))) ? 0.8 : 0.3;
    }
    
    private static class TenantToolRegistry {
        private final List<TenantTool> tools = new ArrayList<>();
        
        public void addTool(TenantTool tool) {
            tools.add(tool);
        }
        
        public List<TenantTool> getTools() {
            return new ArrayList<>(tools);
        }
    }
}

class TenantTool {
    private final String id;
    private final String name;
    private final String description;
    private final List<String> categories;
    private final Map<String, String> metadata;
    
    public TenantTool(String id, String name, String description, 
                     List<String> categories, Map<String, String> metadata) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.categories = categories;
        this.metadata = metadata;
    }
    
    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<String> getCategories() { return categories; }
    public Map<String, String> getMetadata() { return metadata; }
}

class TenantToolMatch {
    private final TenantTool tool;
    private final double similarity;
    private final String reason;
    
    public TenantToolMatch(TenantTool tool, double similarity, String reason) {
        this.tool = tool;
        this.similarity = similarity;
        this.reason = reason;
    }
    
    public TenantTool getTool() { return tool; }
    public double getSimilarity() { return similarity; }
    public String getReason() { return reason; }
}