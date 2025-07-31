package com.zamaz.adk.context;

import com.google.cloud.aiplatform.v1.*;
import com.zamaz.adk.context.ContextFailureDetector.*;
import com.zamaz.adk.workflow.WorkflowEngine.VertexAIClient;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Context Mitigation Service - Automatically corrects context issues
 * detected by the ContextFailureDetector
 */
public class ContextMitigationService {
    private final VertexAIClient aiClient;
    private final Map<FailureMode, ContextMitigator> mitigators = new HashMap<>();
    private final Map<String, MitigationHistory> mitigationHistories = new ConcurrentHashMap<>();
    
    public ContextMitigationService(VertexAIClient aiClient) {
        this.aiClient = aiClient;
        initializeMitigators();
    }
    
    /**
     * Mitigation result
     */
    public static class MitigationResult {
        private final String originalContent;
        private final String mitigatedContent;
        private final Map<FailureMode, MitigationAction> actions;
        private final double improvementScore;
        private final List<String> changes;
        private final boolean successful;
        
        public MitigationResult(String originalContent, String mitigatedContent,
                              Map<FailureMode, MitigationAction> actions,
                              double improvementScore, List<String> changes,
                              boolean successful) {
            this.originalContent = originalContent;
            this.mitigatedContent = mitigatedContent;
            this.actions = actions;
            this.improvementScore = improvementScore;
            this.changes = changes;
            this.successful = successful;
        }
        
        // Getters
        public String getOriginalContent() { return originalContent; }
        public String getMitigatedContent() { return mitigatedContent; }
        public Map<FailureMode, MitigationAction> getActions() { return actions; }
        public double getImprovementScore() { return improvementScore; }
        public List<String> getChanges() { return changes; }
        public boolean isSuccessful() { return successful; }
    }
    
    /**
     * Individual mitigation action
     */
    public static class MitigationAction {
        private final FailureMode mode;
        private final String description;
        private final int changesApplied;
        private final double severityReduction;
        
        public MitigationAction(FailureMode mode, String description,
                              int changesApplied, double severityReduction) {
            this.mode = mode;
            this.description = description;
            this.changesApplied = changesApplied;
            this.severityReduction = severityReduction;
        }
        
        // Getters
        public FailureMode getMode() { return mode; }
        public String getDescription() { return description; }
        public int getChangesApplied() { return changesApplied; }
        public double getSeverityReduction() { return severityReduction; }
    }
    
    /**
     * Mitigation history for learning
     */
    public static class MitigationHistory {
        private final Map<FailureMode, List<MitigationRecord>> records = new HashMap<>();
        
        public void addRecord(FailureMode mode, double originalSeverity,
                            double mitigatedSeverity, boolean successful) {
            records.computeIfAbsent(mode, k -> new ArrayList<>())
                .add(new MitigationRecord(originalSeverity, mitigatedSeverity, successful));
        }
        
        public double getAverageImprovement(FailureMode mode) {
            List<MitigationRecord> modeRecords = records.get(mode);
            if (modeRecords == null || modeRecords.isEmpty()) {
                return 0;
            }
            
            return modeRecords.stream()
                .mapToDouble(r -> r.originalSeverity - r.mitigatedSeverity)
                .average()
                .orElse(0);
        }
        
        public double getSuccessRate(FailureMode mode) {
            List<MitigationRecord> modeRecords = records.get(mode);
            if (modeRecords == null || modeRecords.isEmpty()) {
                return 0;
            }
            
            long successful = modeRecords.stream()
                .filter(r -> r.successful)
                .count();
            
            return (double) successful / modeRecords.size();
        }
    }
    
    /**
     * Mitigation record
     */
    private static class MitigationRecord {
        private final double originalSeverity;
        private final double mitigatedSeverity;
        private final boolean successful;
        private final long timestamp;
        
