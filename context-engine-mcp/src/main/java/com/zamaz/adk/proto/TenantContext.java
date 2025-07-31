package com.zamaz.adk.proto;

/**
 * Protocol Buffer representation of TenantContext
 * In a real implementation, this would be generated from .proto files
 */
public class TenantContext {
    private String organizationId;
    private String projectId;
    private String subprojectId;
    
    public TenantContext() {}
    
    public TenantContext(String organizationId, String projectId, String subprojectId) {
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.subprojectId = subprojectId;
    }
    
    public static Builder newBuilder() {
        return new Builder();
    }
    
    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getSubprojectId() { return subprojectId; }
    public void setSubprojectId(String subprojectId) { this.subprojectId = subprojectId; }
    
    public static class Builder {
        private String organizationId;
        private String projectId;
        private String subprojectId;
        
        public Builder setOrganizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }
        
        public Builder setProjectId(String projectId) {
            this.projectId = projectId;
            return this;
        }
        
        public Builder setSubprojectId(String subprojectId) {
            this.subprojectId = subprojectId;
            return this;
        }
        
        public TenantContext build() {
            return new TenantContext(organizationId, projectId, subprojectId);
        }
    }
}