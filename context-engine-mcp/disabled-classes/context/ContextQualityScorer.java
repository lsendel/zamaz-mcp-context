package com.zamaz.adk.context;

import com.google.cloud.monitoring.v3.*;
import com.google.monitoring.v3.*;
import com.google.protobuf.util.Timestamps;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Context Quality Scorer - Provides real-time quality metrics and monitoring
 */
public class ContextQualityScorer {
    private final MetricServiceClient metricClient;
    private final String projectId;
    private final Map<String, QualityMetrics> realtimeMetrics = new ConcurrentHashMap<>();
    private final Map<String, QualityTrend> qualityTrends = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    // Quality thresholds
    private static final double EXCELLENT_THRESHOLD = 0.9;
    private static final double GOOD_THRESHOLD = 0.7;
    private static final double ACCEPTABLE_THRESHOLD = 0.5;
    private static final double POOR_THRESHOLD = 0.3;
    
    public ContextQualityScorer(String projectId) {
        this.projectId = projectId;
        try {
            this.metricClient = MetricServiceClient.create();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create metric client", e);
        }
        
        // Schedule periodic metric aggregation
        scheduler.scheduleAtFixedRate(this::aggregateMetrics, 0, 60, TimeUnit.SECONDS);
        
        // Schedule trend calculation
        scheduler.scheduleAtFixedRate(this::calculateTrends, 0, 300, TimeUnit.SECONDS);
    }
    
    /**
     * Real-time quality metrics
     */
    public static class QualityMetrics {
        private final String contextId;
        private final double overallScore;
        private final Map<String, Double> dimensionScores;
        private final QualityLevel level;
        private final long timestamp;
        private final Map<String, Object> metadata;
        
        public enum QualityLevel {
            EXCELLENT("Excellent", "✅", 0.9),
            GOOD("Good", "👍", 0.7),
            ACCEPTABLE("Acceptable", "📊", 0.5),
            POOR("Poor", "⚠️", 0.3),
            CRITICAL("Critical", "❌", 0.0);
            
            private final String label;
            private final String emoji;
            private final double threshold;
            
            QualityLevel(String label, String emoji, double threshold) {
                this.label = label;
                this.emoji = emoji;
                this.threshold = threshold;
            }
            
            public static QualityLevel fromScore(double score) {
                for (QualityLevel level : values()) {
                    if (score >= level.threshold) {
                        return level;
                    }
                }
                return CRITICAL;
            }
            
            public String getLabel() { return label; }
            public String getEmoji() { return emoji; }
            public double getThreshold() { return threshold; }
        }
        
        public QualityMetrics(String contextId, double overallScore,
                            Map<String, Double> dimensionScores,
                            Map<String, Object> metadata) {
            this.contextId = contextId;
            this.overallScore = overallScore;
            this.dimensionScores = dimensionScores;
            this.level = QualityLevel.fromScore(overallScore);
            this.timestamp = System.currentTimeMillis();
            this.metadata = metadata;
        }
        
        // Getters
        public String getContextId() { return contextId; }
        public double getOverallScore() { return overallScore; }
        public Map<String, Double> getDimensionScores() { return dimensionScores; }
        public QualityLevel getLevel() { return level; }
        public long getTimestamp() { return timestamp; }
        public Map<String, Object> getMetadata() { return metadata; }
    }
    
    /**
     * Quality trend analysis
     */
    public static class QualityTrend {
        private final String contextId;
        private final List<QualityDataPoint> dataPoints;
        private final double trend; // Positive = improving, negative = degrading
        private final double volatility;
        private final double forecast;
        private final Map<String, Double> dimensionTrends;
        
        public QualityTrend(String contextId, List<QualityDataPoint> dataPoints) {
            this.contextId = contextId;
            this.dataPoints = new ArrayList<>(dataPoints);
            this.trend = calculateTrend();
            this.volatility = calculateVolatility();
            this.forecast = calculateForecast();
            this.dimensionTrends = calculateDimensionTrends();
        }
        
        private double calculateTrend() {
            if (dataPoints.size() < 2) return 0;
            
            // Simple linear regression
            double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
            int n = dataPoints.size();
            
            for (int i = 0; i < n; i++) {
                sumX += i;
                sumY += dataPoints.get(i).score;
                sumXY += i * dataPoints.get(i).score;
                sumX2 += i * i;
            }
            
            double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
            return slope;
        }
        
