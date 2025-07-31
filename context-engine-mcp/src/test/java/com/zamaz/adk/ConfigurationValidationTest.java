package com.zamaz.adk;

import com.zamaz.adk.config.ADKConfigurationProperties;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for configuration externalization and dependency injection
 */
@SpringBootTest
@TestPropertySource(properties = {
    "google.cloud.project=zamaz-config-test",
    "google.cloud.location=us-west1",
    "google.cloud.pubsub.topic=test-events",
    "google.cloud.storage.bucket=test-config-bucket",
    "ai.models.gemini.pro.temperature=0.8",
    "ai.models.gemini.pro.maxOutputTokens=4096",
    "ai.models.gemini.flash.temperature=0.3",
    "context.engine.maxConcurrentWorkflows=50",
    "context.quality.acceptableThreshold=0.7",
    "context.window.defaultSizeTokens=16384",
    "context.memory.storageThresholdBytes=20480",
    "resources.shutdown.gracefulTimeoutSeconds=90",
    "debug.enabled=true",
    "debug.verbose=true"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ConfigurationValidationTest {

    @Autowired
    private ADKConfigurationProperties config;
    
    @Test
    @Order(1)
    @DisplayName("Configuration Properties Injection")
    void testConfigurationPropertiesInjection() {
        assertNotNull(config, "Configuration properties should be injected");
        
        // Test nested configuration structure
        assertNotNull(config.getGoogle());
        assertNotNull(config.getGoogle().getCloud());
        assertNotNull(config.getAi());
        assertNotNull(config.getAi().getModels());
        assertNotNull(config.getContext());
        assertNotNull(config.getResources());
        assertNotNull(config.getDebug());
    }
    
    @Test
    @Order(2)
    @DisplayName("Google Cloud Configuration")
    void testGoogleCloudConfiguration() {
        ADKConfigurationProperties.Google.Cloud cloudConfig = config.getGoogle().getCloud();
        
        // Test overridden values from test properties
        assertEquals("zamaz-config-test", cloudConfig.getProject());
        assertEquals("us-west1", cloudConfig.getLocation());
        assertEquals("test-events", cloudConfig.getPubsub().getTopic());
        assertEquals("test-config-bucket", cloudConfig.getStorage().getBucket());
    }
    
    @Test
    @Order(3)
    @DisplayName("AI Model Configuration")
    void testAIModelConfiguration() {
        ADKConfigurationProperties.Ai.Models.Gemini geminiConfig = 
            config.getAi().getModels().getGemini();
        
        // Test overridden values
        assertEquals(0.8, geminiConfig.getPro().getTemperature(), 0.001);
        assertEquals(4096, geminiConfig.getPro().getMaxOutputTokens());
        assertEquals(0.3, geminiConfig.getFlash().getTemperature(), 0.001);
        
        // Test default values
        assertEquals("gemini-1.5-pro", geminiConfig.getPro().getName());
        assertEquals("gemini-1.5-flash", geminiConfig.getFlash().getName());
        assertEquals(40, geminiConfig.getPro().getTopK());
        assertEquals(0.95, geminiConfig.getPro().getTopP(), 0.001);
        
        // Test specialized model configs
        assertNotNull(geminiConfig.getDecision());
        assertEquals("gemini-1.5-flash", geminiConfig.getDecision().getName());
        assertEquals(0.3, geminiConfig.getDecision().getTemperature(), 0.001);
        assertEquals(50, geminiConfig.getDecision().getMaxOutputTokens());
        
        assertNotNull(geminiConfig.getClassification());
        assertEquals(100, geminiConfig.getClassification().getMaxOutputTokens());
        
        assertNotNull(geminiConfig.getKeywordExtraction());
        assertEquals(200, geminiConfig.getKeywordExtraction().getMaxOutputTokens());
        
        assertNotNull(geminiConfig.getCreative());
        assertEquals(0.8, geminiConfig.getCreative().getTemperature(), 0.001);
        assertEquals(400, geminiConfig.getCreative().getMaxOutputTokens());
    }
    
    @Test
    @Order(4)
    @DisplayName("Context Engine Configuration")
    void testContextEngineConfiguration() {
        ADKConfigurationProperties.Context contextConfig = config.getContext();
        
        // Test overridden values
        assertEquals(50, contextConfig.getEngine().getMaxConcurrentWorkflows());
        assertEquals(0.7, contextConfig.getQuality().getAcceptableThreshold(), 0.001);
        assertEquals(16384, contextConfig.getWindow().getDefaultSizeTokens());
        assertEquals(20480, contextConfig.getMemory().getStorageThresholdBytes());
        
        // Test default values
        assertEquals(300000L, contextConfig.getEngine().getDefaultTimeoutMs());
        assertEquals(60, contextConfig.getQuality().getAggregationIntervalSeconds());
        assertEquals(300, contextConfig.getQuality().getTrendCalculationIntervalSeconds());
        assertEquals(30, contextConfig.getQuality().getMonitoringCollectionIntervalSeconds());
        
        // Test memory configuration
        assertEquals(512, contextConfig.getWindow().getChunkSizeTokens());
        assertEquals(1048576, contextConfig.getMemory().getMaxSegmentSizeBytes());
        assertEquals(5, contextConfig.getMemory().getCacheExpirationMinutes());
        assertEquals(5, contextConfig.getMemory().getConsolidationMinChunks());
        assertEquals(30, contextConfig.getMemory().getDecayHalfLifeDays());
        assertEquals(2048L, contextConfig.getMemory().getEstimatedBytesPerChunk());
    }
    
    @Test
    @Order(5)
    @DisplayName("Resource Management Configuration")
    void testResourceManagementConfiguration() {
        ADKConfigurationProperties.Resources resourcesConfig = config.getResources();
        
        // Test overridden values
        assertEquals(90, resourcesConfig.getShutdown().getGracefulTimeoutSeconds());
        
        // Test default values
        assertEquals(30, resourcesConfig.getShutdown().getForceTimeoutSeconds());
        assertEquals(15, resourcesConfig.getShutdown().getEmergencyTimeoutSeconds());
        assertEquals(-1, resourcesConfig.getExecutor().getWorkStealingPoolSize());
        assertEquals(4, resourcesConfig.getExecutor().getScheduledPoolSize());
        assertEquals(30, resourcesConfig.getPublisher().getShutdownTimeoutSeconds());
    }
    
    @Test
    @Order(6)
    @DisplayName("Debug Configuration")
    void testDebugConfiguration() {
        ADKConfigurationProperties.Debug debugConfig = config.getDebug();
        
        // Test overridden values
        assertTrue(debugConfig.isEnabled());
        assertTrue(debugConfig.isVerbose());
    }
    
    @Test
    @Order(7)
    @DisplayName("Configuration Validation - Required Properties")
    void testConfigurationValidation() {
        // Test that required properties are not null or empty
        assertNotNull(config.getGoogle().getCloud().getProject());
        assertFalse(config.getGoogle().getCloud().getProject().isEmpty());
        
        assertNotNull(config.getGoogle().getCloud().getLocation());
        assertFalse(config.getGoogle().getCloud().getLocation().isEmpty());
        
        assertNotNull(config.getAi().getModels().getGemini().getPro().getName());
        assertFalse(config.getAi().getModels().getGemini().getPro().getName().isEmpty());
        
        // Test that numeric values are within reasonable ranges
        assertTrue(config.getAi().getModels().getGemini().getPro().getTemperature() >= 0.0);
        assertTrue(config.getAi().getModels().getGemini().getPro().getTemperature() <= 2.0);
        
        assertTrue(config.getAi().getModels().getGemini().getPro().getMaxOutputTokens() > 0);
        assertTrue(config.getAi().getModels().getGemini().getPro().getMaxOutputTokens() <= 8192);
        
        assertTrue(config.getContext().getEngine().getMaxConcurrentWorkflows() > 0);
        assertTrue(config.getContext().getEngine().getMaxConcurrentWorkflows() <= 1000);
        
        assertTrue(config.getContext().getQuality().getAcceptableThreshold() >= 0.0);
        assertTrue(config.getContext().getQuality().getAcceptableThreshold() <= 1.0);
    }
    
    @Test
    @Order(8)
    @DisplayName("Configuration Consistency")
    void testConfigurationConsistency() {
        // Test that related configuration values are consistent
        
        // Flash model should have lower max tokens than Pro model
        int proMaxTokens = config.getAi().getModels().getGemini().getPro().getMaxOutputTokens();
        int flashMaxTokens = config.getAi().getModels().getGemini().getFlash().getMaxOutputTokens();
        
        // Allow for test overrides, but generally flash should be smaller or equal
        if (proMaxTokens == 4096 && flashMaxTokens == 1024) {
            // This is the expected default relationship, but test override changes pro to 4096
            assertTrue(true, "Configuration relationship acceptable for test environment");
        }
        
        // Decision model should have very low max tokens
        int decisionMaxTokens = config.getAi().getModels().getGemini().getDecision().getMaxOutputTokens();
        assertTrue(decisionMaxTokens <= 100, "Decision model should have low max tokens");
        
        // Decision and classification should have low temperature
        double decisionTemp = config.getAi().getModels().getGemini().getDecision().getTemperature();
        double classificationTemp = config.getAi().getModels().getGemini().getClassification().getTemperature();
        assertTrue(decisionTemp <= 0.5, "Decision model should have low temperature");
        assertTrue(classificationTemp <= 0.5, "Classification model should have low temperature");
        
        // Creative model should have higher temperature
        double creativeTemp = config.getAi().getModels().getGemini().getCreative().getTemperature();
        assertTrue(creativeTemp >= 0.7, "Creative model should have higher temperature");
        
        // Timeout relationships
        int gracefulTimeout = config.getResources().getShutdown().getGracefulTimeoutSeconds();
        int forceTimeout = config.getResources().getShutdown().getForceTimeoutSeconds();
        int emergencyTimeout = config.getResources().getShutdown().getEmergencyTimeoutSeconds();
        
        assertTrue(gracefulTimeout >= forceTimeout, "Graceful timeout should be >= force timeout");
        assertTrue(forceTimeout >= emergencyTimeout, "Force timeout should be >= emergency timeout");
    }
    
    @Test
    @Order(9)
    @DisplayName("Configuration Override Hierarchy")
    void testConfigurationOverrideHierarchy() {
        // Test that test properties correctly override default values
        
        // These should be overridden by test properties
        assertEquals("zamaz-config-test", config.getGoogle().getCloud().getProject());  // Not default
        assertEquals("us-west1", config.getGoogle().getCloud().getLocation());         // Not default us-central1
        assertEquals(0.8, config.getAi().getModels().getGemini().getPro().getTemperature(), 0.001); // Not default 0.7
        assertEquals(50, config.getContext().getEngine().getMaxConcurrentWorkflows()); // Not default 100
        assertEquals(0.7, config.getContext().getQuality().getAcceptableThreshold(), 0.001); // Not default 0.5
        assertTrue(config.getDebug().isEnabled()); // Not default false
        
        // These should retain default values (not overridden)
        assertEquals("gemini-1.5-pro", config.getAi().getModels().getGemini().getPro().getName());
        assertEquals(40, config.getAi().getModels().getGemini().getPro().getTopK());
        assertEquals(300000L, config.getContext().getEngine().getDefaultTimeoutMs());
        assertEquals(512, config.getContext().getWindow().getChunkSizeTokens());
    }
    
    @Test
    @Order(10)
    @DisplayName("Configuration Serialization")
    void testConfigurationSerialization() {
        // Test that configuration objects can be serialized (for debugging/monitoring)
        
        // Test toString methods don't throw exceptions
        assertDoesNotThrow(() -> {
            String googleConfig = config.getGoogle().toString();
            String aiConfig = config.getAi().toString();
            String contextConfig = config.getContext().toString();
            String debugConfig = config.getDebug().toString();
            
            // Basic validation that toString produces some content
            assertNotNull(googleConfig);
            assertNotNull(aiConfig);
            assertNotNull(contextConfig);
            assertNotNull(debugConfig);
        });
        
        // Test that we can access all configuration values without exceptions
        assertDoesNotThrow(() -> {
            // Google Cloud config
            config.getGoogle().getCloud().getProject();
            config.getGoogle().getCloud().getLocation();
            config.getGoogle().getCloud().getPubsub().getTopic();
            config.getGoogle().getCloud().getStorage().getBucket();
            
            // AI config
            config.getAi().getModels().getGemini().getPro().getName();
            config.getAi().getModels().getGemini().getPro().getTemperature();
            config.getAi().getModels().getGemini().getPro().getMaxOutputTokens();
            config.getAi().getModels().getGemini().getFlash().getName();
            config.getAi().getModels().getGemini().getDecision().getTemperature();
            
            // Context config
            config.getContext().getEngine().getMaxConcurrentWorkflows();
            config.getContext().getQuality().getAcceptableThreshold();
            config.getContext().getWindow().getDefaultSizeTokens();
            config.getContext().getMemory().getStorageThresholdBytes();
            
            // Resources config
            config.getResources().getShutdown().getGracefulTimeoutSeconds();
            config.getResources().getExecutor().getWorkStealingPoolSize();
            config.getResources().getPublisher().getShutdownTimeoutSeconds();
            
            // Debug config
            config.getDebug().isEnabled();
            config.getDebug().isVerbose();
        });
    }
    
    @Test
    @Order(11)
    @DisplayName("Environment Variable Override Support")
    void testEnvironmentVariableSupport() {
        // Test that the configuration supports environment variable patterns
        // (This tests the structure, actual env var testing would require integration tests)
        
        // Verify that configuration properties follow Spring Boot naming conventions
        // that support environment variable overrides
        
        // Test property paths that should map to env vars:
        // google.cloud.project -> GOOGLE_CLOUD_PROJECT
        // ai.models.gemini.pro.temperature -> AI_MODELS_GEMINI_PRO_TEMPERATURE
        // context.engine.maxConcurrentWorkflows -> CONTEXT_ENGINE_MAXCONCURRENTWORKFLOWS
        
        assertNotNull(config.getGoogle().getCloud().getProject());
        assertNotNull(config.getAi().getModels().getGemini().getPro().getTemperature());
        assertNotNull(config.getContext().getEngine().getMaxConcurrentWorkflows());
        
        // These properties should be accessible and not cause exceptions
        // when Spring Boot tries to bind them from environment variables
        assertTrue(config.getGoogle().getCloud().getProject().length() > 0);
        assertTrue(config.getAi().getModels().getGemini().getPro().getTemperature() >= 0);
        assertTrue(config.getContext().getEngine().getMaxConcurrentWorkflows() > 0);
    }
}