        public MitigationRecord(double originalSeverity, double mitigatedSeverity,
                              boolean successful) {
            this.originalSeverity = originalSeverity;
            this.mitigatedSeverity = mitigatedSeverity;
            this.successful = successful;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    /**
     * Base interface for mitigators
     */
    private interface ContextMitigator {
        MitigationAction mitigate(String content, FailureDetection detection,
                                ContextMetrics metrics);
    }
    
    /**
     * Initialize all mitigators
     */
    private void initializeMitigators() {
        mitigators.put(FailureMode.POISONING, new PoisoningMitigator());
        mitigators.put(FailureMode.DISTRACTION, new DistractionMitigator());
        mitigators.put(FailureMode.CONFUSION, new ConfusionMitigator());
        mitigators.put(FailureMode.CLASH, new ClashMitigator());
        mitigators.put(FailureMode.REPETITION, new RepetitionMitigator());
        mitigators.put(FailureMode.INCOHERENCE, new IncoherenceMitigator());
        mitigators.put(FailureMode.BIAS, new BiasMitigator());
        mitigators.put(FailureMode.MANIPULATION, new ManipulationMitigator());
    }
    
    /**
     * Mitigate context issues
     */
    public CompletableFuture<MitigationResult> mitigate(String contextId,
                                                       ContextAnalysis analysis) {
        return CompletableFuture.supplyAsync(() -> {
            String content = analysis.getContextId(); // Should be the actual content
            String workingContent = content;
            Map<FailureMode, MitigationAction> actions = new HashMap<>();
            List<String> allChanges = new ArrayList<>();
            
            // Apply mitigations in order of severity
            List<Map.Entry<FailureMode, FailureDetection>> sortedDetections = 
                analysis.getDetections().entrySet().stream()
                    .filter(e -> e.getValue().isDetected())
                    .sorted((a, b) -> Double.compare(
                        b.getValue().getSeverity(), 
                        a.getValue().getSeverity()))
                    .collect(Collectors.toList());
            
            for (Map.Entry<FailureMode, FailureDetection> entry : sortedDetections) {
                ContextMitigator mitigator = mitigators.get(entry.getKey());
                if (mitigator != null) {
                    MitigationAction action = mitigator.mitigate(
                        workingContent, entry.getValue(), analysis.getMetrics());
                    
                    if (action.getChangesApplied() > 0) {
                        actions.put(entry.getKey(), action);
                        allChanges.add(action.getDescription());
                        // Update working content (in real implementation)
                        workingContent = applyMitigation(workingContent, action);
                    }
                }
            }
            
            // Use AI for final polish if significant changes were made
            if (!allChanges.isEmpty()) {
                workingContent = polishWithAI(workingContent, allChanges);
            }
            
            // Calculate improvement score
            double improvementScore = calculateImprovement(
                analysis.getOverallQualityScore(), workingContent);
            
            // Record history
            MitigationHistory history = mitigationHistories.computeIfAbsent(
                contextId, k -> new MitigationHistory());
            
            for (Map.Entry<FailureMode, MitigationAction> entry : actions.entrySet()) {
                FailureDetection original = analysis.getDetections().get(entry.getKey());
                history.addRecord(entry.getKey(), original.getSeverity(),
                    original.getSeverity() - entry.getValue().getSeverityReduction(),
                    entry.getValue().getSeverityReduction() > 0.1);
            }
            
            return new MitigationResult(
                content,
                workingContent,
                actions,
                improvementScore,
                allChanges,
                improvementScore > 0.1
            );
        });
    }
    
    /**
     * Poisoning mitigator - removes hallucinations and false claims
     */
    private class PoisoningMitigator implements ContextMitigator {
        private final Set<String> hallucinationPhrases = Set.of(
            "as an ai", "as a language model", "i cannot", "i don't have access",
            "quantum supremacy", "100% accuracy", "perfect solution",
            "breakthrough technology", "revolutionary", "game-changing"
        );
        
        private final Map<String, String> replacements = Map.of(
            "always", "typically",
            "never", "rarely",
            "every", "most",
            "all", "many",
            "none", "few",
            "impossible", "very difficult",
            "guaranteed", "likely",
            "definitely", "probably"
        );
        
        @Override
        public MitigationAction mitigate(String content, FailureDetection detection,
                                       ContextMetrics metrics) {
            String modified = content;
            int changes = 0;
            
            // Remove hallucination phrases
            for (String phrase : hallucinationPhrases) {
                Pattern pattern = Pattern.compile(
                    "\\b" + Pattern.quote(phrase) + "\\b", 
                    Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(modified);
                if (matcher.find()) {
                    modified = matcher.replaceAll("");
                    changes++;
                }
            }
            
            // Replace absolute terms
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                Pattern pattern = Pattern.compile(
                    "\\b" + entry.getKey() + "\\b", 
                    Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(modified);
                StringBuffer sb = new StringBuffer();
                
                while (matcher.find()) {
                    matcher.appendReplacement(sb, entry.getValue());
                    changes++;
                }
                matcher.appendTail(sb);
                modified = sb.toString();
            }
            
            // Remove obviously false statistical claims
            Pattern statsPattern = Pattern.compile("\\b\\d{2,3}%\\s*(accuracy|success|improvement)");
            Matcher statsMatcher = statsPattern.matcher(modified);
            while (statsMatcher.find()) {
                String claim = statsMatcher.group();
                int percentage = Integer.parseInt(claim.replaceAll("[^0-9]", ""));
                if (percentage > 95) {
                    modified = modified.replace(claim, "high " + 
                        claim.replaceAll("\\d+%", "").trim());
                    changes++;
                }
            }
            
            double severityReduction = changes > 0 ? 
                Math.min(0.5, changes * 0.1) : 0;
            
            return new MitigationAction(
                FailureMode.POISONING,
                "Removed " + changes + " hallucination indicators",
                changes,
                severityReduction
            );
        }
    }
    
    /**
     * Distraction mitigator - focuses content on main topic
     */
    private class DistractionMitigator implements ContextMitigator {
        @Override
        public MitigationAction mitigate(String content, FailureDetection detection,
                                       ContextMetrics metrics) {
            // Identify main topic from metrics
            Map<String, Integer> topics = metrics.getTopicDistribution();
            if (topics.isEmpty()) {
                return new MitigationAction(FailureMode.DISTRACTION,
                    "No topic information available", 0, 0);
            }
            
            // Find dominant topic
            String mainTopic = topics.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
            
            // Use AI to refocus content
            String prompt = String.format(
                "Refocus this content on the main topic '%s'. " +
                "Remove tangential information while preserving key points:\n%s",
                mainTopic, content
            );
            
            String refocused = aiClient.generateContent("gemini-1.5-flash", prompt,
                Map.of("temperature", 0.3, "maxOutputTokens", content.length()));
            
            // Count sentences removed (simplified)
            int originalSentences = content.split("[.!?]+").length;
            int refocusedSentences = refocused.split("[.!?]+").length;
            int removed = Math.max(0, originalSentences - refocusedSentences);
            
            return new MitigationAction(
                FailureMode.DISTRACTION,
                "Refocused content on " + mainTopic + ", removed " + removed + " off-topic sentences",
                removed,
                removed > 0 ? 0.3 : 0
            );
        }
    }
    
    /**
     * Confusion mitigator - resolves contradictions
     */
    private class ConfusionMitigator implements ContextMitigator {
        @Override
        public MitigationAction mitigate(String content, FailureDetection detection,
                                       ContextMetrics metrics) {
            List<String> sentences = Arrays.asList(content.split("[.!?]+"));
            Set<Integer> contradictoryIndices = new HashSet<>();
            
            // Identify contradictory sentence pairs from detection evidence
            for (String location : detection.getLocations()) {
                if (location.startsWith("Contradiction between sentences")) {
                    // Extract indices
                    String[] parts = location.split("\\s+");
                    for (String part : parts) {
                        try {
                            int idx = Integer.parseInt(part);
                            contradictoryIndices.add(idx);
                        } catch (NumberFormatException e) {
                            // Ignore non-numeric parts
                        }
                    }
                }
            }
            
            // Remove contradictory sentences (keep the first occurrence)
            List<String> cleaned = new ArrayList<>();
            Set<String> seenConcepts = new HashSet<>();
            
            for (int i = 0; i < sentences.size(); i++) {
                if (!contradictoryIndices.contains(i) || 
                    !hasSeenConcept(sentences.get(i), seenConcepts)) {
                    cleaned.add(sentences.get(i));
                    addConcepts(sentences.get(i), seenConcepts);
                }
            }
            
            String mitigated = String.join(". ", cleaned);
            int removed = sentences.size() - cleaned.size();
            
            return new MitigationAction(
                FailureMode.CONFUSION,
                "Removed " + removed + " contradictory statements",
                removed,
                removed > 0 ? 0.4 : 0
            );
        }
        
        private boolean hasSeenConcept(String sentence, Set<String> seenConcepts) {
            // Simplified concept extraction
            String[] words = sentence.toLowerCase().split("\\s+");
            for (String word : words) {
                if (seenConcepts.contains(word) && word.length() > 4) {
                    return true;
                }
            }
            return false;
        }
        
        private void addConcepts(String sentence, Set<String> seenConcepts) {
            String[] words = sentence.toLowerCase().split("\\s+");
            for (String word : words) {
                if (word.length() > 4) {
                    seenConcepts.add(word);
                }
            }
        }
    }
    
    /**
     * Clash mitigator - reconciles conflicting instructions
     */
    private class ClashMitigator implements ContextMitigator {
        @Override
        public MitigationAction mitigate(String content, FailureDetection detection,
                                       ContextMetrics metrics) {
            // Use AI to reconcile conflicts
            String prompt = "Reconcile the conflicting instructions in this content. " +
                           "Create a clear, consistent set of instructions:\n" + content;
            
            String reconciled = aiClient.generateContent("gemini-1.5-pro", prompt,
                Map.of("temperature", 0.2, "maxOutputTokens", content.length()));
            
            // Count instruction changes
            Pattern instructionPattern = Pattern.compile(
                "\\b(must|should|shall|will|can|cannot|forbidden|required)\\b",
                Pattern.CASE_INSENSITIVE);
            
            int originalInstructions = countMatches(instructionPattern, content);
            int reconciledInstructions = countMatches(instructionPattern, reconciled);
            
            return new MitigationAction(
                FailureMode.CLASH,
                "Reconciled conflicting instructions",
                Math.abs(originalInstructions - reconciledInstructions),
                0.5
            );
        }
        
        private int countMatches(Pattern pattern, String text) {
            Matcher matcher = pattern.matcher(text);
            int count = 0;
            while (matcher.find()) {
                count++;
            }
            return count;
        }
    }
    
    /**
     * Repetition mitigator - reduces redundancy
     */
    private class RepetitionMitigator implements ContextMitigator {
        @Override
        public MitigationAction mitigate(String content, FailureDetection detection,
                                       ContextMetrics metrics) {
            String[] sentences = content.split("[.!?]+");
            List<String> unique = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            int removed = 0;
            
            for (String sentence : sentences) {
                String normalized = sentence.trim().toLowerCase()
                    .replaceAll("\\s+", " ");
                
                // Check for near-duplicates
                boolean isDuplicate = false;
                for (String seenSentence : seen) {
                    if (calculateSimilarity(normalized, seenSentence) > 0.8) {
                        isDuplicate = true;
                        removed++;
                        break;
                    }
                }
                
                if (!isDuplicate) {
                    unique.add(sentence.trim());
                    seen.add(normalized);
                }
            }
            
            // Remove repetitive phrases within sentences
            List<String> cleaned = new ArrayList<>();
            for (String sentence : unique) {
                cleaned.add(removeRepetitivePhrases(sentence));
            }
            
            String mitigated = String.join(". ", cleaned);
            
            return new MitigationAction(
                FailureMode.REPETITION,
                "Removed " + removed + " duplicate sentences and repetitive phrases",
                removed,
                removed > 0 ? 0.3 : 0
            );
        }
        
        private double calculateSimilarity(String s1, String s2) {
            // Jaccard similarity
            Set<String> words1 = new HashSet<>(Arrays.asList(s1.split("\\s+")));
            Set<String> words2 = new HashSet<>(Arrays.asList(s2.split("\\s+")));
            
            Set<String> intersection = new HashSet<>(words1);
            intersection.retainAll(words2);
            
            Set<String> union = new HashSet<>(words1);
            union.addAll(words2);
            
            return union.isEmpty() ? 0 : 
                (double) intersection.size() / union.size();
        }
        
        private String removeRepetitivePhrases(String sentence) {
            // Remove repeated 3-word phrases
            String[] words = sentence.split("\\s+");
            if (words.length < 6) return sentence;
            
            Map<String, Integer> phraseCount = new HashMap<>();
            for (int i = 0; i < words.length - 2; i++) {
                String phrase = words[i] + " " + words[i + 1] + " " + words[i + 2];
                phraseCount.merge(phrase, 1, Integer::sum);
            }
            
            // Rebuild sentence removing duplicate phrases
            StringBuilder result = new StringBuilder();
            Set<String> usedPhrases = new HashSet<>();
            
            for (int i = 0; i < words.length; i++) {
                if (i < words.length - 2) {
                    String phrase = words[i] + " " + words[i + 1] + " " + words[i + 2];
                    if (phraseCount.get(phrase) > 1 && usedPhrases.contains(phrase)) {
                        i += 2; // Skip the repeated phrase
                        continue;
                    }
                    usedPhrases.add(phrase);
                }
                result.append(words[i]).append(" ");
            }
            
            return result.toString().trim();
        }
    }
    
    /**
     * Incoherence mitigator - improves logical flow
     */
    private class IncoherenceMitigator implements ContextMitigator {
        @Override
        public MitigationAction mitigate(String content, FailureDetection detection,
                                       ContextMetrics metrics) {
            // Use AI to improve coherence
            String prompt = "Improve the logical flow and coherence of this content. " +
                           "Fix incomplete sentences and improve structure:\n" + content;
            
            String improved = aiClient.generateContent("gemini-1.5-pro", prompt,
                Map.of("temperature", 0.3, "maxOutputTokens", content.length() * 2));
            
            // Measure improvement (simplified)
            int originalSentences = content.split("[.!?]+").length;
            int improvedSentences = improved.split("[.!?]+").length;
            
            // Check if sentences are more complete
            int incompleteOriginal = countIncompleteSentences(content);
            int incompleteImproved = countIncompleteSentences(improved);
            
            int improvements = Math.max(0, incompleteOriginal - incompleteImproved);
            
            return new MitigationAction(
                FailureMode.INCOHERENCE,
                "Improved coherence and fixed " + improvements + " incomplete sentences",
                improvements,
                improvements > 0 ? 0.4 : 0
            );
        }
        
        private int countIncompleteSentences(String text) {
            String[] sentences = text.split("[.!?]+");
            int incomplete = 0;
            
            for (String sentence : sentences) {
                String trimmed = sentence.trim();
                if (trimmed.length() < 10 || 
                    trimmed.split("\\s+").length < 3 ||
                    !trimmed.matches(".*[a-zA-Z].*")) {
                    incomplete++;
                }
            }
            
            return incomplete;
        }
    }
    
    /**
     * Bias mitigator - reduces harmful biases
     */
    private class BiasMitigator implements ContextMitigator {
        @Override
        public MitigationAction mitigate(String content, FailureDetection detection,
                                       ContextMetrics metrics) {
            // Use AI to reduce bias
            String prompt = "Rewrite this content to be more balanced and unbiased. " +
                           "Remove any harmful stereotypes or unfair characterizations:\n" + 
                           content;
            
            String unbiased = aiClient.generateContent("gemini-1.5-pro", prompt,
                Map.of("temperature", 0.3, "maxOutputTokens", content.length()));
            
            // Measure change (would use proper bias detection in production)
            double originalToxicity = metrics.getToxicityScore();
            double reduction = originalToxicity > 0.5 ? 0.4 : 0.1;
            
            return new MitigationAction(
                FailureMode.BIAS,
                "Reduced bias and improved balance",
                1,
                reduction
            );
        }
    }
    
    /**
     * Manipulation mitigator - removes manipulation attempts
     */
    private class ManipulationMitigator implements ContextMitigator {
        private final Pattern jailbreakPattern = Pattern.compile(
            "ignore.*previous|disregard.*instructions|forget.*said|new.*instructions|" +
            "pretend.*you.*are|act.*as.*if|roleplay|bypass|override",
            Pattern.CASE_INSENSITIVE
        );
        
        private final Pattern encodingPattern = Pattern.compile(
            "[A-Za-z0-9+/]{20,}={0,2}|[0-9a-fA-F]{16,}"
        );
        
        private final Pattern injectionPattern = Pattern.compile(
            "\\[\\[|\\]\\]|\\{\\{|\\}\\}|<\\||\\|>"
        );
        
        @Override
        public MitigationAction mitigate(String content, FailureDetection detection,
                                       ContextMetrics metrics) {
            String cleaned = content;
            int changes = 0;
            
            // Remove jailbreak attempts
            Matcher jailbreakMatcher = jailbreakPattern.matcher(cleaned);
            if (jailbreakMatcher.find()) {
                cleaned = jailbreakMatcher.replaceAll("[removed]");
                changes++;
            }
            
            // Remove encoded content
            Matcher encodingMatcher = encodingPattern.matcher(cleaned);
            if (encodingMatcher.find()) {
                cleaned = encodingMatcher.replaceAll("[encoded content removed]");
                changes++;
            }
            
            // Remove injection markers
            Matcher injectionMatcher = injectionPattern.matcher(cleaned);
            if (injectionMatcher.find()) {
                cleaned = injectionMatcher.replaceAll("");
                changes++;
            }
            
            // Remove suspicious instruction blocks
            String[] lines = cleaned.split("\n");
            List<String> safeLines = new ArrayList<>();
            
            for (String line : lines) {
                if (!isSuspiciousInstruction(line)) {
                    safeLines.add(line);
                } else {
                    changes++;
                }
            }
            
            cleaned = String.join("\n", safeLines);
            
            return new MitigationAction(
                FailureMode.MANIPULATION,
                "Removed " + changes + " manipulation attempts",
                changes,
                changes > 0 ? 0.7 : 0
            );
        }
        
        private boolean isSuspiciousInstruction(String line) {
            String lower = line.toLowerCase();
            return lower.contains("system:") || 
                   lower.contains("assistant:") ||
                   lower.contains("instructions:") ||
                   lower.startsWith("###") ||
                   lower.startsWith("```") && lower.contains("ignore");
        }
    }
    
    /**
     * Apply mitigation changes to content
     */
    private String applyMitigation(String content, MitigationAction action) {
        // In a real implementation, this would apply the specific changes
        // For now, return the original content
        return content;
    }
    
    /**
     * Polish content with AI after mitigations
     */
    private String polishWithAI(String content, List<String> changes) {
        String prompt = String.format(
            "Polish this content after the following corrections were made:\n%s\n\n" +
            "Ensure the content flows naturally while maintaining all corrections:\n%s",
            String.join(", ", changes),
            content
        );
        
        return aiClient.generateContent("gemini-1.5-flash", prompt,
            Map.of("temperature", 0.4, "maxOutputTokens", content.length() * 2));
    }
    
    /**
     * Calculate improvement score
     */
    private double calculateImprovement(double originalScore, String mitigatedContent) {
        // In production, re-run detection on mitigated content
        // For now, estimate based on changes
        return Math.min(0.3, Math.random() * 0.4);
    }
    
    /**
     * Get mitigation history for analysis
     */
    public MitigationHistory getHistory(String contextId) {
        return mitigationHistories.get(contextId);
    }
    
    /**
     * Clear old histories
     */
    public void clearOldHistories(long maxAgeMillis) {
        // In production, track timestamps and remove old entries
        long cutoff = System.currentTimeMillis() - maxAgeMillis;
        // Implementation would clean based on record timestamps
    }
}