        private double calculateVolatility() {
            if (dataPoints.size() < 2) return 0;
            
            double mean = dataPoints.stream()
                .mapToDouble(dp -> dp.score)
                .average()
                .orElse(0);
            
            double variance = dataPoints.stream()
                .mapToDouble(dp -> Math.pow(dp.score - mean, 2))
                .average()
                .orElse(0);
            
            return Math.sqrt(variance);
        }
        
        private double calculateForecast() {
            if (dataPoints.isEmpty()) return 0.5;
            
            double lastScore = dataPoints.get(dataPoints.size() - 1).score;
            double forecastedScore = lastScore + (trend * 5); // 5 periods ahead
            
            return Math.max(0, Math.min(1.0, forecastedScore));
        }
        
        private Map<String, Double> calculateDimensionTrends() {
            Map<String, List<Double>> dimensionData = new HashMap<>();
            
            // Collect dimension scores over time
            for (QualityDataPoint dp : dataPoints) {
                for (Map.Entry<String, Double> entry : dp.dimensionScores.entrySet()) {
                    dimensionData.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                        .add(entry.getValue());
                }
            }
            
            // Calculate trend for each dimension
            Map<String, Double> trends = new HashMap<>();
            for (Map.Entry<String, List<Double>> entry : dimensionData.entrySet()) {
                List<Double> scores = entry.getValue();
                if (scores.size() >= 2) {
                    double firstAvg = scores.subList(0, scores.size() / 2).stream()
                        .mapToDouble(Double::doubleValue).average().orElse(0);
                    double secondAvg = scores.subList(scores.size() / 2, scores.size()).stream()
                        .mapToDouble(Double::doubleValue).average().orElse(0);
                    trends.put(entry.getKey(), secondAvg - firstAvg);
                } else {
                    trends.put(entry.getKey(), 0.0);
                }
            }
            
            return trends;
        }
        
        public String getTrendDescription() {
            if (Math.abs(trend) < 0.01) return "Stable";
            return trend > 0 ? "Improving" : "Degrading";
        }
        
        public String getVolatilityDescription() {
            if (volatility < 0.05) return "Very Stable";
            if (volatility < 0.1) return "Stable";
            if (volatility < 0.2) return "Moderate";
            return "High Volatility";
        }
        
        // Getters
        public String getContextId() { return contextId; }
        public List<QualityDataPoint> getDataPoints() { return new ArrayList<>(dataPoints); }
        public double getTrend() { return trend; }
        public double getVolatility() { return volatility; }
        public double getForecast() { return forecast; }
        public Map<String, Double> getDimensionTrends() { return dimensionTrends; }
    }
    
    /**
     * Quality data point for trend analysis
     */
    public static class QualityDataPoint {
        private final double score;
        private final Map<String, Double> dimensionScores;
        private final long timestamp;
        
        public QualityDataPoint(double score, Map<String, Double> dimensionScores, 
                              long timestamp) {
            this.score = score;
            this.dimensionScores = new HashMap<>(dimensionScores);
            this.timestamp = timestamp;
        }
        
