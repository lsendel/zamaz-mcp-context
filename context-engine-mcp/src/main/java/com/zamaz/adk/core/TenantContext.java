package com.zamaz.adk.core;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.util.Objects;
import java.util.Optional;

/**
 * Multi-tenant context for all Zamaz ADK services
 * Supports hierarchical organization/project/subproject structure
 */
public class TenantContext {
    private final String organizationId;
    private final String projectId;
    private final String subprojectId;
    
    // Private constructor - use builder
    private TenantContext(String organizationId, String projectId, String subprojectId) {
        this.organizationId = Preconditions.checkNotNull(organizationId, "organizationId cannot be null");
        this.projectId = projectId;
        this.subprojectId = subprojectId;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Create tenant context from protobuf
     */
    public static TenantContext fromProto(com.zamaz.adk.proto.TenantContext proto) {
        return builder()
            .organizationId(proto.getOrganizationId())
            .projectId(proto.getProjectId())
            .subprojectId(proto.getSubprojectId())
            .build();
    }
    
    /**
     * Convert to protobuf
     */
    public com.zamaz.adk.proto.TenantContext toProto() {
        com.zamaz.adk.proto.TenantContext.Builder builder = 
            com.zamaz.adk.proto.TenantContext.newBuilder()
                .setOrganizationId(organizationId);
        
        if (projectId != null) {
            builder.setProjectId(projectId);
        }
        if (subprojectId != null) {
            builder.setSubprojectId(subprojectId);
        }
        
        return builder.build();
    }
    
    /**
     * Get the full tenant path (e.g., "org1/proj1/sub1")
     */
    public String getTenantPath() {
        StringBuilder path = new StringBuilder(organizationId);
        
        if (!Strings.isNullOrEmpty(projectId)) {
            path.append("/").append(projectId);
            
            if (!Strings.isNullOrEmpty(subprojectId)) {
                path.append("/").append(subprojectId);
            }
        }
        
        return path.toString();
    }
    
    /**
     * Get Firestore collection path for tenant isolation
     */
    public String getFirestoreBasePath() {
        return String.format("organizations/%s/data", organizationId);
    }
    
    /**
     * Get full Firestore path with optional project/subproject
     */
    public String getFirestorePath(String collection) {
        StringBuilder path = new StringBuilder("organizations/")
            .append(organizationId);
        
        if (!Strings.isNullOrEmpty(projectId)) {
            path.append("/projects/").append(projectId);
            
            if (!Strings.isNullOrEmpty(subprojectId)) {
                path.append("/subprojects/").append(subprojectId);
            }
        }
        
        path.append("/").append(collection);
        return path.toString();
    }
    
    /**
     * Get GCS bucket name for tenant
     */
    public String getStorageBucket() {
        return String.format("zamaz-org-%s", organizationId.toLowerCase());
    }
    
    /**
     * Get Vector Search index name for tenant
     */
    public String getVectorIndexName(String indexType) {
        return String.format("%s-%s-index", getTenantPath().replace("/", "-"), indexType);
    }
    
    /**
     * Get Pub/Sub topic name for tenant
     */
    public String getTopicName(String topicType) {
        return String.format("%s-%s-topic", getTenantPath().replace("/", "-"), topicType);
    }
    
    /**
     * Check if this context has access to another context
     */
    public boolean hasAccessTo(TenantContext other) {
        // Organization must match
        if (!organizationId.equals(other.organizationId)) {
            return false;
        }
        
        // If we have no project restriction, we have access to all projects in org
        if (Strings.isNullOrEmpty(projectId)) {
            return true;
        }
        
        // Project must match if specified
        if (!Objects.equals(projectId, other.projectId)) {
            return false;
        }
        
        // If we have no subproject restriction, we have access to all subprojects
        if (Strings.isNullOrEmpty(subprojectId)) {
            return true;
        }
        
        // Subproject must match if specified
        return Objects.equals(subprojectId, other.subprojectId);
    }
    
    // Getters
    public String getOrganizationId() {
        return organizationId;
    }
    
    public Optional<String> getProjectId() {
        return Optional.ofNullable(projectId);
    }
    
    public Optional<String> getSubprojectId() {
        return Optional.ofNullable(subprojectId);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TenantContext that = (TenantContext) o;
        return Objects.equals(organizationId, that.organizationId) &&
               Objects.equals(projectId, that.projectId) &&
               Objects.equals(subprojectId, that.subprojectId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(organizationId, projectId, subprojectId);
    }
    
    @Override
    public String toString() {
        return "TenantContext{path='" + getTenantPath() + "'}";
    }
    
    /**
     * Builder for TenantContext
     */
    public static class Builder {
        private String organizationId;
        private String projectId;
        private String subprojectId;
        
        public Builder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }
        
        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }
        
        public Builder subprojectId(String subprojectId) {
            this.subprojectId = subprojectId;
            return this;
        }
        
        /**
         * Parse from path string (e.g., "org1/proj1/sub1")
         */
        public Builder fromPath(String path) {
            String[] parts = path.split("/");
            
            if (parts.length >= 1) {
                this.organizationId = parts[0];
            }
            if (parts.length >= 2 && !Strings.isNullOrEmpty(parts[1])) {
                this.projectId = parts[1];
            }
            if (parts.length >= 3 && !Strings.isNullOrEmpty(parts[2])) {
                this.subprojectId = parts[2];
            }
            
            return this;
        }
        
        public TenantContext build() {
            return new TenantContext(organizationId, projectId, subprojectId);
        }
    }
}