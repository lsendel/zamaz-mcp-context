package com.zamaz.adk.core;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.storage.Storage;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Map;

/**
 * Base class for all tenant-aware services in Zamaz ADK
 * Provides tenant isolation and resource management
 */
public abstract class TenantAwareService {
    private static final Logger logger = LoggerFactory.getLogger(TenantAwareService.class);
    
    protected final String baseProjectId;
    protected final String location;
    protected final Firestore firestore;
    
    @Value("#{${tenant.organizations}}")
    private Map<String, String> organizationProjectMappings;
    
    // Cache tenant-specific resources
    private final ConcurrentMap<String, Object> tenantResourceCache = new ConcurrentHashMap<>();
    
    protected TenantAwareService(String baseProjectId, String location, Firestore firestore) {
        this.baseProjectId = Preconditions.checkNotNull(baseProjectId, "baseProjectId cannot be null");
        this.location = Preconditions.checkNotNull(location, "location cannot be null");
        this.firestore = Preconditions.checkNotNull(firestore, "firestore cannot be null");
    }
    
    /**
     * Get the Google Cloud project ID for a tenant
     * Organizations can have their own GCP projects
     */
    protected String getTenantProjectId(TenantContext tenant) {
        // Check if organization has a custom project mapping
        String customProjectId = getOrganizationProjectMapping(tenant.getOrganizationId());
        if (customProjectId != null) {
            return customProjectId;
        }
        
        // Default to base project with tenant prefix
        return baseProjectId;
    }
    
    /**
     * Get or create a tenant-specific resource
     */
    @SuppressWarnings("unchecked")
    protected <T> T getTenantResource(TenantContext tenant, String resourceType, 
                                     ResourceFactory<T> factory) {
        String cacheKey = tenant.getTenantPath() + ":" + resourceType;
        
        return (T) tenantResourceCache.computeIfAbsent(cacheKey, key -> {
            try {
                logger.info("Creating {} for tenant {}", resourceType, tenant.getTenantPath());
                return factory.create(tenant);
            } catch (Exception e) {
                logger.error("Failed to create {} for tenant {}", resourceType, tenant, e);
                throw new RuntimeException("Failed to create resource", e);
            }
        });
    }
    
    /**
     * Validate tenant access for an operation
     */
    protected void validateTenantAccess(TenantContext requestTenant, TenantContext resourceTenant) {
        if (!requestTenant.hasAccessTo(resourceTenant)) {
            throw new SecurityException(String.format(
                "Tenant %s does not have access to resource owned by %s",
                requestTenant.getTenantPath(), resourceTenant.getTenantPath()
            ));
        }
    }
    
    /**
     * Get tenant-isolated Firestore collection
     */
    protected com.google.cloud.firestore.CollectionReference getTenantCollection(
            TenantContext tenant, String collection) {
        
        // Validate collection name for security
        if (collection == null || collection.trim().isEmpty()) {
            throw new IllegalArgumentException("Collection name cannot be null or empty");
        }
        
        // Sanitize collection name to prevent injection attacks
        String sanitizedCollection = sanitizeFirestorePath(collection);
        
        String path = tenant.getFirestorePath(sanitizedCollection);
        logger.debug("Accessing Firestore collection: {}", path);
        
        // Validate and sanitize the full path
        String sanitizedPath = sanitizeFirestorePath(path);
        
        // Navigate through the path hierarchy
        String[] pathParts = sanitizedPath.split("/");
        com.google.cloud.firestore.DocumentReference docRef = null;
        
        for (int i = 0; i < pathParts.length - 1; i += 2) {
            if (docRef == null) {
                docRef = firestore.collection(pathParts[i]).document(pathParts[i + 1]);
            } else {
                docRef = docRef.collection(pathParts[i]).document(pathParts[i + 1]);
            }
        }
        
        // Return the final collection
        return docRef != null ? 
            docRef.collection(pathParts[pathParts.length - 1]) : 
            firestore.collection(pathParts[pathParts.length - 1]);
    }
    
