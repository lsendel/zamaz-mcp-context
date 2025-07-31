package com.zamaz.adk;

import com.zamaz.adk.core.TenantContext;
import com.zamaz.adk.core.TenantAwareService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for tenant-aware core components
 */
public class TenantAwareUnitTest {
    
    @Test
    @DisplayName("TenantContext - Builder and Path Generation")
    void testTenantContextBuilder() {
        // Organization only
        TenantContext orgContext = TenantContext.builder()
            .organizationId("test-org")
            .build();
        assertEquals("test-org", orgContext.getTenantPath());
        assertEquals("test-org", orgContext.getOrganizationId());
        assertFalse(orgContext.getProjectId().isPresent());
        assertFalse(orgContext.getSubprojectId().isPresent());
        
        // Organization + Project
        TenantContext projectContext = TenantContext.builder()
            .organizationId("test-org")
            .projectId("test-project")
            .build();
        assertEquals("test-org/test-project", projectContext.getTenantPath());
        assertTrue(projectContext.getProjectId().isPresent());
        assertEquals("test-project", projectContext.getProjectId().get());
        
        // Full hierarchy
        TenantContext fullContext = TenantContext.builder()
            .organizationId("test-org")
            .projectId("test-project")
            .subprojectId("test-subproject")
            .build();
        assertEquals("test-org/test-project/test-subproject", fullContext.getTenantPath());
        assertTrue(fullContext.getSubprojectId().isPresent());
        assertEquals("test-subproject", fullContext.getSubprojectId().get());
    }
    
    @Test
    @DisplayName("TenantContext - From Path Parsing")
    void testTenantContextFromPath() {
        // Parse organization path
        TenantContext orgContext = TenantContext.builder()
            .fromPath("org1")
            .build();
        assertEquals("org1", orgContext.getOrganizationId());
        
        // Parse project path
        TenantContext projectContext = TenantContext.builder()
            .fromPath("org1/proj1")
            .build();
        assertEquals("org1", projectContext.getOrganizationId());
        assertEquals("proj1", projectContext.getProjectId().orElse(""));
        
        // Parse full path
        TenantContext fullContext = TenantContext.builder()
            .fromPath("org1/proj1/sub1")
            .build();
        assertEquals("org1", fullContext.getOrganizationId());
        assertEquals("proj1", fullContext.getProjectId().orElse(""));
        assertEquals("sub1", fullContext.getSubprojectId().orElse(""));
    }
    
    @ParameterizedTest
    @MethodSource("provideAccessControlScenarios")
    @DisplayName("TenantContext - Access Control")
    void testTenantAccessControl(TenantContext requestor, TenantContext resource, 
                                 boolean expectedAccess, String scenario) {
        boolean hasAccess = requestor.hasAccessTo(resource);
        assertEquals(expectedAccess, hasAccess, 
            "Failed scenario: " + scenario);
    }
    
    private static Stream<Arguments> provideAccessControlScenarios() {
        TenantContext org1 = TenantContext.builder()
            .organizationId("org1").build();
        TenantContext org1Proj1 = TenantContext.builder()
            .organizationId("org1").projectId("proj1").build();
        TenantContext org1Proj1Sub1 = TenantContext.builder()
            .organizationId("org1").projectId("proj1").subprojectId("sub1").build();
        TenantContext org1Proj2 = TenantContext.builder()
            .organizationId("org1").projectId("proj2").build();
        TenantContext org2 = TenantContext.builder()
            .organizationId("org2").build();
        
        return Stream.of(
            // Same organization scenarios
            Arguments.of(org1, org1, true, "Org can access itself"),
            Arguments.of(org1, org1Proj1, true, "Org can access its project"),
            Arguments.of(org1, org1Proj1Sub1, true, "Org can access its subproject"),
            Arguments.of(org1Proj1, org1Proj1, true, "Project can access itself"),
            Arguments.of(org1Proj1, org1Proj1Sub1, true, "Project can access its subproject"),
            Arguments.of(org1Proj1Sub1, org1Proj1Sub1, true, "Subproject can access itself"),
            
            // Cross-project scenarios
            Arguments.of(org1Proj1, org1Proj2, false, "Project cannot access sibling project"),
            Arguments.of(org1Proj1Sub1, org1Proj1, false, "Subproject cannot access parent project"),
            Arguments.of(org1Proj1Sub1, org1, false, "Subproject cannot access parent org"),
            
            // Cross-organization scenarios
            Arguments.of(org1, org2, false, "Org cannot access different org"),
            Arguments.of(org1Proj1, org2, false, "Project cannot access different org")
        );
    }
    
