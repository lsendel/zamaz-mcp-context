package com.zamaz.adk.workflow;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.pubsub.v1.*;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Workflow Streaming Service - Provides real-time updates for workflow execution
 * Supports both Server-Sent Events (SSE) and WebSocket protocols
 */
public class WorkflowStreamingService {
    private final Map<String, WorkflowStream> activeStreams = new ConcurrentHashMap<>();
    private final Map<String, List<SseEmitter>> sseEmitters = new ConcurrentHashMap<>();
    private final Publisher eventPublisher;
    private final Subscriber eventSubscriber;
    private final Firestore firestore;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    // For reactive streams
    private final Map<String, Sinks.Many<WorkflowEvent>> workflowSinks = new ConcurrentHashMap<>();
    
    public WorkflowStreamingService(String projectId, String topicName, 
                                  String subscriptionName, Firestore firestore) {
        this.firestore = firestore;
        
        try {
            // Create publisher for sending events
            this.eventPublisher = Publisher.newBuilder(
                TopicName.of(projectId, topicName)).build();
            
            // Create subscriber for receiving events
            ProjectSubscriptionName subscriptionNameObj = 
                ProjectSubscriptionName.of(projectId, subscriptionName);
            
            MessageReceiver receiver = (PubsubMessage message, AckReplyConsumer consumer) -> {
                handleIncomingEvent(message.getData().toStringUtf8());
                consumer.ack();
            };
            
            this.eventSubscriber = Subscriber.newBuilder(subscriptionNameObj, receiver).build();
            this.eventSubscriber.startAsync().awaitRunning();
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize streaming service", e);
        }
        
        // Schedule cleanup of inactive streams
        scheduler.scheduleAtFixedRate(this::cleanupInactiveStreams, 
            30, 30, TimeUnit.SECONDS);
    }
    
    /**
     * Workflow execution event
     */
    public static class WorkflowEvent {
        private final String workflowId;
        private final String executionId;
        private final String nodeId;
        private final EventType type;
        private final Map<String, Object> data;
        private final long timestamp;
        private final String tenantPath;
        
        public enum EventType {
            WORKFLOW_STARTED,
            NODE_STARTED,
            NODE_COMPLETED,
            NODE_FAILED,
            EDGE_TRAVERSED,
            STATE_UPDATED,
            WORKFLOW_COMPLETED,
            WORKFLOW_FAILED,
            CHECKPOINT_SAVED,
            BACKTRACK_INITIATED,
            PARALLEL_BRANCH_STARTED,
            PARALLEL_BRANCH_COMPLETED,
            CONTEXT_WARNING,
            MEMORY_UPDATED
        }
        
        public WorkflowEvent(String workflowId, String executionId, String nodeId,
                           EventType type, Map<String, Object> data, String tenantPath) {
            this.workflowId = workflowId;
            this.executionId = executionId;
            this.nodeId = nodeId;
            this.type = type;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
            this.tenantPath = tenantPath;
        }
        
        // Getters
        public String getWorkflowId() { return workflowId; }
        public String getExecutionId() { return executionId; }
        public String getNodeId() { return nodeId; }
        public EventType getType() { return type; }
        public Map<String, Object> getData() { return data; }
        public long getTimestamp() { return timestamp; }
        public String getTenantPath() { return tenantPath; }
        
        public String toJson() {
            // Convert to JSON for transmission
            return String.format(
                "{\"workflowId\":\"%s\",\"executionId\":\"%s\",\"nodeId\":\"%s\"," +
                "\"type\":\"%s\",\"timestamp\":%d,\"data\":%s}",
                workflowId, executionId, nodeId, type.name(), timestamp,
                dataToJson(data)
            );
        }
        
        private String dataToJson(Map<String, Object> data) {
            // Simple JSON conversion - in production use Jackson
            StringBuilder json = new StringBuilder("{");
            data.forEach((k, v) -> {
                if (json.length() > 1) json.append(",");
                json.append("\"").append(k).append("\":\"").append(v).append("\"");
            });
            json.append("}");
            return json.toString();
        }
    }
    
    /**
     * Workflow execution stream
     */
    public static class WorkflowStream {
        private final String streamId;
        private final String workflowId;
        private final String executionId;
        private final List<WorkflowEvent> events = new ArrayList<>();
        private final long startTime;
        private long lastEventTime;
        private boolean isActive = true;
        
        public WorkflowStream(String workflowId, String executionId) {
            this.streamId = UUID.randomUUID().toString();
            this.workflowId = workflowId;
            this.executionId = executionId;
            this.startTime = System.currentTimeMillis();
            this.lastEventTime = startTime;
        }
        
        public void addEvent(WorkflowEvent event) {
            events.add(event);
            lastEventTime = System.currentTimeMillis();
        }
        
