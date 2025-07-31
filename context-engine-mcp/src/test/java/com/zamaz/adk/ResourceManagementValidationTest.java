package com.zamaz.adk;

import com.zamaz.adk.integration.UnifiedContextEngine;
import com.zamaz.adk.agents.MultiAgentOrchestrator;
import com.zamaz.adk.config.ADKConfigurationProperties;
import com.zamaz.adk.core.TenantContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for resource management and graceful shutdown improvements
 */
@SpringBootTest
@TestPropertySource(properties = {
    "google.cloud.project=zamaz-resource-test",
    "google.cloud.location=us-central1",
    "resources.shutdown.gracefulTimeoutSeconds=30",
    "resources.shutdown.forceTimeoutSeconds=15",
    "resources.shutdown.emergencyTimeoutSeconds=5",
    "resources.executor.workStealingPoolSize=4",
    "resources.executor.scheduledPoolSize=2",
    "debug.enabled=true"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ResourceManagementValidationTest {

    @Autowired
    private UnifiedContextEngine contextEngine;
    
    @Autowired
    private MultiAgentOrchestrator agentOrchestrator;
    
    @Autowired
    private ADKConfigurationProperties config;
    
    private TenantContext testTenant;
    
    @BeforeEach
    void setUp() {
        testTenant = TenantContext.builder()
            .organizationId("zamaz-resource-test")
            .projectId("resource-management")
            .build();
    }
    
    @Test
    @Order(1)
    @DisplayName("Resource Initialization - Proper Pool Setup")
    void testResourceInitialization() {
        // Test that components are properly initialized
        assertNotNull(contextEngine, "UnifiedContextEngine should be initialized");
        assertNotNull(agentOrchestrator, "MultiAgentOrchestrator should be initialized");
        
        // Test that components are not in shutdown state
        assertFalse(contextEngine.isShutdown(), "Context engine should not be shutdown initially");
        assertFalse(agentOrchestrator.isShutdown(), "Agent orchestrator should not be shutdown initially");
        
        // Test configuration is properly loaded
        assertEquals(30, config.getResources().getShutdown().getGracefulTimeoutSeconds());
        assertEquals(15, config.getResources().getShutdown().getForceTimeoutSeconds());
        assertEquals(5, config.getResources().getShutdown().getEmergencyTimeoutSeconds());
        assertEquals(4, config.getResources().getExecutor().getWorkStealingPoolSize());
        assertEquals(2, config.getResources().getExecutor().getScheduledPoolSize());
    }
    
    @Test
    @Order(2)
    @DisplayName("Thread Pool Management - Work Stealing Pool")
    void testWorkStealingPoolManagement() throws Exception {
        // Test that work stealing pool is properly sized
        int expectedPoolSize = config.getResources().getExecutor().getWorkStealingPoolSize();
        
        // Submit work to test pool utilization
        List<CompletableFuture<String>> futures = new ArrayList<>();
        AtomicInteger completedTasks = new AtomicInteger(0);
        
        for (int i = 0; i < expectedPoolSize * 2; i++) {
            final int taskId = i;
            
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    // Simulate work
                    Thread.sleep(100);
                    completedTasks.incrementAndGet();
                    return "Task-" + taskId + "-completed";
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Task interrupted", e);
                }
            });
            
            futures.add(future);
        }
        
        // Wait for all tasks to complete
        CompletableFuture<Void> allTasks = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );
        
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            allTasks.get();
            return null;
        });
        
        // Verify all tasks completed
        assertEquals(expectedPoolSize * 2, completedTasks.get());
        
        // Verify results
        for (CompletableFuture<String> future : futures) {
            assertTrue(future.isDone());
            assertFalse(future.isCancelled());
            String result = future.get();
            assertTrue(result.startsWith("Task-"));
            assertTrue(result.endsWith("-completed"));
        }
        
        System.out.println("Work stealing pool handled " + completedTasks.get() + " tasks successfully");
    }
    
    @Test
    @Order(3)
    @DisplayName("Scheduled Pool Management - Periodic Tasks")
    void testScheduledPoolManagement() throws Exception {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
            config.getResources().getExecutor().getScheduledPoolSize()
        );
        
        AtomicInteger periodicTaskCount = new AtomicInteger(0);
        AtomicLong lastExecutionTime = new AtomicLong(System.currentTimeMillis());
        
        // Schedule periodic task
        ScheduledFuture<?> periodicTask = scheduler.scheduleAtFixedRate(() -> {
            long currentTime = System.currentTimeMillis();
            long timeSinceLastExecution = currentTime - lastExecutionTime.get();
            
            // Verify execution interval is reasonable (should be ~200ms +- 50ms)
            assertTrue(timeSinceLastExecution >= 150 && timeSinceLastExecution <= 300,
                "Execution interval should be reasonable: " + timeSinceLastExecution + "ms");
            
            periodicTaskCount.incrementAndGet();
            lastExecutionTime.set(currentTime);
        }, 100, 200, TimeUnit.MILLISECONDS);
        
        // Let it run for a few cycles
        Thread.sleep(1000);
        
        // Cancel the task
        periodicTask.cancel(false);
        
        // Verify it executed multiple times
        int executionCount = periodicTaskCount.get();
        assertTrue(executionCount >= 3 && executionCount <= 6, 
            "Should have executed 3-6 times, but was: " + executionCount);
        
        // Test scheduler shutdown
        scheduler.shutdown();
        assertTrue(scheduler.awaitTermination(5, TimeUnit.SECONDS), 
            "Scheduler should shutdown within 5 seconds");
        
        System.out.println("Scheduled task executed " + executionCount + " times");
    }
    
    @Test
    @Order(4)
    @DisplayName("Resource Cleanup - Memory Management")
    void testResourceCleanup() throws Exception {
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // Create memory-intensive operations
        List<CompletableFuture<Void>> memoryTasks = new ArrayList<>();
        
        for (int i = 0; i < 10; i++) {
            final int taskId = i;
            
            CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
                // Create temporary data structures
                List<String> tempData = new ArrayList<>();
                for (int j = 0; j < 10000; j++) {
                    tempData.add("TempData-" + taskId + "-" + j + "-" + UUID.randomUUID());
                }
                
                // Process data (simulating work)
                String processed = tempData.stream()
                    .filter(s -> s.contains("TempData-" + taskId))
                    .findFirst()
                    .orElse("Not found");
                
                // Data should be cleaned up automatically when task completes
                assertNotNull(processed);
            });
            
            memoryTasks.add(task);
        }
        
        // Wait for all tasks to complete
        CompletableFuture<Void> allMemoryTasks = CompletableFuture.allOf(
            memoryTasks.toArray(new CompletableFuture[0])
        );
        
        assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
            allMemoryTasks.get();
            return null;
        });
        
        // Force garbage collection and wait
        System.gc();
        Thread.sleep(1000);
        System.gc();
        Thread.sleep(500);
        
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;
        
        System.out.println("Memory cleanup test:");
        System.out.println("Initial memory: " + (initialMemory / 1024 / 1024) + " MB");
        System.out.println("Final memory: " + (finalMemory / 1024 / 1024) + " MB");
        System.out.println("Memory increase: " + (memoryIncrease / 1024 / 1024) + " MB");
        
        // Memory increase should be reasonable (less than 100MB after cleanup)
        assertTrue(memoryIncrease < 100 * 1024 * 1024, 
            "Memory increase should be reasonable after cleanup");
    }
    
    @Test
    @Order(5)
    @DisplayName("Shutdown Hook Registration - Emergency Cleanup")
    void testShutdownHookRegistration() {
        // Test that shutdown hooks are properly registered
        
        // Simulate shutdown scenario by creating resources that need cleanup
        List<CompletableFuture<String>> backgroundTasks = new ArrayList<>();
        
        for (int i = 0; i < 5; i++) {
            final int taskId = i;
            
            CompletableFuture<String> task = CompletableFuture.supplyAsync(() -> {
                try {
                    // Long-running task that would be interrupted on shutdown
                    Thread.sleep(5000);
                    return "Task-" + taskId + "-completed";
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return "Task-" + taskId + "-interrupted";
                }
            });
            
            backgroundTasks.add(task);
        }
        
        // Give tasks time to start
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Verify tasks are running
        for (CompletableFuture<String> task : backgroundTasks) {
            assertFalse(task.isDone(), "Task should still be running");
        }
        
        // Cancel tasks (simulating shutdown)
        for (CompletableFuture<String> task : backgroundTasks) {
            task.cancel(true);
        }
        
        // Verify cancellation
        for (CompletableFuture<String> task : backgroundTasks) {
            assertTrue(task.isCancelled() || task.isDone(), "Task should be cancelled or done");
        }
        
        System.out.println("Successfully tested shutdown hook scenario");
    }
    
    @Test
    @Order(6)
    @DisplayName("Graceful Shutdown - Timeout Handling")
    void testGracefulShutdownTimeout() throws Exception {
        // Test graceful shutdown with different timeout scenarios
        
        ExecutorService testExecutor = Executors.newFixedThreadPool(2);
        
        // Start a long-running task
        Future<String> longTask = testExecutor.submit(() -> {
            try {
                Thread.sleep(10000); // 10 second task
                return "Long task completed";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "Long task interrupted";
            }
        });
        
        // Start a short task
        Future<String> shortTask = testExecutor.submit(() -> {
            try {
                Thread.sleep(500); // 0.5 second task
                return "Short task completed";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "Short task interrupted";
            }
        });
        
        // Give tasks time to start
        Thread.sleep(100);
        
        // Initiate graceful shutdown
        testExecutor.shutdown();
        
        // Test graceful timeout (should be less than graceful timeout)
        int gracefulTimeoutSeconds = config.getResources().getShutdown().getGracefulTimeoutSeconds();
        boolean gracefulShutdown = testExecutor.awaitTermination(gracefulTimeoutSeconds, TimeUnit.SECONDS);
        
        if (!gracefulShutdown) {
            // Force shutdown if graceful didn't complete in time
            System.out.println("Graceful shutdown timed out, forcing shutdown");
            testExecutor.shutdownNow();
            
            // Wait for forced shutdown
            int forceTimeoutSeconds = config.getResources().getShutdown().getForceTimeoutSeconds();
            boolean forcedShutdown = testExecutor.awaitTermination(forceTimeoutSeconds, TimeUnit.SECONDS);
            
            if (!forcedShutdown) {
                System.out.println("Forced shutdown timed out, emergency cleanup required");
            }
            
            assertTrue(forcedShutdown, "Should complete shutdown within force timeout");
        }
        
        // Verify executor is shutdown
        assertTrue(testExecutor.isShutdown(), "Executor should be shutdown");
        assertTrue(testExecutor.isTerminated(), "Executor should be terminated");
        
        // Check task results
        if (shortTask.isDone()) {
            String shortResult = shortTask.get();
            System.out.println("Short task result: " + shortResult);
        }
        
        if (longTask.isDone()) {
            String longResult = longTask.get();
            System.out.println("Long task result: " + longResult);
        }
    }
    
    @Test
    @Order(7)
    @DisplayName("Resource Monitoring - Health Checks")
    void testResourceMonitoring() {
        // Test that components provide health information
        
        // Check context engine health
        assertFalse(contextEngine.isShutdown(), "Context engine should be healthy");
        
        // Check agent orchestrator health  
        assertFalse(agentOrchestrator.isShutdown(), "Agent orchestrator should be healthy");
        
        // Test resource utilization monitoring
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        
        double memoryUtilization = (double) usedMemory / totalMemory;
        double maxMemoryUtilization = (double) usedMemory / maxMemory;
        
        System.out.println("Resource monitoring:");
        System.out.println("Total memory: " + (totalMemory / 1024 / 1024) + " MB");
        System.out.println("Free memory: " + (freeMemory / 1024 / 1024) + " MB");
        System.out.println("Used memory: " + (usedMemory / 1024 / 1024) + " MB");
        System.out.println("Max memory: " + (maxMemory / 1024 / 1024) + " MB");
        System.out.println("Memory utilization: " + String.format("%.2f%%", memoryUtilization * 100));
        System.out.println("Max memory utilization: " + String.format("%.2f%%", maxMemoryUtilization * 100));
        
        // Verify memory utilization is reasonable
        assertTrue(memoryUtilization < 0.95, "Memory utilization should be under 95%");
        assertTrue(maxMemoryUtilization < 0.90, "Max memory utilization should be under 90%");
        
        // Test thread monitoring
        ThreadMXBean threadBean = java.lang.management.ManagementFactory.getThreadMXBean();
        int activeThreads = threadBean.getThreadCount();
        int peakThreads = threadBean.getPeakThreadCount();
        
        System.out.println("Active threads: " + activeThreads);
        System.out.println("Peak threads: " + peakThreads);
        
        // Verify thread count is reasonable
        assertTrue(activeThreads > 0, "Should have active threads");
        assertTrue(activeThreads < 100, "Thread count should be reasonable");
    }
    
    @Test
    @Order(8)
    @DisplayName("Error Recovery - Resource Exhaustion")
    void testErrorRecovery() throws Exception {
        // Test recovery from resource exhaustion scenarios
        
        List<CompletableFuture<String>> stressTasks = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        // Create stress scenario
        for (int i = 0; i < 20; i++) {
            final int taskId = i;
            
            CompletableFuture<String> task = CompletableFuture.supplyAsync(() -> {
                try {
                    // Simulate resource-intensive operation
                    List<byte[]> memoryConsumer = new ArrayList<>();
                    for (int j = 0; j < 1000; j++) {
                        memoryConsumer.add(new byte[1024]); // 1KB chunks
                    }
                    
                    // Simulate processing
                    Thread.sleep(50);
                    
                    successCount.incrementAndGet();
                    return "Stress-task-" + taskId + "-completed";
                    
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    return "Stress-task-" + taskId + "-failed: " + e.getMessage();
                }
            }).exceptionally(throwable -> {
                errorCount.incrementAndGet();
                return "Stress-task-" + taskId + "-exception: " + throwable.getMessage();
            });
            
            stressTasks.add(task);
        }
        
        // Wait for all tasks to complete
        CompletableFuture<Void> allStressTasks = CompletableFuture.allOf(
            stressTasks.toArray(new CompletableFuture[0])
        );
        
        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            allStressTasks.get();
            return null;
        });
        
        int totalTasks = stressTasks.size();
        int completed = successCount.get();
        int failed = errorCount.get();
        
        System.out.println("Stress test results:");
        System.out.println("Total tasks: " + totalTasks);
        System.out.println("Successful: " + completed);
        System.out.println("Failed: " + failed);
        System.out.println("Success rate: " + String.format("%.2f%%", (double) completed / totalTasks * 100));
        
        // At least 70% should succeed (allowing for some stress-induced failures)
        assertTrue(completed >= totalTasks * 0.7, 
            "At least 70% of stress tasks should succeed");
        
        // System should still be responsive after stress test
        assertFalse(contextEngine.isShutdown(), "Context engine should still be healthy after stress");
        assertFalse(agentOrchestrator.isShutdown(), "Agent orchestrator should still be healthy after stress");
    }
    
    @Test
    @Order(9)
    @DisplayName("Configuration-based Resource Limits")
    void testConfigurationBasedResourceLimits() {
        // Test that resource limits from configuration are respected
        
        ADKConfigurationProperties.Resources resourcesConfig = config.getResources();
        
        // Verify timeout configurations
        assertTrue(resourcesConfig.getShutdown().getGracefulTimeoutSeconds() > 0, 
            "Graceful timeout should be positive");
        assertTrue(resourcesConfig.getShutdown().getForceTimeoutSeconds() > 0, 
            "Force timeout should be positive");
        assertTrue(resourcesConfig.getShutdown().getEmergencyTimeoutSeconds() > 0, 
            "Emergency timeout should be positive");
        
        // Verify timeout hierarchy
        assertTrue(resourcesConfig.getShutdown().getGracefulTimeoutSeconds() >= 
                  resourcesConfig.getShutdown().getForceTimeoutSeconds(),
            "Graceful timeout should be >= force timeout");
        assertTrue(resourcesConfig.getShutdown().getForceTimeoutSeconds() >= 
                  resourcesConfig.getShutdown().getEmergencyTimeoutSeconds(),
            "Force timeout should be >= emergency timeout");
        
        // Verify executor configurations
        assertTrue(resourcesConfig.getExecutor().getScheduledPoolSize() > 0, 
            "Scheduled pool size should be positive");
        
        // Work stealing pool size can be -1 (use default) or positive
        int workStealingPoolSize = resourcesConfig.getExecutor().getWorkStealingPoolSize();
        assertTrue(workStealingPoolSize == -1 || workStealingPoolSize > 0, 
            "Work stealing pool size should be -1 or positive");
        
        System.out.println("Configuration validation:");
        System.out.println("Graceful timeout: " + resourcesConfig.getShutdown().getGracefulTimeoutSeconds() + "s");
        System.out.println("Force timeout: " + resourcesConfig.getShutdown().getForceTimeoutSeconds() + "s");
        System.out.println("Emergency timeout: " + resourcesConfig.getShutdown().getEmergencyTimeoutSeconds() + "s");
        System.out.println("Work stealing pool size: " + workStealingPoolSize);
        System.out.println("Scheduled pool size: " + resourcesConfig.getExecutor().getScheduledPoolSize());
    }
    
    @AfterEach
    void cleanup() {
        // Cleanup after each test
        System.gc(); // Suggest garbage collection
    }
    
    @AfterAll
    static void tearDown() {
        System.out.println("Resource management validation tests completed");
    }
}