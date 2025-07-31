package com.zamaz.adk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Configuration properties for ADK components
 * Externalizes all hardcoded values from the codebase
 */
@ConfigurationProperties(prefix = "adk")
public class ADKConfigurationProperties {

    private Google google = new Google();
    private Ai ai = new Ai();
    private Context context = new Context();
    private Agents agents = new Agents();
    private Workflow workflow = new Workflow();
    private Tools tools = new Tools();
    private Vector vector = new Vector();
    private Resources resources = new Resources();
    private Monitoring monitoring = new Monitoring();
    private Tenant tenant = new Tenant();
    private Security security = new Security();
    private Debug debug = new Debug();

    // Google Cloud Configuration
    public static class Google {
        private Cloud cloud = new Cloud();
        
        public static class Cloud {
            private String project = "zamaz-dev";
            private String location = "us-central1";
            private Pubsub pubsub = new Pubsub();
            private Storage storage = new Storage();
            
            public static class Pubsub {
                private String topic = "agent-events";
                public String getTopic() { return topic; }
                public void setTopic(String topic) { this.topic = topic; }
            }
            
            public static class Storage {
                private String bucket = "zamaz-context-bucket";
                public String getBucket() { return bucket; }
                public void setBucket(String bucket) { this.bucket = bucket; }
            }
            
            // Getters and setters
            public String getProject() { return project; }
            public void setProject(String project) { this.project = project; }
            public String getLocation() { return location; }
            public void setLocation(String location) { this.location = location; }
            public Pubsub getPubsub() { return pubsub; }
            public Storage getStorage() { return storage; }
        }
        
        public Cloud getCloud() { return cloud; }
    }

    // AI Model Configuration
    public static class Ai {
        private Models models = new Models();
        
        public static class Models {
            private Gemini gemini = new Gemini();
            
            public static class Gemini {
                private ModelConfig pro = new ModelConfig("gemini-1.5-pro", 0.7, 2048, 40, 0.95);
                private ModelConfig flash = new ModelConfig("gemini-1.5-flash", 0.5, 1024, 40, 0.95);
                private ModelConfig decision = new ModelConfig("gemini-1.5-flash", 0.3, 50, 40, 0.95);
                private ModelConfig classification = new ModelConfig("gemini-1.5-flash", 0.3, 100, 40, 0.95);
                private ModelConfig keywordExtraction = new ModelConfig("gemini-1.5-flash", 0.5, 200, 40, 0.95);
                private ModelConfig creative = new ModelConfig("gemini-1.5-pro", 0.8, 400, 40, 0.95);
                
                // Getters
                public ModelConfig getPro() { return pro; }
                public ModelConfig getFlash() { return flash; }
                public ModelConfig getDecision() { return decision; }
                public ModelConfig getClassification() { return classification; }
                public ModelConfig getKeywordExtraction() { return keywordExtraction; }
                public ModelConfig getCreative() { return creative; }
            }
            
            public Gemini getGemini() { return gemini; }
        }
        
        public static class ModelConfig {
            private String name;
            private double temperature;
            private int maxOutputTokens;
            private int topK;
            private double topP;
            
            public ModelConfig() {}
            
            public ModelConfig(String name, double temperature, int maxOutputTokens, int topK, double topP) {
                this.name = name;
                this.temperature = temperature;
                this.maxOutputTokens = maxOutputTokens;
                this.topK = topK;
                this.topP = topP;
            }
            
            // Getters and setters
            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            public double getTemperature() { return temperature; }
            public void setTemperature(double temperature) { this.temperature = temperature; }
            public int getMaxOutputTokens() { return maxOutputTokens; }
            public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }
            public int getTopK() { return topK; }
            public void setTopK(int topK) { this.topK = topK; }
            public double getTopP() { return topP; }
            public void setTopP(double topP) { this.topP = topP; }
        }
        