        public boolean isStale() {
            return System.currentTimeMillis() - lastEventTime > 300000; // 5 minutes
        }
        
        public void complete() {
            isActive = false;
        }
        
        // Getters
        public String getStreamId() { return streamId; }
        public String getWorkflowId() { return workflowId; }
        public String getExecutionId() { return executionId; }
        public List<WorkflowEvent> getEvents() { return new ArrayList<>(events); }
        public boolean isActive() { return isActive; }
    }
    
    /**
     * Start streaming for a workflow execution
     */
    public WorkflowStream startStream(String workflowId, String executionId, 
                                    String tenantPath) {
        WorkflowStream stream = new WorkflowStream(workflowId, executionId);
        activeStreams.put(executionId, stream);
        
        // Create reactive sink for this workflow
        Sinks.Many<WorkflowEvent> sink = Sinks.many().multicast().onBackpressureBuffer();
        workflowSinks.put(executionId, sink);
        
        // Publish start event
        publishEvent(new WorkflowEvent(
            workflowId, executionId, "start",
            WorkflowEvent.EventType.WORKFLOW_STARTED,
            Map.of("message", "Workflow execution started"),
            tenantPath
        ));
        
        return stream;
    }
    
    /**
     * Publish workflow event
     */
    public void publishEvent(WorkflowEvent event) {
        // Add to stream
        WorkflowStream stream = activeStreams.get(event.getExecutionId());
        if (stream != null) {
            stream.addEvent(event);
        }
        
        // Publish to Pub/Sub
        try {
            PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
                .setData(ByteString.copyFromUtf8(event.toJson()))
                .putAttributes("workflowId", event.getWorkflowId())
                .putAttributes("executionId", event.getExecutionId())
                .putAttributes("eventType", event.getType().name())
                .build();
            
            eventPublisher.publish(pubsubMessage);
        } catch (Exception e) {
            System.err.println("Failed to publish event: " + e.getMessage());
        }
        
        // Emit to reactive stream
        Sinks.Many<WorkflowEvent> sink = workflowSinks.get(event.getExecutionId());
        if (sink != null) {
            sink.tryEmitNext(event);
        }
        
        // Send to SSE emitters
        List<SseEmitter> emitters = sseEmitters.get(event.getExecutionId());
        if (emitters != null) {
            List<SseEmitter> deadEmitters = new ArrayList<>();
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                        .name(event.getType().name())
                        .data(event.toJson())
                        .id(String.valueOf(event.getTimestamp())));
                } catch (IOException e) {
                    deadEmitters.add(emitter);
                }
            }
            emitters.removeAll(deadEmitters);
        }
        
        // Store in Firestore for replay capability
        storeEventForReplay(event);
    }
    
    /**
     * Get reactive stream for workflow
     */
    public Flux<WorkflowEvent> getEventStream(String executionId) {
        Sinks.Many<WorkflowEvent> sink = workflowSinks.get(executionId);
        if (sink == null) {
            return Flux.empty();
        }
        
        return sink.asFlux()
            .doOnCancel(() -> System.out.println("Stream cancelled: " + executionId))
            .doOnComplete(() -> System.out.println("Stream completed: " + executionId));
    }
    
    /**
     * Create SSE emitter for workflow
     */
    public SseEmitter createSseEmitter(String executionId) {
        SseEmitter emitter = new SseEmitter(300000L); // 5 minute timeout
        
        sseEmitters.computeIfAbsent(executionId, k -> new ArrayList<>()).add(emitter);
        
        emitter.onCompletion(() -> removeSseEmitter(executionId, emitter));
        emitter.onTimeout(() -> removeSseEmitter(executionId, emitter));
        emitter.onError(e -> removeSseEmitter(executionId, emitter));
        
        // Send initial events if any
        WorkflowStream stream = activeStreams.get(executionId);
        if (stream != null) {
            stream.getEvents().forEach(event -> {
                try {
                    emitter.send(SseEmitter.event()
                        .name(event.getType().name())
                        .data(event.toJson())
                        .id(String.valueOf(event.getTimestamp())));
                } catch (IOException e) {
                    // Ignore - emitter might be closed
                }
            });
        }
        
        return emitter;
    }
    
    /**
     * Get execution progress
     */
    public ExecutionProgress getProgress(String executionId) {
        WorkflowStream stream = activeStreams.get(executionId);
        if (stream == null) {
            return null;
        }
        
        List<WorkflowEvent> events = stream.getEvents();
        
        // Calculate progress based on events
        long totalNodes = events.stream()
            .filter(e -> e.getType() == WorkflowEvent.EventType.NODE_STARTED)
            .map(WorkflowEvent::getNodeId)
            .distinct()
            .count();
        
        long completedNodes = events.stream()
            .filter(e -> e.getType() == WorkflowEvent.EventType.NODE_COMPLETED)
            .map(WorkflowEvent::getNodeId)
            .distinct()
            .count();
        
        WorkflowEvent lastEvent = events.isEmpty() ? null : 
            events.get(events.size() - 1);
        
        return new ExecutionProgress(
            executionId,
            stream.getWorkflowId(),
            completedNodes,
            totalNodes,
            stream.isActive() ? "RUNNING" : "COMPLETED",
            lastEvent != null ? lastEvent.getNodeId() : null,
            stream.startTime,
            calculateEstimatedCompletion(stream)
        );
    }
    
    /**
     * Store event for replay capability
     */
    private void storeEventForReplay(WorkflowEvent event) {
        String path = String.format("workflow_events/%s/%s_%d",
            event.getExecutionId(),
            event.getType().name(),
            event.getTimestamp());
        
        firestore.document(path).set(event);
    }
    
    /**
     * Replay workflow execution
     */
    public List<WorkflowEvent> replayExecution(String executionId) {
        try {
            return firestore.collection("workflow_events")
                .document(executionId)
                .collection("events")
                .orderBy("timestamp")
                .get()
                .get()
                .getDocuments()
                .stream()
                .map(doc -> doc.toObject(WorkflowEvent.class))
                .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("Failed to replay execution: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Handle incoming events from Pub/Sub
     */
    private void handleIncomingEvent(String eventJson) {
        // Parse and process event
        // In production, use proper JSON parsing
        System.out.println("Received event: " + eventJson);
    }
    
    /**
     * Remove SSE emitter
     */
    private void removeSseEmitter(String executionId, SseEmitter emitter) {
        List<SseEmitter> emitters = sseEmitters.get(executionId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                sseEmitters.remove(executionId);
            }
        }
    }
    
    /**
     * Cleanup inactive streams
     */
    private void cleanupInactiveStreams() {
        List<String> toRemove = new ArrayList<>();
        
        activeStreams.forEach((id, stream) -> {
            if (stream.isStale() || !stream.isActive()) {
                toRemove.add(id);
            }
        });
        
        toRemove.forEach(id -> {
            activeStreams.remove(id);
            workflowSinks.remove(id);
            sseEmitters.remove(id);
        });
    }
    
    /**
     * Calculate estimated completion time
     */
    private long calculateEstimatedCompletion(WorkflowStream stream) {
        if (!stream.isActive()) {
            return stream.lastEventTime;
        }
        
        List<WorkflowEvent> events = stream.getEvents();
        if (events.size() < 2) {
            return -1; // Not enough data
        }
        
        // Simple linear estimation based on average node completion time
        long totalTime = stream.lastEventTime - stream.startTime;
        long completedNodes = events.stream()
            .filter(e -> e.getType() == WorkflowEvent.EventType.NODE_COMPLETED)
            .count();
        
        if (completedNodes == 0) {
            return -1;
        }
        
        long avgTimePerNode = totalTime / completedNodes;
        long remainingNodes = events.stream()
            .filter(e -> e.getType() == WorkflowEvent.EventType.NODE_STARTED)
            .count() - completedNodes;
        
        return System.currentTimeMillis() + (avgTimePerNode * remainingNodes);
    }
    
    /**
     * Execution progress information
     */
    public static class ExecutionProgress {
        private final String executionId;
        private final String workflowId;
        private final long completedNodes;
        private final long totalNodes;
        private final String status;
        private final String currentNode;
        private final long startTime;
        private final long estimatedCompletion;
        
        public ExecutionProgress(String executionId, String workflowId,
                               long completedNodes, long totalNodes,
                               String status, String currentNode,
                               long startTime, long estimatedCompletion) {
            this.executionId = executionId;
            this.workflowId = workflowId;
            this.completedNodes = completedNodes;
            this.totalNodes = totalNodes;
            this.status = status;
            this.currentNode = currentNode;
            this.startTime = startTime;
            this.estimatedCompletion = estimatedCompletion;
        }
        
        public double getProgressPercentage() {
            return totalNodes > 0 ? (completedNodes * 100.0 / totalNodes) : 0;
        }
        
        // Getters
        public String getExecutionId() { return executionId; }
        public String getWorkflowId() { return workflowId; }
        public long getCompletedNodes() { return completedNodes; }
        public long getTotalNodes() { return totalNodes; }
        public String getStatus() { return status; }
        public String getCurrentNode() { return currentNode; }
        public long getStartTime() { return startTime; }
        public long getEstimatedCompletion() { return estimatedCompletion; }
    }
    
    public void shutdown() {
        scheduler.shutdown();
        try {
            eventPublisher.shutdown();
            eventPublisher.awaitTermination(30, TimeUnit.SECONDS);
            eventSubscriber.stopAsync().awaitTerminated();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}