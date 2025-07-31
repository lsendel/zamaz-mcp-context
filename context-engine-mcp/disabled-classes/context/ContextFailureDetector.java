package com.zamaz.adk.context;

import com.google.cloud.aiplatform.v1.*;
import com.google.cloud.language.v1.*;
import com.google.cloud.translate.v3.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Context Failure Detector - Detects various failure modes in AI contexts
 * including poisoning, distraction, confusion, and clash
 */
public class ContextFailureDetector {
    private final LanguageServiceClient languageClient;
    private final TranslationServiceClient translateClient;
    private final Map<FailureMode, FailureDetector> detectors = new HashMap<>();
    private final Map<String, ContextMetrics> contextMetrics = new ConcurrentHashMap<>();
    
    public enum FailureMode {
        POISONING,      // Hallucinations, false information
        DISTRACTION,    // Off-topic content
        CONFUSION,      // Contradictory information
        CLASH,          // Conflicting instructions
        REPETITION,     // Excessive repetition
        INCOHERENCE,    // Logical inconsistencies
        BIAS,           // Harmful biases
        MANIPULATION    // Attempted prompt manipulation
    }
    
    public ContextFailureDetector(String projectId) {
        try {
            this.languageClient = LanguageServiceClient.create();
            this.translateClient = TranslationServiceClient.create();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize clients", e);
        }
        
        initializeDetectors();
    }
    
    /**
     * Context analysis result
     */
    public static class ContextAnalysis {
        private final String contextId;
        private final Map<FailureMode, FailureDetection> detections;
        private final double overallQualityScore;
        private final List<String> recommendations;
        private final ContextMetrics metrics;
        
        public ContextAnalysis(String contextId, 
                             Map<FailureMode, FailureDetection> detections,
                             double overallQualityScore,
                             List<String> recommendations,
                             ContextMetrics metrics) {
            this.contextId = contextId;
            this.detections = detections;
            this.overallQualityScore = overallQualityScore;
            this.recommendations = recommendations;
            this.metrics = metrics;
        }
        
        public boolean hasFailures() {
            return detections.values().stream()
                .anyMatch(d -> d.getSeverity() >= 0.5);
        }
        
        public List<FailureMode> getDetectedModes() {
            return detections.entrySet().stream()
                .filter(e -> e.getValue().isDetected())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        }
        
        // Getters
        public String getContextId() { return contextId; }
        public Map<FailureMode, FailureDetection> getDetections() { return detections; }
        public double getOverallQualityScore() { return overallQualityScore; }
        public List<String> getRecommendations() { return recommendations; }
        public ContextMetrics getMetrics() { return metrics; }
    }
    
    /**
     * Individual failure detection result
     */
    public static class FailureDetection {
        private final FailureMode mode;
        private final boolean detected;
        private final double severity;
        private final String evidence;
        private final List<String> locations;
        
        public FailureDetection(FailureMode mode, boolean detected, 
                              double severity, String evidence,
                              List<String> locations) {
            this.mode = mode;
            this.detected = detected;
            this.severity = severity;
            this.evidence = evidence;
            this.locations = locations;
        }
        
        // Getters
        public FailureMode getMode() { return mode; }
        public boolean isDetected() { return detected; }
        public double getSeverity() { return severity; }
        public String getEvidence() { return evidence; }
        public List<String> getLocations() { return locations; }
    }
    
    /**
     * Context metrics for tracking
     */
    public static class ContextMetrics {
        private final int totalTokens;
        private final int uniqueTokens;
        private final double entropy;
        private final double perplexity;
        private final Map<String, Integer> topicDistribution;
        private final double sentimentScore;
        private final double toxicityScore;
        
        public ContextMetrics(int totalTokens, int uniqueTokens, double entropy,
                            double perplexity, Map<String, Integer> topicDistribution,
                            double sentimentScore, double toxicityScore) {
            this.totalTokens = totalTokens;
            this.uniqueTokens = uniqueTokens;
            this.entropy = entropy;
            this.perplexity = perplexity;
            this.topicDistribution = topicDistribution;
            this.sentimentScore = sentimentScore;
            this.toxicityScore = toxicityScore;
        }
        