        // Getters
        public double getScore() { return score; }
        public Map<String, Double> getDimensionScores() { return dimensionScores; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Score context quality in real-time
     */
    public QualityMetrics scoreContext(String contextId, 
                                     ContextFailureDetector.ContextAnalysis analysis) {
        // Calculate dimension scores
        Map<String, Double> dimensionScores = new HashMap<>();
        
        // Accuracy dimension (inverse of poisoning)
        double accuracyScore = 1.0 - analysis.getDetections()
            .get(ContextFailureDetector.FailureMode.POISONING).getSeverity();
        dimensionScores.put("accuracy", accuracyScore);
        
        // Focus dimension (inverse of distraction)
        double focusScore = 1.0 - analysis.getDetections()
            .get(ContextFailureDetector.FailureMode.DISTRACTION).getSeverity();
        dimensionScores.put("focus", focusScore);
        
        // Clarity dimension (inverse of confusion)
        double clarityScore = 1.0 - analysis.getDetections()
            .get(ContextFailureDetector.FailureMode.CONFUSION).getSeverity();
        dimensionScores.put("clarity", clarityScore);
        
        // Consistency dimension (inverse of clash)
        double consistencyScore = 1.0 - analysis.getDetections()
            .get(ContextFailureDetector.FailureMode.CLASH).getSeverity();
        dimensionScores.put("consistency", consistencyScore);
        
        // Conciseness dimension (inverse of repetition)
        double concisenessScore = 1.0 - analysis.getDetections()
            .get(ContextFailureDetector.FailureMode.REPETITION).getSeverity();
        dimensionScores.put("conciseness", concisenessScore);
        
        // Coherence dimension
        double coherenceScore = 1.0 - analysis.getDetections()
            .get(ContextFailureDetector.FailureMode.INCOHERENCE).getSeverity();
        dimensionScores.put("coherence", coherenceScore);
        
        // Safety dimension (inverse of bias and manipulation)
        double safetyScore = 1.0 - Math.max(
            analysis.getDetections().get(ContextFailureDetector.FailureMode.BIAS).getSeverity(),
            analysis.getDetections().get(ContextFailureDetector.FailureMode.MANIPULATION).getSeverity()
        );
        dimensionScores.put("safety", safetyScore);
        
        // Information density (from metrics)
        ContextFailureDetector.ContextMetrics metrics = analysis.getMetrics();
        double densityScore = calculateInformationDensity(metrics);
        dimensionScores.put("information_density", densityScore);
        
        // Calculate overall score with weights
        Map<String, Double> weights = Map.of(
            "accuracy", 2.0,
            "safety", 2.0,
            "clarity", 1.5,
            "consistency", 1.5,
            "coherence", 1.0,
            "focus", 1.0,
            "conciseness", 0.5,
            "information_density", 0.5
        );
        
        double weightedSum = 0;
        double totalWeight = 0;
        
        for (Map.Entry<String, Double> entry : dimensionScores.entrySet()) {
            double weight = weights.getOrDefault(entry.getKey(), 1.0);
            weightedSum += entry.getValue() * weight;
            totalWeight += weight;
        }
        
        double overallScore = weightedSum / totalWeight;
        
        // Add metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("token_count", metrics.getTotalTokens());
        metadata.put("unique_tokens", metrics.getUniqueTokens());
        metadata.put("entropy", metrics.getEntropy());
        metadata.put("perplexity", metrics.getPerplexity());
        metadata.put("sentiment", metrics.getSentimentScore());
        metadata.put("detected_failures", analysis.getDetectedModes());
        
        // Create metrics object
        QualityMetrics qualityMetrics = new QualityMetrics(
            contextId, overallScore, dimensionScores, metadata);
        
        // Store for real-time access
        realtimeMetrics.put(contextId, qualityMetrics);
        
        // Send to Cloud Monitoring
        publishMetrics(qualityMetrics);
        
        return qualityMetrics;
    }
    
    /**
     * Calculate information density score
     */
    private double calculateInformationDensity(ContextFailureDetector.ContextMetrics metrics) {
        if (metrics.getTotalTokens() == 0) return 0;
        
        // Unique token ratio
        double uniqueRatio = (double) metrics.getUniqueTokens() / metrics.getTotalTokens();
        
        // Entropy normalized (0.3-0.7 is ideal)
        double entropyScore = 1.0 - Math.abs(metrics.getEntropy() - 0.5) * 2;
        
        // Perplexity score (lower is better, but not too low)
        double perplexityScore = metrics.getPerplexity() < 20 ? 0.5 :
                               metrics.getPerplexity() > 200 ? 0.2 :
                               1.0 - (metrics.getPerplexity() - 20) / 180;
        
        return (uniqueRatio + entropyScore + perplexityScore) / 3;
    }
    
    /**
     * Get real-time metrics for a context
     */
    public QualityMetrics getRealtimeMetrics(String contextId) {
        return realtimeMetrics.get(contextId);
    }
    
    /**
     * Get quality trend for a context
     */
    public QualityTrend getQualityTrend(String contextId) {
        return qualityTrends.get(contextId);
    }
    
    /**
     * Get comparative analysis across contexts
     */
    public ComparativeAnalysis compareContexts(List<String> contextIds) {
        Map<String, QualityMetrics> metrics = new HashMap<>();
        
        for (String contextId : contextIds) {
            QualityMetrics m = realtimeMetrics.get(contextId);
            if (m != null) {
                metrics.put(contextId, m);
            }
        }
        
        return new ComparativeAnalysis(metrics);
    }
    
    /**
     * Comparative analysis across contexts
     */
    public static class ComparativeAnalysis {
        private final Map<String, QualityMetrics> contextMetrics;
        private final String bestContext;
        private final String worstContext;
        private final Map<String, String> dimensionLeaders;
        private final Map<String, Double> averageScores;
        
        public ComparativeAnalysis(Map<String, QualityMetrics> contextMetrics) {
            this.contextMetrics = contextMetrics;
            
            // Find best and worst
            this.bestContext = contextMetrics.entrySet().stream()
                .max(Map.Entry.comparingByValue(
                    Comparator.comparingDouble(QualityMetrics::getOverallScore)))
                .map(Map.Entry::getKey)
                .orElse(null);
            
            this.worstContext = contextMetrics.entrySet().stream()
                .min(Map.Entry.comparingByValue(
                    Comparator.comparingDouble(QualityMetrics::getOverallScore)))
                .map(Map.Entry::getKey)
                .orElse(null);
            
            // Find dimension leaders
            this.dimensionLeaders = new HashMap<>();
            Set<String> dimensions = contextMetrics.values().stream()
                .flatMap(m -> m.getDimensionScores().keySet().stream())
                .collect(Collectors.toSet());
            
            for (String dimension : dimensions) {
                String leader = contextMetrics.entrySet().stream()
                    .max(Comparator.comparingDouble(e -> 
                        e.getValue().getDimensionScores().getOrDefault(dimension, 0.0)))
                    .map(Map.Entry::getKey)
                    .orElse(null);
                
                if (leader != null) {
                    dimensionLeaders.put(dimension, leader);
                }
            }
            
            // Calculate average scores
            this.averageScores = new HashMap<>();
            for (String dimension : dimensions) {
                double avg = contextMetrics.values().stream()
                    .mapToDouble(m -> m.getDimensionScores().getOrDefault(dimension, 0.0))
                    .average()
                    .orElse(0);
                averageScores.put(dimension, avg);
            }
        }
        
        public String getInsights() {
            StringBuilder insights = new StringBuilder();
            
            insights.append("Best performing context: ").append(bestContext)
                   .append(" (").append(String.format("%.2f", 
                       contextMetrics.get(bestContext).getOverallScore())).append(")\n");
            
            insights.append("Worst performing context: ").append(worstContext)
                   .append(" (").append(String.format("%.2f", 
                       contextMetrics.get(worstContext).getOverallScore())).append(")\n");
            
            insights.append("\nDimension leaders:\n");
            for (Map.Entry<String, String> entry : dimensionLeaders.entrySet()) {
                insights.append("- ").append(entry.getKey()).append(": ")
                       .append(entry.getValue()).append("\n");
            }
            
            return insights.toString();
        }
        
        // Getters
        public Map<String, QualityMetrics> getContextMetrics() { return contextMetrics; }
        public String getBestContext() { return bestContext; }
        public String getWorstContext() { return worstContext; }
        public Map<String, String> getDimensionLeaders() { return dimensionLeaders; }
        public Map<String, Double> getAverageScores() { return averageScores; }
    }
    
    /**
     * Set quality alert thresholds
     */
    public void setAlertThreshold(String contextId, double threshold, 
                                AlertCallback callback) {
        // Monitor quality and trigger alerts
        scheduler.scheduleAtFixedRate(() -> {
            QualityMetrics metrics = realtimeMetrics.get(contextId);
            if (metrics != null && metrics.getOverallScore() < threshold) {
                callback.onQualityAlert(contextId, metrics);
            }
        }, 0, 30, TimeUnit.SECONDS);
    }
    
    /**
     * Alert callback interface
     */
    public interface AlertCallback {
        void onQualityAlert(String contextId, QualityMetrics metrics);
    }
    
    /**
     * Publish metrics to Cloud Monitoring
     */
    private void publishMetrics(QualityMetrics metrics) {
        try {
            ProjectName projectName = ProjectName.of(projectId);
            
            // Create time series
            List<TimeSeries> timeSeries = new ArrayList<>();
            
            // Overall score metric
            timeSeries.add(createTimeSeries(
                "context_quality_score",
                metrics.getOverallScore(),
                Map.of("context_id", metrics.getContextId(),
                       "quality_level", metrics.getLevel().getLabel())
            ));
            
            // Dimension scores
            for (Map.Entry<String, Double> entry : metrics.getDimensionScores().entrySet()) {
                timeSeries.add(createTimeSeries(
                    "context_dimension_score",
                    entry.getValue(),
                    Map.of("context_id", metrics.getContextId(),
                           "dimension", entry.getKey())
                ));
            }
            
            // Send to Cloud Monitoring
            CreateTimeSeriesRequest request = CreateTimeSeriesRequest.newBuilder()
                .setName(projectName.toString())
                .addAllTimeSeries(timeSeries)
                .build();
            
            metricClient.createTimeSeries(request);
            
        } catch (Exception e) {
            System.err.println("Failed to publish metrics: " + e.getMessage());
        }
    }
    
    /**
     * Create time series for metric
     */
    private TimeSeries createTimeSeries(String metricType, double value,
                                       Map<String, String> labels) {
        Metric metric = Metric.newBuilder()
            .setType("custom.googleapis.com/context/" + metricType)
            .putAllLabels(labels)
            .build();
        
        Point point = Point.newBuilder()
            .setInterval(TimeInterval.newBuilder()
                .setEndTime(Timestamps.fromMillis(System.currentTimeMillis()))
                .build())
            .setValue(TypedValue.newBuilder()
                .setDoubleValue(value)
                .build())
            .build();
        
        return TimeSeries.newBuilder()
            .setMetric(metric)
            .addPoints(point)
            .build();
    }
    
    /**
     * Aggregate metrics periodically
     */
    private void aggregateMetrics() {
        // Clean old metrics
        long cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24);
        realtimeMetrics.entrySet().removeIf(entry -> 
            entry.getValue().getTimestamp() < cutoff);
    }
    
