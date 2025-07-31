package com.zamaz.adk;

import com.zamaz.adk.exceptions.*;
import com.zamaz.adk.core.TenantContext;
import com.zamaz.adk.integration.UnifiedContextEngine;
import com.zamaz.adk.agents.MultiAgentOrchestrator;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for custom exception handling and error context
 */
@SpringBootTest
@TestPropertySource(properties = {
    "google.cloud.project=zamaz-test",
    "google.cloud.location=us-central1"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ExceptionHandlingValidationTest {

    @Autowired
    private UnifiedContextEngine contextEngine;
    
    @Autowired
    private MultiAgentOrchestrator agentOrchestrator;
    
    private TenantContext validTenant;
    private TenantContext invalidTenant;
    
    @BeforeEach
    void setUp() {
        validTenant = TenantContext.builder()
            .organizationId("zamaz-test")
            .projectId("exception-validation")
            .build();
            
        invalidTenant = TenantContext.builder()
            .organizationId("") // Invalid
            .build();
    }
    
    @Test
    @Order(1)
    @DisplayName("WorkflowExecutionException - Structured Error Info")
    void testWorkflowExecutionExceptionStructure() {
        WorkflowExecutionException exception = new WorkflowExecutionException(
            WorkflowExecutionException.WorkflowErrorCode.WORKFLOW_NOT_FOUND,
            "test-workflow-123",
            "exec-456",
            "node-789",
            validTenant,
            "Workflow definition could not be loaded from storage"
        );
        
        // Test basic exception properties
        assertEquals("WF001", exception.getErrorCode());
        assertEquals(ADKException.ErrorSeverity.ERROR, exception.getSeverity());
        assertEquals("WorkflowEngine", exception.getComponent());
        assertNotNull(exception.getContext());
        
        // Test workflow-specific properties
        assertEquals("test-workflow-123", exception.getWorkflowId());
        assertEquals("exec-456", exception.getExecutionId());
        assertEquals("node-789", exception.getNodeId());
        assertEquals(validTenant, exception.getTenantContext());
        
        // Test structured error info
        ADKException.ErrorInfo errorInfo = exception.getErrorInfo();
        assertNotNull(errorInfo);
        assertEquals("WorkflowExecutionException", errorInfo.getExceptionType());
        assertEquals("WF001", errorInfo.getErrorCode());
        assertTrue(errorInfo.getMessage().contains("Workflow not found"));
        assertTrue(errorInfo.getMessage().contains("test-workflow-123"));
        assertTrue(errorInfo.getMessage().contains("zamaz-test/exception-validation"));
        assertTrue(errorInfo.getTimestamp() > 0);
        
        // Test context object
        assertTrue(exception.getContext() instanceof WorkflowExecutionException.WorkflowContext);
        WorkflowExecutionException.WorkflowContext context = 
            (WorkflowExecutionException.WorkflowContext) exception.getContext();
        assertEquals("test-workflow-123", context.getWorkflowId());
        assertEquals("exec-456", context.getExecutionId());
        assertEquals("node-789", context.getNodeId());
        assertEquals("Workflow definition could not be loaded from storage", 
                    context.getAdditionalInfo());
    }
    
    @Test
    @Order(2)
    @DisplayName("AgentOrchestrationException - Agent Context")
    void testAgentOrchestrationExceptionStructure() {
        AgentOrchestrationException exception = new AgentOrchestrationException(
            AgentOrchestrationException.AgentErrorCode.AGENT_COMMUNICATION_FAILED,
            "data-processor-agent-001",
            "req-789",
            validTenant,
            "Message routing timeout after 30 seconds"
        );
        
        // Test agent-specific properties
        assertEquals("AG003", exception.getErrorCode());
        assertEquals("data-processor-agent-001", exception.getAgentId());
        assertEquals("req-789", exception.getRequestId());
        assertEquals(validTenant, exception.getTenantContext());
        
        // Test error info
        ADKException.ErrorInfo errorInfo = exception.getErrorInfo();
        assertEquals("AgentOrchestrationException", errorInfo.getExceptionType());
        assertEquals(ADKException.ErrorSeverity.ERROR, errorInfo.getSeverity());
        assertEquals("AgentOrchestrator", errorInfo.getComponent());
        
        // Test context
        assertTrue(exception.getContext() instanceof AgentOrchestrationException.AgentContext);
        AgentOrchestrationException.AgentContext context = 
            (AgentOrchestrationException.AgentContext) exception.getContext();
        assertEquals("data-processor-agent-001", context.getAgentId());
        assertEquals("req-789", context.getRequestId());
        assertEquals("Message routing timeout after 30 seconds", context.getAdditionalInfo());
    }
    
    @Test
    @Order(3)
    @DisplayName("TenantAccessException - Security Context")
    void testTenantAccessExceptionStructure() {
        TenantAccessException exception = new TenantAccessException(
            TenantAccessException.TenantErrorCode.ACCESS_DENIED,
            invalidTenant,
            "workflow-execution-service",
            "workflow:execute",
            "Tenant quota exceeded for workflow executions"
        );
        
        // Test tenant-specific properties
        assertEquals("TN002", exception.getErrorCode());
        assertEquals("workflow-execution-service", exception.getRequestedResource());
        assertEquals("workflow:execute", exception.getRequiredPermission());
        assertEquals(ADKException.ErrorSeverity.WARNING, exception.getSeverity());
        
        // Test context
        assertTrue(exception.getContext() instanceof TenantAccessException.TenantAccessContext);
        TenantAccessException.TenantAccessContext context = 
            (TenantAccessException.TenantAccessContext) exception.getContext();
        assertEquals("workflow-execution-service", context.getRequestedResource());
        assertEquals("workflow:execute", context.getRequiredPermission());
        assertEquals("Tenant quota exceeded for workflow executions", context.getAdditionalInfo());
    }
    
    @Test
    @Order(4)
    @DisplayName("ContextValidationException - Quality Scoring")
    void testContextValidationExceptionStructure() {
        ContextValidationException exception = new ContextValidationException(
            ContextValidationException.ContextErrorCode.CONTEXT_QUALITY_TOO_LOW,
            "ctx-12345",
            validTenant,
            "minimum_quality_threshold",
            0.23,
            "Context quality score below required threshold of 0.5"
        );
        
        // Test context validation properties
        assertEquals("CX003", exception.getErrorCode());
        assertEquals("ctx-12345", exception.getContextId());
        assertEquals("minimum_quality_threshold", exception.getValidationRule());
        assertEquals(0.23, exception.getQualityScore(), 0.001);
        assertEquals(ADKException.ErrorSeverity.WARNING, exception.getSeverity()); // Low quality = WARNING
        
        // Test context
        assertTrue(exception.getContext() instanceof ContextValidationException.ContextValidationContext);
        ContextValidationException.ContextValidationContext context = 
            (ContextValidationException.ContextValidationContext) exception.getContext();
        assertEquals("ctx-12345", context.getContextId());
        assertEquals(0.23, context.getQualityScore(), 0.001);
        assertEquals("minimum_quality_threshold", context.getValidationRule());
    }
    
    @Test
    @Order(5)
    @DisplayName("Exception Severity Determination")
    void testExceptionSeverityDetermination() {
        // Critical severity
        ContextValidationException criticalException = new ContextValidationException(
            ContextValidationException.ContextErrorCode.CONTEXT_SECURITY_VIOLATION,
            "ctx-security-test",
            validTenant,
            "security_scan",
            0.0,
            "Potential security breach detected"
        );
        assertEquals(ADKException.ErrorSeverity.CRITICAL, criticalException.getSeverity());
        
        // Error severity for very low quality
        ContextValidationException errorException = new ContextValidationException(
            ContextValidationException.ContextErrorCode.CONTEXT_QUALITY_TOO_LOW,
            "ctx-quality-test",
            validTenant,
            "quality_check",
            0.15, // Very low quality
            "Extremely low quality score"
        );
        assertEquals(ADKException.ErrorSeverity.ERROR, errorException.getSeverity());
        
        // Warning severity for moderately low quality  
        ContextValidationException warningException = new ContextValidationException(
            ContextValidationException.ContextErrorCode.CONTEXT_QUALITY_TOO_LOW,
            "ctx-quality-test-2",
            validTenant,
            "quality_check",
            0.45, // Moderate quality
            "Quality below threshold but not critical"
        );
        assertEquals(ADKException.ErrorSeverity.WARNING, warningException.getSeverity());
    }
    
    @Test
    @Order(6)
    @DisplayName("Exception Chain in Async Operations")
    void testExceptionChainInAsyncOperations() {
        // Test that custom exceptions are properly propagated through async chains
        UnifiedContextEngine.WorkflowExecutionRequest invalidRequest = 
            new UnifiedContextEngine.WorkflowExecutionRequest.Builder()
                .workflowId("non-existent-workflow-test")
                .tenantContext(validTenant)
                .build();
        
        CompletableFuture<UnifiedContextEngine.WorkflowExecutionResult> future = 
            contextEngine.executeWorkflow(invalidRequest);
        
        ExecutionException executionException = assertThrows(ExecutionException.class, () -> {
            future.get();
        });
        
        // Should have our custom exception in the chain
        Throwable cause = executionException.getCause();
        assertNotNull(cause);
        
        // Find our custom exception in the chain
        Throwable currentCause = cause;
        boolean foundCustomException = false;
        int maxDepth = 5; // Prevent infinite loops
        
        for (int i = 0; i < maxDepth && currentCause != null; i++) {
            if (currentCause instanceof WorkflowExecutionException) {
                foundCustomException = true;
                WorkflowExecutionException workflowException = (WorkflowExecutionException) currentCause;
                
                // Verify exception properties
                assertNotNull(workflowException.getErrorCode());
                assertEquals("WorkflowEngine", workflowException.getComponent());
                assertNotNull(workflowException.getErrorInfo());
                break;
            }
            currentCause = currentCause.getCause();
        }
        
        assertTrue(foundCustomException, "Should find WorkflowExecutionException in exception chain");
    }
    
    @Test
    @Order(7)
    @DisplayName("Error Info Serialization")
    void testErrorInfoSerialization() {
        WorkflowExecutionException exception = new WorkflowExecutionException(
            WorkflowExecutionException.WorkflowErrorCode.NODE_EXECUTION_FAILED,
            "workflow-123",
            "execution-456",
            "node-789",
            validTenant,
            "Node processing timeout"
        );
        
        ADKException.ErrorInfo errorInfo = exception.getErrorInfo();
        String errorInfoString = errorInfo.toString();
        
        // Verify toString contains all important information
        assertTrue(errorInfoString.contains("WorkflowExecutionException"));
        assertTrue(errorInfoString.contains("WF003"));
        assertTrue(errorInfoString.contains("Node execution failed"));
        assertTrue(errorInfoString.contains("ERROR"));
        assertTrue(errorInfoString.contains("WorkflowEngine"));
        
        // Test that error info is consistent
        ADKException.ErrorInfo errorInfo2 = exception.getErrorInfo();
        assertEquals(errorInfo.getErrorCode(), errorInfo2.getErrorCode());
        assertEquals(errorInfo.getExceptionType(), errorInfo2.getExceptionType());
        assertEquals(errorInfo.getSeverity(), errorInfo2.getSeverity());
        assertEquals(errorInfo.getComponent(), errorInfo2.getComponent());
        assertEquals(errorInfo.getMessage(), errorInfo2.getMessage());
    }
    
    @Test
    @Order(8)
    @DisplayName("Exception Context Preservation")
    void testExceptionContextPreservation() {
        // Test that exception context is preserved through various operations
        String originalWorkflowId = "context-preservation-test";
        String originalExecutionId = "exec-" + System.currentTimeMillis();
        String originalNodeId = "test-node";
        String originalMessage = "Context preservation validation";
        
        WorkflowExecutionException originalException = new WorkflowExecutionException(
            WorkflowExecutionException.WorkflowErrorCode.CONDITION_EVALUATION_FAILED,
            originalWorkflowId,
            originalExecutionId,
            originalNodeId,
            validTenant,
            originalMessage
        );
        
        // Wrap in another exception
        RuntimeException wrappedException = new RuntimeException(
            "Wrapper exception", originalException
        );
        
        // Extract and verify context is preserved
        Throwable cause = wrappedException.getCause();
        assertTrue(cause instanceof WorkflowExecutionException);
        
        WorkflowExecutionException extractedException = (WorkflowExecutionException) cause;
        assertEquals(originalWorkflowId, extractedException.getWorkflowId());
        assertEquals(originalExecutionId, extractedException.getExecutionId());
        assertEquals(originalNodeId, extractedException.getNodeId());
        assertEquals(validTenant, extractedException.getTenantContext());
        assertTrue(extractedException.getMessage().contains(originalMessage));
        
        // Verify error info is still accessible
        ADKException.ErrorInfo errorInfo = extractedException.getErrorInfo();
        assertNotNull(errorInfo);
        assertEquals("WF004", errorInfo.getErrorCode());
        assertEquals("WorkflowExecutionException", errorInfo.getExceptionType());
    }
    
    @Test
    @Order(9)
    @DisplayName("Multiple Exception Types in Single Operation")
    void testMultipleExceptionTypesHandling() {
        // Create a scenario that could trigger multiple types of exceptions
        Map<Class<? extends Exception>, Integer> exceptionCounts = new HashMap<>();
        
        // Test invalid tenant access
        try {
            throw new TenantAccessException(
                TenantAccessException.TenantErrorCode.TENANT_NOT_FOUND,
                null, // null tenant
                "test-resource",
                "test-permission",
                "Tenant validation failed"
            );
        } catch (TenantAccessException e) {
            exceptionCounts.put(TenantAccessException.class, 
                exceptionCounts.getOrDefault(TenantAccessException.class, 0) + 1);
            
            // Verify exception structure
            assertEquals("TN001", e.getErrorCode());
            assertNull(e.getTenantContext());
        }
        
        // Test workflow execution failure
        try {
            throw new WorkflowExecutionException(
                WorkflowExecutionException.WorkflowErrorCode.WORKFLOW_TIMEOUT,
                "timeout-test",
                "exec-timeout",
                validTenant,
                new RuntimeException("Underlying timeout")
            );
        } catch (WorkflowExecutionException e) {
            exceptionCounts.put(WorkflowExecutionException.class,
                exceptionCounts.getOrDefault(WorkflowExecutionException.class, 0) + 1);
            
            // Verify exception structure
            assertEquals("WF005", e.getErrorCode());
            assertNotNull(e.getCause());
        }
        
        // Test context validation failure
        try {
            throw new ContextValidationException(
                ContextValidationException.ContextErrorCode.CONTEXT_CORRUPTION_DETECTED,
                "corrupt-ctx",
                validTenant,
                new RuntimeException("Data corruption detected")
            );
        } catch (ContextValidationException e) {
            exceptionCounts.put(ContextValidationException.class,
                exceptionCounts.getOrDefault(ContextValidationException.class, 0) + 1);
            
            // Verify exception structure
            assertEquals("CX005", e.getErrorCode());
            assertEquals(ADKException.ErrorSeverity.CRITICAL, e.getSeverity());
        }
        
        // Verify all exception types were handled
        assertEquals(3, exceptionCounts.size());
        assertEquals(1, (int) exceptionCounts.get(TenantAccessException.class));
        assertEquals(1, (int) exceptionCounts.get(WorkflowExecutionException.class));
        assertEquals(1, (int) exceptionCounts.get(ContextValidationException.class));
    }
    
    @AfterEach
    void cleanup() {
        // Any test-specific cleanup
        System.gc(); // Suggest cleanup after exception tests
    }
}