        // Getters
        public int getTotalTokens() { return totalTokens; }
        public int getUniqueTokens() { return uniqueTokens; }
        public double getEntropy() { return entropy; }
        public double getPerplexity() { return perplexity; }
        public Map<String, Integer> getTopicDistribution() { return topicDistribution; }
        public double getSentimentScore() { return sentimentScore; }
        public double getToxicityScore() { return toxicityScore; }
    }
    
    /**
     * Base interface for failure detectors
     */
    private interface FailureDetector {
        FailureDetection detect(String content, ContextMetrics metrics);
    }
    
    /**
     * Initialize all detectors
     */
    private void initializeDetectors() {
        // Poisoning detector - detects hallucinations and false information
        detectors.put(FailureMode.POISONING, new PoisoningDetector());
        
        // Distraction detector - detects off-topic content
        detectors.put(FailureMode.DISTRACTION, new DistractionDetector());
        
        // Confusion detector - detects contradictory information
        detectors.put(FailureMode.CONFUSION, new ConfusionDetector());
        
        // Clash detector - detects conflicting instructions
        detectors.put(FailureMode.CLASH, new ClashDetector());
        
        // Repetition detector
        detectors.put(FailureMode.REPETITION, new RepetitionDetector());
        
        // Incoherence detector
        detectors.put(FailureMode.INCOHERENCE, new IncoherenceDetector());
        
        // Bias detector
        detectors.put(FailureMode.BIAS, new BiasDetector());
        
        // Manipulation detector
        detectors.put(FailureMode.MANIPULATION, new ManipulationDetector());
    }
    
    /**
     * Analyze context for failures
     */
    public ContextAnalysis analyzeContext(String contextId, String content) {
        // Calculate metrics
        ContextMetrics metrics = calculateMetrics(content);
        contextMetrics.put(contextId, metrics);
        
        // Run all detectors
        Map<FailureMode, FailureDetection> detections = new HashMap<>();
        for (Map.Entry<FailureMode, FailureDetector> entry : detectors.entrySet()) {
            detections.put(entry.getKey(), entry.getValue().detect(content, metrics));
        }
        
        // Calculate overall quality score
        double qualityScore = calculateQualityScore(detections, metrics);
        
        // Generate recommendations
        List<String> recommendations = generateRecommendations(detections, metrics);
        
        return new ContextAnalysis(contextId, detections, qualityScore, 
            recommendations, metrics);
    }
    
    /**
     * Poisoning detector - detects hallucinations and false information
     */
    private class PoisoningDetector implements FailureDetector {
        private final Set<String> hallucinationPhrases = Set.of(
            "as an ai", "as a language model", "i cannot", "i don't have access",
            "quantum supremacy", "100% accuracy", "perfect solution",
            "breakthrough technology", "revolutionary", "game-changing"
        );
        
        private final Pattern superlativePattern = Pattern.compile(
            "\\b(always|never|every|all|none|impossible|guaranteed|definitely)\\b",
            Pattern.CASE_INSENSITIVE
        );
        
        @Override
        public FailureDetection detect(String content, ContextMetrics metrics) {
            String lowerContent = content.toLowerCase();
            List<String> locations = new ArrayList<>();
            double severity = 0;
            
            // Check for hallucination phrases
            for (String phrase : hallucinationPhrases) {
                if (lowerContent.contains(phrase)) {
                    locations.add("Phrase: " + phrase);
                    severity += 0.2;
                }
            }
            
            // Check for excessive superlatives
            var matcher = superlativePattern.matcher(content);
            int superlativeCount = 0;
            while (matcher.find()) {
                superlativeCount++;
                if (superlativeCount <= 3) {
                    locations.add("Superlative at position " + matcher.start());
                }
            }
            if (superlativeCount > 5) {
                severity += 0.3;
            }
            
            // Check perplexity (high perplexity might indicate hallucination)
            if (metrics.getPerplexity() > 100) {
                severity += 0.2;
                locations.add("High perplexity: " + metrics.getPerplexity());
            }
            
            // Use sentiment analysis for unrealistic positivity
            if (metrics.getSentimentScore() > 0.9) {
                severity += 0.1;
                locations.add("Extremely positive sentiment");
            }
            
            boolean detected = severity > 0.3;
            String evidence = detected ? 
                "Potential hallucination detected. Found " + locations.size() + " indicators." : 
                "No significant hallucination detected.";
            
            return new FailureDetection(FailureMode.POISONING, detected, 
                Math.min(1.0, severity), evidence, locations);
        }
    }
    