    /**
     * Calculate quality trends
     */
    private void calculateTrends() {
        Map<String, List<QualityDataPoint>> contextDataPoints = new HashMap<>();
        
        // Group data points by context
        for (QualityMetrics metrics : realtimeMetrics.values()) {
            contextDataPoints.computeIfAbsent(metrics.getContextId(), k -> new ArrayList<>())
                .add(new QualityDataPoint(
                    metrics.getOverallScore(),
                    metrics.getDimensionScores(),
                    metrics.getTimestamp()
                ));
        }
        
        // Calculate trends for each context
        for (Map.Entry<String, List<QualityDataPoint>> entry : contextDataPoints.entrySet()) {
            List<QualityDataPoint> points = entry.getValue();
            
            // Sort by timestamp
            points.sort(Comparator.comparingLong(QualityDataPoint::getTimestamp));
            
            // Keep last 100 points
            if (points.size() > 100) {
                points = points.subList(points.size() - 100, points.size());
            }
            
            if (points.size() >= 5) {
                QualityTrend trend = new QualityTrend(entry.getKey(), points);
                qualityTrends.put(entry.getKey(), trend);
            }
        }
    }
    
    /**
     * Export quality report
     */
    public QualityReport generateReport(String contextId, long startTime, long endTime) {
        List<QualityMetrics> historicalMetrics = new ArrayList<>();
        
        // Collect historical metrics (in production, query from storage)
        QualityMetrics current = realtimeMetrics.get(contextId);
        if (current != null) {
            historicalMetrics.add(current);
        }
        
        QualityTrend trend = qualityTrends.get(contextId);
        
        return new QualityReport(contextId, historicalMetrics, trend, 
            startTime, endTime);
    }
    