        public Models getModels() { return models; }
    }

    // Context Configuration  
    public static class Context {
        private Engine engine = new Engine();
        private Quality quality = new Quality();
        private Window window = new Window();
        private Memory memory = new Memory();
        
        public static class Engine {
            private int maxConcurrentWorkflows = 100;
            private long defaultTimeoutMs = 300000L; // 5 minutes
            
            public int getMaxConcurrentWorkflows() { return maxConcurrentWorkflows; }
            public void setMaxConcurrentWorkflows(int maxConcurrentWorkflows) { this.maxConcurrentWorkflows = maxConcurrentWorkflows; }
            public long getDefaultTimeoutMs() { return defaultTimeoutMs; }
            public void setDefaultTimeoutMs(long defaultTimeoutMs) { this.defaultTimeoutMs = defaultTimeoutMs; }
        }
        
        public static class Quality {
            private double acceptableThreshold = 0.5;
            private int aggregationIntervalSeconds = 60;
            private int trendCalculationIntervalSeconds = 300;
            private int monitoringCollectionIntervalSeconds = 30;
            
            // Getters and setters
            public double getAcceptableThreshold() { return acceptableThreshold; }
            public void setAcceptableThreshold(double acceptableThreshold) { this.acceptableThreshold = acceptableThreshold; }
            public int getAggregationIntervalSeconds() { return aggregationIntervalSeconds; }
            public void setAggregationIntervalSeconds(int aggregationIntervalSeconds) { this.aggregationIntervalSeconds = aggregationIntervalSeconds; }
            public int getTrendCalculationIntervalSeconds() { return trendCalculationIntervalSeconds; }
            public void setTrendCalculationIntervalSeconds(int trendCalculationIntervalSeconds) { this.trendCalculationIntervalSeconds = trendCalculationIntervalSeconds; }
            public int getMonitoringCollectionIntervalSeconds() { return monitoringCollectionIntervalSeconds; }
            public void setMonitoringCollectionIntervalSeconds(int monitoringCollectionIntervalSeconds) { this.monitoringCollectionIntervalSeconds = monitoringCollectionIntervalSeconds; }
        }
        
        public static class Window {
            private int defaultSizeTokens = 8192;
            private int chunkSizeTokens = 512;
            
            public int getDefaultSizeTokens() { return defaultSizeTokens; }
            public void setDefaultSizeTokens(int defaultSizeTokens) { this.defaultSizeTokens = defaultSizeTokens; }
            public int getChunkSizeTokens() { return chunkSizeTokens; }
            public void setChunkSizeTokens(int chunkSizeTokens) { this.chunkSizeTokens = chunkSizeTokens; }
        }
        
        public static class Memory {
            private int storageThresholdBytes = 10240;
            private int maxSegmentSizeBytes = 1048576; // 1MB
            private int cacheExpirationMinutes = 5;
            private int consolidationMinChunks = 5;
            private int decayHalfLifeDays = 30;
            private long estimatedBytesPerChunk = 2048L;
            
            // Getters and setters
            public int getStorageThresholdBytes() { return storageThresholdBytes; }
            public void setStorageThresholdBytes(int storageThresholdBytes) { this.storageThresholdBytes = storageThresholdBytes; }
            public int getMaxSegmentSizeBytes() { return maxSegmentSizeBytes; }
            public void setMaxSegmentSizeBytes(int maxSegmentSizeBytes) { this.maxSegmentSizeBytes = maxSegmentSizeBytes; }
            public int getCacheExpirationMinutes() { return cacheExpirationMinutes; }
            public void setCacheExpirationMinutes(int cacheExpirationMinutes) { this.cacheExpirationMinutes = cacheExpirationMinutes; }
            public int getConsolidationMinChunks() { return consolidationMinChunks; }
            public void setConsolidationMinChunks(int consolidationMinChunks) { this.consolidationMinChunks = consolidationMinChunks; }
            public int getDecayHalfLifeDays() { return decayHalfLifeDays; }
            public void setDecayHalfLifeDays(int decayHalfLifeDays) { this.decayHalfLifeDays = decayHalfLifeDays; }
            public long getEstimatedBytesPerChunk() { return estimatedBytesPerChunk; }
            public void setEstimatedBytesPerChunk(long estimatedBytesPerChunk) { this.estimatedBytesPerChunk = estimatedBytesPerChunk; }
        }
        
        // Getters
        public Engine getEngine() { return engine; }
        public Quality getQuality() { return quality; }
        public Window getWindow() { return window; }
        public Memory getMemory() { return memory; }
    }

    // Additional configuration classes would be added here...
    // For brevity, I'll add a few key ones

    public static class Resources {
        private Shutdown shutdown = new Shutdown();
        private Executor executor = new Executor();
        private Publisher publisher = new Publisher();
        
        public static class Shutdown {
            private int gracefulTimeoutSeconds = 60;
            private int forceTimeoutSeconds = 30;
            private int emergencyTimeoutSeconds = 15;
            
            public int getGracefulTimeoutSeconds() { return gracefulTimeoutSeconds; }
            public void setGracefulTimeoutSeconds(int gracefulTimeoutSeconds) { this.gracefulTimeoutSeconds = gracefulTimeoutSeconds; }
            public int getForceTimeoutSeconds() { return forceTimeoutSeconds; }
            public void setForceTimeoutSeconds(int forceTimeoutSeconds) { this.forceTimeoutSeconds = forceTimeoutSeconds; }
            public int getEmergencyTimeoutSeconds() { return emergencyTimeoutSeconds; }
            public void setEmergencyTimeoutSeconds(int emergencyTimeoutSeconds) { this.emergencyTimeoutSeconds = emergencyTimeoutSeconds; }
        }
        
        public static class Executor {
            private int workStealingPoolSize = -1; // Use available processors
            private int scheduledPoolSize = 4;
            
            public int getWorkStealingPoolSize() { return workStealingPoolSize; }
            public void setWorkStealingPoolSize(int workStealingPoolSize) { this.workStealingPoolSize = workStealingPoolSize; }
            public int getScheduledPoolSize() { return scheduledPoolSize; }
            public void setScheduledPoolSize(int scheduledPoolSize) { this.scheduledPoolSize = scheduledPoolSize; }
        }
        
        public static class Publisher {
            private int shutdownTimeoutSeconds = 30;
            
            public int getShutdownTimeoutSeconds() { return shutdownTimeoutSeconds; }
            public void setShutdownTimeoutSeconds(int shutdownTimeoutSeconds) { this.shutdownTimeoutSeconds = shutdownTimeoutSeconds; }
        }
        
        public Shutdown getShutdown() { return shutdown; }
        public Executor getExecutor() { return executor; }
        public Publisher getPublisher() { return publisher; }
    }

    // Placeholder classes for other configuration sections
    public static class Agents {
        // Add agent-specific configuration
    }
    
    public static class Workflow {
        // Add workflow-specific configuration  
    }
    
    public static class Tools {
        // Add tool-specific configuration
    }
    
    public static class Vector {
        // Add vector store configuration
    }
    
    public static class Monitoring {
        // Add monitoring configuration
    }
    
    public static class Tenant {
        // Add tenant configuration
    }
    
    public static class Security {
        // Add security configuration
    }
    
    public static class Debug {
        private boolean enabled = false;
        private boolean verbose = false;
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isVerbose() { return verbose; }
        public void setVerbose(boolean verbose) { this.verbose = verbose; }
    }

    // Main getters
    public Google getGoogle() { return google; }
    public Ai getAi() { return ai; }
    public Context getContext() { return context; }
    public Agents getAgents() { return agents; }
    public Workflow getWorkflow() { return workflow; }
    public Tools getTools() { return tools; }
    public Vector getVector() { return vector; }
    public Resources getResources() { return resources; }
    public Monitoring getMonitoring() { return monitoring; }
    public Tenant getTenant() { return tenant; }
    public Security getSecurity() { return security; }
    public Debug getDebug() { return debug; }
}