    /**
     * Distraction detector - detects off-topic content
     */
    private class DistractionDetector implements FailureDetector {
        @Override
        public FailureDetection detect(String content, ContextMetrics metrics) {
            // Analyze topic distribution
            Map<String, Integer> topics = metrics.getTopicDistribution();
            if (topics.isEmpty()) {
                return new FailureDetection(FailureMode.DISTRACTION, false, 0, 
                    "No topic analysis available", Collections.emptyList());
            }
            
            // Calculate topic concentration
            int totalTopics = topics.values().stream().mapToInt(Integer::intValue).sum();
            double maxTopicRatio = topics.values().stream()
                .mapToDouble(count -> (double) count / totalTopics)
                .max().orElse(0);
            
            // If no topic dominates (max < 30%), likely distracted
            boolean detected = maxTopicRatio < 0.3 && topics.size() > 5;
            double severity = detected ? 1.0 - maxTopicRatio : 0;
            
            List<String> locations = new ArrayList<>();
            if (detected) {
                locations.add("Topics scattered across " + topics.size() + " different areas");
                locations.add("Dominant topic only " + (maxTopicRatio * 100) + "% of content");
            }
            
            return new FailureDetection(FailureMode.DISTRACTION, detected, severity,
                detected ? "Content lacks focus, jumping between topics" : "Content is focused",
                locations);
        }
    }
    
    /**
     * Confusion detector - detects contradictory information
     */
    private class ConfusionDetector implements FailureDetector {
        private final Map<String, String> contradictionPairs = Map.of(
            "increase", "decrease",
            "improve", "worsen",
            "faster", "slower",
            "more", "less",
            "always", "never"
        );
        
        @Override
        public FailureDetection detect(String content, ContextMetrics metrics) {
            String[] sentences = content.split("[.!?]+");
            List<String> locations = new ArrayList<>();
            double severity = 0;
            
            // Check for contradictory statements
            for (int i = 0; i < sentences.length - 1; i++) {
                for (int j = i + 1; j < sentences.length; j++) {
                    String sent1 = sentences[i].toLowerCase();
                    String sent2 = sentences[j].toLowerCase();
                    
                    for (Map.Entry<String, String> pair : contradictionPairs.entrySet()) {
                        if ((sent1.contains(pair.getKey()) && sent2.contains(pair.getValue())) ||
                            (sent1.contains(pair.getValue()) && sent2.contains(pair.getKey()))) {
                            
                            // Check if they refer to the same subject
                            if (shareCommonNouns(sent1, sent2)) {
                                locations.add("Contradiction between sentences " + i + " and " + j);
                                severity += 0.3;
                            }
                        }
                    }
                }
            }
            
            // Check entropy (high entropy might indicate confusion)
            if (metrics.getEntropy() > 0.8) {
                severity += 0.2;
                locations.add("High entropy: " + metrics.getEntropy());
            }
            
            boolean detected = severity > 0.3;
            return new FailureDetection(FailureMode.CONFUSION, detected, 
                Math.min(1.0, severity),
                detected ? "Contradictory statements detected" : "No contradictions found",
                locations);
        }
        