    /**
     * Quality report
     */
    public static class QualityReport {
        private final String contextId;
        private final List<QualityMetrics> metrics;
        private final QualityTrend trend;
        private final long startTime;
        private final long endTime;
        private final Map<String, Object> summary;
        
        public QualityReport(String contextId, List<QualityMetrics> metrics,
                           QualityTrend trend, long startTime, long endTime) {
            this.contextId = contextId;
            this.metrics = metrics;
            this.trend = trend;
            this.startTime = startTime;
            this.endTime = endTime;
            this.summary = generateSummary();
        }
        
        private Map<String, Object> generateSummary() {
            Map<String, Object> summary = new HashMap<>();
            
            if (!metrics.isEmpty()) {
                double avgScore = metrics.stream()
                    .mapToDouble(QualityMetrics::getOverallScore)
                    .average()
                    .orElse(0);
                
                double minScore = metrics.stream()
                    .mapToDouble(QualityMetrics::getOverallScore)
                    .min()
                    .orElse(0);
                
                double maxScore = metrics.stream()
                    .mapToDouble(QualityMetrics::getOverallScore)
                    .max()
                    .orElse(0);
                
                summary.put("average_score", avgScore);
                summary.put("min_score", minScore);
                summary.put("max_score", maxScore);
                summary.put("sample_count", metrics.size());
            }
            
            if (trend != null) {
                summary.put("trend", trend.getTrendDescription());
                summary.put("volatility", trend.getVolatilityDescription());
                summary.put("forecast", trend.getForecast());
            }
            
            return summary;
        }
        
        // Getters
        public String getContextId() { return contextId; }
        public List<QualityMetrics> getMetrics() { return metrics; }
        public QualityTrend getTrend() { return trend; }
        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }
        public Map<String, Object> getSummary() { return summary; }
    }
    
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            metricClient.close();
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
}