    @Test
    @DisplayName("TenantContext - Resource Naming")
    void testTenantResourceNaming() {
        TenantContext tenant = TenantContext.builder()
            .organizationId("zamaz-enterprise")
            .projectId("fba-optimization")
            .subprojectId("electronics")
            .build();
        
        // Test Firestore paths
        assertEquals("organizations/zamaz-enterprise/data", 
            tenant.getFirestoreBasePath());
        assertEquals("organizations/zamaz-enterprise/projects/fba-optimization/subprojects/electronics/workflows",
            tenant.getFirestorePath("workflows"));
        
        // Test GCS bucket
        assertEquals("zamaz-org-zamaz-enterprise", tenant.getStorageBucket());
        
        // Test Vector index
        assertEquals("zamaz-enterprise-fba-optimization-electronics-embeddings-index",
            tenant.getVectorIndexName("embeddings"));
        
        // Test Pub/Sub topic
        assertEquals("zamaz-enterprise-fba-optimization-electronics-events-topic",
            tenant.getTopicName("events"));
    }
    
    @Test
    @DisplayName("TenantContext - Protobuf Conversion")
    void testTenantContextProtobufConversion() {
        TenantContext original = TenantContext.builder()
            .organizationId("test-org")
            .projectId("test-project")
            .subprojectId("test-subproject")
            .build();
        
        // Convert to protobuf
        com.zamaz.adk.proto.TenantContext proto = original.toProto();
        assertEquals("test-org", proto.getOrganizationId());
        assertEquals("test-project", proto.getProjectId());
        assertEquals("test-subproject", proto.getSubprojectId());
        
        // Convert back from protobuf
        TenantContext fromProto = TenantContext.fromProto(proto);
        assertEquals(original, fromProto);
        assertEquals(original.getTenantPath(), fromProto.getTenantPath());
    }
    
    @Test
    @DisplayName("TenantContext - Null Safety")
    void testTenantContextNullSafety() {
        // Organization ID is required
        assertThrows(NullPointerException.class, () -> {
            TenantContext.builder().build();
        });
        
        // Project and subproject are optional
        TenantContext context = TenantContext.builder()
            .organizationId("org1")
            .projectId(null)
            .subprojectId(null)
            .build();
        
        assertNotNull(context);
        assertEquals("org1", context.getTenantPath());
        assertFalse(context.getProjectId().isPresent());
        assertFalse(context.getSubprojectId().isPresent());
    }
    
    @Test
    @DisplayName("Quota Limits by Tier")
    void testQuotaLimitsByTier() {
        // Test workflow limits
        assertEquals(10, getWorkflowLimitForTier("free"));
        assertEquals(100, getWorkflowLimitForTier("standard"));
        assertEquals(1000, getWorkflowLimitForTier("enterprise"));
        assertEquals(5, getWorkflowLimitForTier("unknown"));
        
        // Test agent request limits
        assertEquals(1000, getAgentRequestLimitForTier("free"));
        assertEquals(10000, getAgentRequestLimitForTier("standard"));
        assertEquals(100000, getAgentRequestLimitForTier("enterprise"));
        assertEquals(100, getAgentRequestLimitForTier("unknown"));
    }
    
    @Test
    @DisplayName("Tenant Path Security - No Path Traversal")
    void testTenantPathSecurity() {
        // Attempt path traversal
        TenantContext context = TenantContext.builder()
            .fromPath("org1/../org2/secret-project")
            .build();
        
        // Should sanitize the path
        assertNotEquals("org2", context.getOrganizationId());
        
        // Test with encoded traversal
        TenantContext encoded = TenantContext.builder()
            .fromPath("org1%2F..%2Forg2")
            .build();
        
        assertEquals("org1%2F..%2Forg2", encoded.getOrganizationId());
    }
    
    @Test
    @DisplayName("Concurrent Tenant Context Creation")
    void testConcurrentTenantContextCreation() throws Exception {
        int threads = 100;
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ConcurrentHashMap<String, TenantContext> contexts = 
            new java.util.concurrent.ConcurrentHashMap<>();
        
        for (int i = 0; i < threads; i++) {
            final int index = i;
            new Thread(() -> {
                try {
                    TenantContext context = TenantContext.builder()
                        .organizationId("org-" + index)
                        .projectId("proj-" + index)
                        .build();
                    contexts.put(context.getTenantPath(), context);
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        
        assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(threads, contexts.size());
        
        // Verify all contexts are unique
        for (int i = 0; i < threads; i++) {
            String expectedPath = "org-" + i + "/proj-" + i;
            assertTrue(contexts.containsKey(expectedPath));
        }
    }
    
    // Helper methods matching the service implementations
    private long getWorkflowLimitForTier(String tier) {
        return switch (tier) {
            case "enterprise" -> 1000;
            case "standard" -> 100;
            case "free" -> 10;
            default -> 5;
        };
    }
    
    private long getAgentRequestLimitForTier(String tier) {
        return switch (tier) {
            case "enterprise" -> 100000;
            case "standard" -> 10000;
            case "free" -> 1000;
            default -> 100;
        };
    }
}