        private boolean shareCommonNouns(String sent1, String sent2) {
            // Simple check - in production use NLP
            Set<String> words1 = new HashSet<>(Arrays.asList(sent1.split("\\s+")));
            Set<String> words2 = new HashSet<>(Arrays.asList(sent2.split("\\s+")));
            words1.retainAll(words2);
            return words1.size() > 2;
        }
    }
    
    /**
     * Clash detector - detects conflicting instructions
     */
    private class ClashDetector implements FailureDetector {
        private final Pattern instructionPattern = Pattern.compile(
            "\\b(do|don't|must|should|shall|will|can|cannot|forbidden|required)\\b",
            Pattern.CASE_INSENSITIVE
        );
        
        @Override
        public FailureDetection detect(String content, ContextMetrics metrics) {
            List<String> instructions = extractInstructions(content);
            List<String> locations = new ArrayList<>();
            double severity = 0;
            
            // Check for conflicting modal verbs
            Map<String, List<String>> modalGroups = new HashMap<>();
            for (String instruction : instructions) {
                String lower = instruction.toLowerCase();
                if (lower.contains("must") || lower.contains("required")) {
                    modalGroups.computeIfAbsent("mandatory", k -> new ArrayList<>()).add(instruction);
                } else if (lower.contains("should") || lower.contains("can")) {
                    modalGroups.computeIfAbsent("optional", k -> new ArrayList<>()).add(instruction);
                } else if (lower.contains("don't") || lower.contains("cannot") || lower.contains("forbidden")) {
                    modalGroups.computeIfAbsent("prohibited", k -> new ArrayList<>()).add(instruction);
                }
            }
            
            // Check for conflicts
            if (modalGroups.size() > 1) {
                for (String mandatory : modalGroups.getOrDefault("mandatory", Collections.emptyList())) {
                    for (String prohibited : modalGroups.getOrDefault("prohibited", Collections.emptyList())) {
                        if (shareCommonAction(mandatory, prohibited)) {
                            locations.add("Conflict: mandatory vs prohibited action");
                            severity += 0.4;
                        }
                    }
                }
            }
            
            boolean detected = severity > 0.3;
            return new FailureDetection(FailureMode.CLASH, detected,
                Math.min(1.0, severity),
                detected ? "Conflicting instructions detected" : "Instructions are consistent",
                locations);
        }
        
        private List<String> extractInstructions(String content) {
            List<String> instructions = new ArrayList<>();
            var matcher = instructionPattern.matcher(content);
            String[] sentences = content.split("[.!?]+");
            
            for (String sentence : sentences) {
                if (instructionPattern.matcher(sentence).find()) {
                    instructions.add(sentence.trim());
                }
            }
            return instructions;
        }
        
        private boolean shareCommonAction(String inst1, String inst2) {
            // Extract verbs and objects - simplified
            Set<String> words1 = extractActionWords(inst1);
            Set<String> words2 = extractActionWords(inst2);
            words1.retainAll(words2);
            return !words1.isEmpty();
        }
        
        private Set<String> extractActionWords(String instruction) {
            return Arrays.stream(instruction.toLowerCase().split("\\s+"))
                .filter(word -> word.length() > 3 && !isStopWord(word))
                .collect(Collectors.toSet());
        }
        
        private boolean isStopWord(String word) {
            return Set.of("the", "and", "that", "this", "with", "from", "must", 
                         "should", "will", "can", "don't").contains(word);
        }
    }
    