    /**
     * Sanitize Firestore paths to prevent injection attacks
     */
    private String sanitizeFirestorePath(String path) {
        if (path == null) {
            throw new IllegalArgumentException("Path cannot be null");
        }
        
        // Remove dangerous characters and sequences
        String sanitized = path
            .replaceAll("[^a-zA-Z0-9/_-]", "") // Allow only alphanumeric, underscore, slash, hyphen
            .replaceAll("/{2,}", "/") // Remove multiple consecutive slashes
            .replaceAll("^/|/$", ""); // Remove leading/trailing slashes
        
        // Validate path length
        if (sanitized.length() > 1500) { // Firestore path limit
            throw new IllegalArgumentException("Path too long: " + sanitized.length());
        }
        
        // Validate path structure (must not be empty after sanitization)
        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("Invalid path after sanitization");
        }
        
        return sanitized;
    }
    
    /**
     * Get tenant-specific configuration
     */
    protected TenantConfiguration getTenantConfiguration(TenantContext tenant) {
        return getTenantResource(tenant, "configuration", t -> {
            // Load configuration from Firestore
            return firestore.collection("tenant_configurations")
                .document(t.getTenantPath().replace("/", "_"))
                .get()
                .get()
                .toObject(TenantConfiguration.class);
        });
    }
    
    /**
     * Log tenant operation for audit
     */
    protected void auditLog(TenantContext tenant, String operation, String details) {
        logger.info("AUDIT: Tenant={}, Operation={}, Details={}", 
            tenant.getTenantPath(), operation, details);
        
        // Store audit log in Firestore
        firestore.collection("audit_logs").add(new AuditLogEntry(
            tenant.getTenantPath(),
            operation,
            details,
            System.currentTimeMillis()
        ));
    }
    
    /**
     * Get organization to GCP project mapping from configuration
     */
    private String getOrganizationProjectMapping(String organizationId) {
        if (organizationProjectMappings == null) {
            logger.warn("Organization project mappings not configured");
            return null;
        }
        return organizationProjectMappings.get(organizationId);
    }
    
    /**
     * Factory interface for creating tenant-specific resources
     */
    @FunctionalInterface
    protected interface ResourceFactory<T> {
        T create(TenantContext tenant) throws Exception;
    }
    
    /**
     * Tenant configuration class
     */
    public static class TenantConfiguration {
        private String organizationName;
        private String tier; // free, standard, enterprise
        private long quotaLimit;
        private boolean customModelEnabled;
        private String preferredRegion;
        
        // Getters and setters
        public String getOrganizationName() { return organizationName; }
        public void setOrganizationName(String name) { this.organizationName = name; }
        
        public String getTier() { return tier; }
        public void setTier(String tier) { this.tier = tier; }
        
        public long getQuotaLimit() { return quotaLimit; }
        public void setQuotaLimit(long limit) { this.quotaLimit = limit; }
        
        public boolean isCustomModelEnabled() { return customModelEnabled; }
        public void setCustomModelEnabled(boolean enabled) { this.customModelEnabled = enabled; }
        
        public String getPreferredRegion() { return preferredRegion; }
        public void setPreferredRegion(String region) { this.preferredRegion = region; }
    }
    
    /**
     * Audit log entry
     */
    private static class AuditLogEntry {
        private final String tenantPath;
        private final String operation;
        private final String details;
        private final long timestamp;
        
        public AuditLogEntry(String tenantPath, String operation, String details, long timestamp) {
            this.tenantPath = tenantPath;
            this.operation = operation;
            this.details = details;
            this.timestamp = timestamp;
        }
        
        // Getters for Firestore serialization
        public String getTenantPath() { return tenantPath; }
        public String getOperation() { return operation; }
        public String getDetails() { return details; }
        public long getTimestamp() { return timestamp; }
    }
}