    /**
     * Repetition detector
     */
    private class RepetitionDetector implements FailureDetector {
        @Override
        public FailureDetection detect(String content, ContextMetrics metrics) {
            String[] words = content.toLowerCase().split("\\s+");
            Map<String, Integer> wordFreq = new HashMap<>();
            List<String> locations = new ArrayList<>();
            
            // Count word frequencies
            for (String word : words) {
                if (word.length() > 3) {
                    wordFreq.merge(word, 1, Integer::sum);
                }
            }
            
            // Find excessive repetitions
            double totalWords = words.length;
            double severity = 0;
            
            for (Map.Entry<String, Integer> entry : wordFreq.entrySet()) {
                double frequency = entry.getValue() / totalWords;
                if (frequency > 0.05 && entry.getValue() > 5) {
                    locations.add("Word '" + entry.getKey() + "' repeated " + 
                        entry.getValue() + " times");
                    severity += frequency;
                }
            }
            
            // Check phrase repetition
            Map<String, Integer> phraseFreq = new HashMap<>();
            for (int i = 0; i < words.length - 2; i++) {
                String phrase = words[i] + " " + words[i + 1] + " " + words[i + 2];
                phraseFreq.merge(phrase, 1, Integer::sum);
            }
            
            for (Map.Entry<String, Integer> entry : phraseFreq.entrySet()) {
                if (entry.getValue() > 3) {
                    locations.add("Phrase '" + entry.getKey() + "' repeated " + 
                        entry.getValue() + " times");
                    severity += 0.2;
                }
            }
            
            boolean detected = severity > 0.3;
            return new FailureDetection(FailureMode.REPETITION, detected,
                Math.min(1.0, severity),
                detected ? "Excessive repetition detected" : "Normal repetition levels",
                locations);
        }
    }
    
    /**
     * Incoherence detector
     */
    private class IncoherenceDetector implements FailureDetector {
        @Override
        public FailureDetection detect(String content, ContextMetrics metrics) {
            // Use perplexity and entropy as indicators
            double severity = 0;
            List<String> locations = new ArrayList<>();
            
            if (metrics.getPerplexity() > 150) {
                severity += 0.4;
                locations.add("Very high perplexity: " + metrics.getPerplexity());
            }
            
            if (metrics.getEntropy() > 0.9) {
                severity += 0.3;
                locations.add("Very high entropy: " + metrics.getEntropy());
            }
            
            // Check sentence structure coherence
            String[] sentences = content.split("[.!?]+");
            int incoherentCount = 0;
            
            for (String sentence : sentences) {
                if (sentence.trim().split("\\s+").length < 3) {
                    continue; // Skip very short sentences
                }
                
                // Simple coherence check - proper sentence structure
                if (!sentence.matches(".*[a-zA-Z].*") || 
                    sentence.split(",").length > 8) {
                    incoherentCount++;
                    if (incoherentCount <= 3) {
                        locations.add("Incoherent sentence structure");
                    }
                }
            }
            
            if (incoherentCount > sentences.length * 0.2) {
                severity += 0.3;
            }
            
            boolean detected = severity > 0.4;
            return new FailureDetection(FailureMode.INCOHERENCE, detected,
                Math.min(1.0, severity),
                detected ? "Logical incoherence detected" : "Content is coherent",
                locations);
        }
    }
    
    /**
     * Bias detector
     */
    private class BiasDetector implements FailureDetector {
        @Override
        public FailureDetection detect(String content, ContextMetrics metrics) {
            // Check toxicity score
            double severity = 0;
            List<String> locations = new ArrayList<>();
            
            if (metrics.getToxicityScore() > 0.7) {
                severity = metrics.getToxicityScore();
                locations.add("High toxicity score: " + metrics.getToxicityScore());
            }
            
            // Additional bias detection would go here
            // In production, use specialized bias detection models
            
            boolean detected = severity > 0.5;
            return new FailureDetection(FailureMode.BIAS, detected,
                severity,
                detected ? "Potential bias detected" : "No significant bias detected",
                locations);
        }
    }
    
    /**
     * Manipulation detector
     */
    private class ManipulationDetector implements FailureDetector {
        private final Pattern jailbreakPattern = Pattern.compile(
            "ignore.*previous|disregard.*instructions|forget.*said|new.*instructions|" +
            "pretend.*you.*are|act.*as.*if|roleplay|bypass|override",
            Pattern.CASE_INSENSITIVE
        );
        
        @Override
        public FailureDetection detect(String content, ContextMetrics metrics) {
            List<String> locations = new ArrayList<>();
            double severity = 0;
            
            // Check for jailbreak attempts
            var matcher = jailbreakPattern.matcher(content);
            while (matcher.find()) {
                locations.add("Potential jailbreak at position " + matcher.start());
                severity += 0.5;
            }
            
            // Check for encoded content
            if (containsEncoding(content)) {
                locations.add("Encoded content detected");
                severity += 0.3;
            }
            
            // Check for prompt injection patterns
            if (content.contains("[[") || content.contains("{{") || 
                content.contains("<|") || content.contains("|>")) {
                locations.add("Prompt injection markers detected");
                severity += 0.4;
            }
            
            boolean detected = severity > 0.4;
            return new FailureDetection(FailureMode.MANIPULATION, detected,
                Math.min(1.0, severity),
                detected ? "Manipulation attempt detected" : "No manipulation detected",
                locations);
        }
        
        private boolean containsEncoding(String content) {
            // Check for base64, hex, or other encodings
            return content.matches(".*[A-Za-z0-9+/]{20,}={0,2}.*") || // Base64-like
                   content.matches(".*[0-9a-fA-F]{16,}.*");           // Hex-like
        }
    }
    
    /**
     * Calculate context metrics
     */
    private ContextMetrics calculateMetrics(String content) {
        try {
            // Tokenize
            String[] tokens = content.toLowerCase().split("\\s+");
            int totalTokens = tokens.length;
            Set<String> uniqueTokens = new HashSet<>(Arrays.asList(tokens));
            int uniqueCount = uniqueTokens.size();
            
            // Calculate entropy
            Map<String, Integer> tokenFreq = new HashMap<>();
            for (String token : tokens) {
                tokenFreq.merge(token, 1, Integer::sum);
            }
            
            double entropy = 0;
            for (int freq : tokenFreq.values()) {
                double p = (double) freq / totalTokens;
                if (p > 0) {
                    entropy -= p * Math.log(p) / Math.log(2);
                }
            }
            entropy = entropy / Math.log(uniqueCount) / Math.log(2); // Normalize
            
            // Estimate perplexity (simplified)
            double perplexity = Math.pow(2, entropy) * uniqueCount / 10;
            
            // Analyze sentiment
            Document doc = Document.newBuilder()
                .setContent(content)
                .setType(Document.Type.PLAIN_TEXT)
                .build();
            
            AnalyzeSentimentResponse sentimentResponse = 
                languageClient.analyzeSentiment(doc);
            double sentimentScore = sentimentResponse.getDocumentSentiment().getScore();
            
            // Analyze entities for topic distribution
            AnalyzeEntitiesResponse entitiesResponse = 
                languageClient.analyzeEntities(doc);
            
            Map<String, Integer> topicDistribution = new HashMap<>();
            for (Entity entity : entitiesResponse.getEntitiesList()) {
                topicDistribution.merge(entity.getType().name(), 1, Integer::sum);
            }
            
            // Estimate toxicity (would use Perspective API in production)
            double toxicityScore = estimateToxicity(content);
            
            return new ContextMetrics(totalTokens, uniqueCount, entropy,
                perplexity, topicDistribution, sentimentScore, toxicityScore);
            
        } catch (Exception e) {
            // Return default metrics on error
            return new ContextMetrics(0, 0, 0.5, 50, new HashMap<>(), 0, 0);
        }
    }
    
    /**
     * Calculate overall quality score
     */
    private double calculateQualityScore(Map<FailureMode, FailureDetection> detections,
                                       ContextMetrics metrics) {
        double totalSeverity = 0;
        double totalWeight = 0;
        
        // Weight different failure modes
        Map<FailureMode, Double> weights = Map.of(
            FailureMode.POISONING, 2.0,
            FailureMode.MANIPULATION, 2.0,
            FailureMode.BIAS, 1.5,
            FailureMode.CONFUSION, 1.0,
            FailureMode.CLASH, 1.0,
            FailureMode.INCOHERENCE, 1.0,
            FailureMode.DISTRACTION, 0.5,
            FailureMode.REPETITION, 0.5
        );
        
        for (Map.Entry<FailureMode, FailureDetection> entry : detections.entrySet()) {
            double weight = weights.getOrDefault(entry.getKey(), 1.0);
            totalSeverity += entry.getValue().getSeverity() * weight;
            totalWeight += weight;
        }
        
        // Calculate base quality (1 - weighted severity)
        double baseQuality = 1.0 - (totalSeverity / totalWeight);
        
        // Apply metric adjustments
        if (metrics.getEntropy() > 0.9 || metrics.getEntropy() < 0.1) {
            baseQuality *= 0.9;
        }
        
        if (metrics.getToxicityScore() > 0.3) {
            baseQuality *= (1.0 - metrics.getToxicityScore());
        }
        
        return Math.max(0, Math.min(1.0, baseQuality));
    }
    
    /**
     * Generate recommendations based on detections
     */
    private List<String> generateRecommendations(Map<FailureMode, FailureDetection> detections,
                                                ContextMetrics metrics) {
        List<String> recommendations = new ArrayList<>();
        
        for (Map.Entry<FailureMode, FailureDetection> entry : detections.entrySet()) {
            if (!entry.getValue().isDetected()) continue;
            
            switch (entry.getKey()) {
                case POISONING:
                    recommendations.add("Verify factual claims with authoritative sources");
                    recommendations.add("Reduce use of absolute statements and superlatives");
                    break;
                case DISTRACTION:
                    recommendations.add("Focus on the main topic and remove tangential content");
                    recommendations.add("Organize content with clear structure");
                    break;
                case CONFUSION:
                    recommendations.add("Resolve contradictory statements");
                    recommendations.add("Ensure consistency throughout the context");
                    break;
                case CLASH:
                    recommendations.add("Reconcile conflicting instructions");
                    recommendations.add("Establish clear priority for requirements");
                    break;
                case REPETITION:
                    recommendations.add("Reduce repetitive content");
                    recommendations.add("Use synonyms and varied expressions");
                    break;
                case INCOHERENCE:
                    recommendations.add("Improve logical flow and structure");
                    recommendations.add("Ensure sentences are complete and meaningful");
                    break;
                case BIAS:
                    recommendations.add("Review content for harmful biases");
                    recommendations.add("Ensure balanced and fair representation");
                    break;
                case MANIPULATION:
                    recommendations.add("Remove manipulation attempts");
                    recommendations.add("Maintain clear boundaries and guidelines");
                    break;
            }
        }
        
        // Add general recommendations based on metrics
        if (metrics.getPerplexity() > 100) {
            recommendations.add("Simplify complex or unclear passages");
        }
        
        if (metrics.getEntropy() > 0.8) {
            recommendations.add("Improve content coherence and reduce randomness");
        }
        
        return recommendations;
    }
    
    /**
     * Estimate toxicity score (simplified)
     */
    private double estimateToxicity(String content) {
        // In production, use Perspective API or similar
        String lower = content.toLowerCase();
        double score = 0;
        
        // Simple keyword-based estimation
        String[] toxicIndicators = {
            "hate", "kill", "stupid", "idiot", "damn", "hell"
        };
        
        for (String indicator : toxicIndicators) {
            if (lower.contains(indicator)) {
                score += 0.2;
            }
        }
        
        return Math.min(1.0, score);
    }
    
    /**
     * Get historical metrics for a context
     */
    public ContextMetrics getHistoricalMetrics(String contextId) {
        return contextMetrics.get(contextId);
    }
    
    /**
     * Clear old metrics
     */
    public void clearOldMetrics(long maxAgeMillis) {
        // In production, track timestamps and clean accordingly
        long cutoff = System.currentTimeMillis() - maxAgeMillis;
        // Implementation would remove old entries
    }
    
    public void shutdown() {
        try {
            languageClient.close();
            translateClient.close();
        } catch (Exception e) {
            System.err.println("Error shutting down clients: " + e.getMessage());
        }
    }
}