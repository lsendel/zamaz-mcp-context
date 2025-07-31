package com.zamaz.adk.agents;

import com.google.cloud.pubsub.v1.*;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.*;
import com.zamaz.adk.agents.MultiAgentOrchestrator.AgentType;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Agent Communication Bus - Enables direct and broadcast communication between agents
 * Supports synchronous requests, asynchronous messaging, and event broadcasting
 */
public class AgentCommunicationBus {
    private final Map<String, AgentEndpoint> agentEndpoints = new ConcurrentHashMap<>();
    private final Map<String, List<MessageHandler>> topicSubscriptions = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<AgentMessage>> pendingRequests = new ConcurrentHashMap<>();
    private final Publisher publisher;
    private final Subscriber subscriber;
    private final ExecutorService messageExecutor = Executors.newWorkStealingPool();
    private final ScheduledExecutorService timeoutScheduler = Executors.newScheduledThreadPool(1);
    
    // Message configuration
    private static final long DEFAULT_REQUEST_TIMEOUT = 30000; // 30 seconds
    private static final int MAX_MESSAGE_SIZE = 1024 * 1024; // 1MB
    private static final int MAX_RETRY_ATTEMPTS = 3;
    
    public AgentCommunicationBus(String projectId, String topicName, String subscriptionName) {
        try {
            // Create Pub/Sub publisher
            TopicName topic = TopicName.of(projectId, topicName);
            this.publisher = Publisher.newBuilder(topic).build();
            
            // Create subscriber
            ProjectSubscriptionName subscription = 
                ProjectSubscriptionName.of(projectId, subscriptionName);
            
            MessageReceiver receiver = (PubsubMessage message, AckReplyConsumer consumer) -> {
                handleIncomingMessage(message);
                consumer.ack();
            };
            
            this.subscriber = Subscriber.newBuilder(subscription, receiver)
                .setExecutorProvider(InstantiatingExecutorProvider.newBuilder()
                    .setExecutorThreadCount(4)
                    .build())
                .build();
            
            this.subscriber.startAsync().awaitRunning();
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize communication bus", e);
        }
        
        // Schedule timeout checks
        timeoutScheduler.scheduleAtFixedRate(this::checkTimeouts, 
            5, 5, TimeUnit.SECONDS);
    }
    
    /**
     * Agent message
     */
    public static class AgentMessage {
        private final String messageId;
        private final String fromAgent;
        private final String toAgent;
        private final MessageType type;
        private final String topic;
        private final Map<String, Object> payload;
        private final Map<String, String> headers;
        private final long timestamp;
        private final String correlationId;
        private final int priority;
        
        public enum MessageType {
            REQUEST,      // Expects response
            RESPONSE,     // Response to request
            BROADCAST,    // One-to-many
            EVENT,        // Event notification
            COMMAND,      // Direct command
            QUERY         // Information query
        }
        
        public AgentMessage(String fromAgent, String toAgent, MessageType type,
                          String topic, Map<String, Object> payload) {
            this.messageId = UUID.randomUUID().toString();
            this.fromAgent = fromAgent;
            this.toAgent = toAgent;
            this.type = type;
            this.topic = topic;
            this.payload = payload;
            this.headers = new HashMap<>();
            this.timestamp = System.currentTimeMillis();
            this.correlationId = null;
            this.priority = 5; // Default medium priority
        }
        
        // Builder pattern for complex messages
        public static class Builder {
            private String fromAgent;
            private String toAgent;
            private MessageType type;
            private String topic;
            private Map<String, Object> payload = new HashMap<>();
            private Map<String, String> headers = new HashMap<>();
            private String correlationId;
            private int priority = 5;
            
            public Builder from(String agent) {
                this.fromAgent = agent;
                return this;
            }
            
            public Builder to(String agent) {
                this.toAgent = agent;
                return this;
            }
            
            public Builder type(MessageType type) {
                this.type = type;
                return this;
            }
            
            public Builder topic(String topic) {
                this.topic = topic;
                return this;
            }
            
            public Builder payload(Map<String, Object> payload) {
                this.payload = payload;
                return this;
            }
            
            public Builder header(String key, String value) {
                this.headers.put(key, value);
                return this;
            }
            
            public Builder correlationId(String id) {
                this.correlationId = id;
                return this;
            }
            
            public Builder priority(int priority) {
                this.priority = Math.max(1, Math.min(10, priority));
                return this;
            }
            
            public AgentMessage build() {
                AgentMessage message = new AgentMessage(fromAgent, toAgent, type, topic, payload);
                message.headers.putAll(headers);
                if (correlationId != null) {
                    message.headers.put("correlation_id", correlationId);
                }
                message.headers.put("priority", String.valueOf(priority));
                return message;
            }
        }
        
        // Getters
        public String getMessageId() { return messageId; }
        public String getFromAgent() { return fromAgent; }
        public String getToAgent() { return toAgent; }
        public MessageType getType() { return type; }
        public String getTopic() { return topic; }
        public Map<String, Object> getPayload() { return payload; }
        public Map<String, String> getHeaders() { return headers; }
        public long getTimestamp() { return timestamp; }
        public String getCorrelationId() { 
            return headers.getOrDefault("correlation_id", correlationId); 
        }
        public int getPriority() { 
            return Integer.parseInt(headers.getOrDefault("priority", "5")); 
        }
    }
    
    /**
     * Agent endpoint registration
     */
    public static class AgentEndpoint {
        private final String agentId;
        private final AgentType agentType;
        private final Set<String> subscribedTopics = new HashSet<>();
        private final MessageHandler handler;
        private final Map<String, Object> capabilities;
        private volatile boolean active = true;
        private long lastHeartbeat;
        
        public AgentEndpoint(String agentId, AgentType agentType, 
                           MessageHandler handler, Map<String, Object> capabilities) {
            this.agentId = agentId;
            this.agentType = agentType;
            this.handler = handler;
            this.capabilities = capabilities;
            this.lastHeartbeat = System.currentTimeMillis();
        }
        
        public void addSubscription(String topic) {
            subscribedTopics.add(topic);
        }
        
        public void removeSubscription(String topic) {
            subscribedTopics.remove(topic);
        }
        
        public void heartbeat() {
            lastHeartbeat = System.currentTimeMillis();
        }
        
        public boolean isActive() {
            // Consider inactive if no heartbeat for 60 seconds
            return active && (System.currentTimeMillis() - lastHeartbeat < 60000);
        }
        
        public void deactivate() {
            active = false;
        }
        
        // Getters
        public String getAgentId() { return agentId; }
        public AgentType getAgentType() { return agentType; }
        public Set<String> getSubscribedTopics() { return new HashSet<>(subscribedTopics); }
        public MessageHandler getHandler() { return handler; }
        public Map<String, Object> getCapabilities() { return capabilities; }
    }
    
    /**
     * Message handler interface
     */
    public interface MessageHandler {
        void handleMessage(AgentMessage message);
    }
    
    /**
     * Register an agent endpoint
     */
    public void registerAgent(String agentId, AgentType agentType,
                            MessageHandler handler, Map<String, Object> capabilities) {
        AgentEndpoint endpoint = new AgentEndpoint(agentId, agentType, handler, capabilities);
        agentEndpoints.put(agentId, endpoint);
        
        // Auto-subscribe to agent-specific topic
        subscribe(agentId, "agent." + agentId, handler);
        
        // Auto-subscribe to type-specific topic
        subscribe(agentId, "type." + agentType.name(), handler);
        
        // Broadcast agent registration
        broadcast("system.agent.registered", Map.of(
            "agent_id", agentId,
            "agent_type", agentType.name(),
            "capabilities", capabilities
        ));
    }
    
    /**
     * Unregister an agent
     */
    public void unregisterAgent(String agentId) {
        AgentEndpoint endpoint = agentEndpoints.remove(agentId);
        if (endpoint != null) {
            endpoint.deactivate();
            
            // Remove all subscriptions
            for (String topic : endpoint.getSubscribedTopics()) {
                unsubscribe(agentId, topic);
            }
            
            // Broadcast agent unregistration
            broadcast("system.agent.unregistered", Map.of("agent_id", agentId));
        }
    }
    
    /**
     * Send a direct message
     */
    public void send(AgentMessage message) {
        if (message.getPayload().toString().length() > MAX_MESSAGE_SIZE) {
            throw new IllegalArgumentException("Message exceeds maximum size");
        }
        
        // For direct messages, deliver immediately if possible
        if (message.getToAgent() != null && !message.getToAgent().equals("*")) {
            AgentEndpoint endpoint = agentEndpoints.get(message.getToAgent());
            if (endpoint != null && endpoint.isActive()) {
                deliverMessage(message, endpoint);
                return;
            }
        }
        
        // Otherwise, publish to Pub/Sub
        publishMessage(message);
    }
    
    /**
     * Send request and wait for response
     */
    public CompletableFuture<AgentMessage> request(String fromAgent, String toAgent,
                                                  String topic, Map<String, Object> payload,
                                                  long timeoutMillis) {
        AgentMessage request = new AgentMessage.Builder()
            .from(fromAgent)
            .to(toAgent)
            .type(AgentMessage.MessageType.REQUEST)
            .topic(topic)
            .payload(payload)
            .build();
        
        CompletableFuture<AgentMessage> future = new CompletableFuture<>();
        pendingRequests.put(request.getMessageId(), future);
        
        // Set timeout
        timeoutScheduler.schedule(() -> {
            CompletableFuture<AgentMessage> pending = pendingRequests.remove(request.getMessageId());
            if (pending != null && !pending.isDone()) {
                pending.completeExceptionally(
                    new TimeoutException("Request timed out: " + request.getMessageId()));
            }
        }, timeoutMillis, TimeUnit.MILLISECONDS);
        
        // Send request
        send(request);
        
        return future;
    }
    
    /**
     * Send response to a request
     */
    public void respond(AgentMessage request, Map<String, Object> responsePayload) {
        if (request.getType() != AgentMessage.MessageType.REQUEST) {
            throw new IllegalArgumentException("Can only respond to REQUEST messages");
        }
        
        AgentMessage response = new AgentMessage.Builder()
            .from(request.getToAgent())
            .to(request.getFromAgent())
            .type(AgentMessage.MessageType.RESPONSE)
            .topic(request.getTopic())
            .payload(responsePayload)
            .correlationId(request.getMessageId())
            .build();
        
        send(response);
    }
    
    /**
     * Broadcast message to a topic
     */
    public void broadcast(String topic, Map<String, Object> payload) {
        broadcast("system", topic, payload);
    }
    
    public void broadcast(String fromAgent, String topic, Map<String, Object> payload) {
        AgentMessage broadcast = new AgentMessage.Builder()
            .from(fromAgent)
            .to("*") // Broadcast indicator
            .type(AgentMessage.MessageType.BROADCAST)
            .topic(topic)
            .payload(payload)
            .build();
        
        // Deliver to all subscribers
        List<MessageHandler> handlers = topicSubscriptions.get(topic);
        if (handlers != null) {
            for (MessageHandler handler : handlers) {
                deliverMessage(broadcast, handler);
            }
        }
        
        // Also publish to Pub/Sub for distributed scenarios
        publishMessage(broadcast);
    }
    
    /**
     * Subscribe to a topic
     */
    public void subscribe(String agentId, String topic, MessageHandler handler) {
        topicSubscriptions.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>())
            .add(handler);
        
        AgentEndpoint endpoint = agentEndpoints.get(agentId);
        if (endpoint != null) {
            endpoint.addSubscription(topic);
        }
    }
    
    /**
     * Unsubscribe from a topic
     */
    public void unsubscribe(String agentId, String topic) {
        AgentEndpoint endpoint = agentEndpoints.get(agentId);
        if (endpoint != null) {
            endpoint.removeSubscription(topic);
            
            List<MessageHandler> handlers = topicSubscriptions.get(topic);
            if (handlers != null) {
                handlers.remove(endpoint.getHandler());
            }
        }
    }
    
    /**
     * Query agents by capability
     */
    public List<AgentEndpoint> findAgentsByCapability(String capability, Object value) {
        return agentEndpoints.values().stream()
            .filter(endpoint -> {
                Object capValue = endpoint.getCapabilities().get(capability);
                return value.equals(capValue);
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Get active agents
     */
    public List<String> getActiveAgents() {
        return agentEndpoints.values().stream()
            .filter(AgentEndpoint::isActive)
            .map(AgentEndpoint::getAgentId)
            .collect(Collectors.toList());
    }
    
    /**
     * Send heartbeat for agent
     */
    public void heartbeat(String agentId) {
        AgentEndpoint endpoint = agentEndpoints.get(agentId);
        if (endpoint != null) {
            endpoint.heartbeat();
        }
    }
    
    /**
     * Get communication statistics
     */
    public CommunicationStatistics getStatistics() {
        Map<String, Integer> messageCountByType = new HashMap<>();
        Map<String, Integer> messageCountByAgent = new HashMap<>();
        Map<String, List<Long>> latencyByRoute = new HashMap<>();
        
        // In production, would track these metrics
        
        return new CommunicationStatistics(
            agentEndpoints.size(),
            (int) agentEndpoints.values().stream().filter(AgentEndpoint::isActive).count(),
            topicSubscriptions.size(),
            pendingRequests.size(),
            messageCountByType,
            messageCountByAgent,
            latencyByRoute
        );
    }
    
    /**
     * Communication statistics
     */
    public static class CommunicationStatistics {
        private final int totalAgents;
        private final int activeAgents;
        private final int topicCount;
        private final int pendingRequests;
        private final Map<String, Integer> messageCountByType;
        private final Map<String, Integer> messageCountByAgent;
        private final Map<String, List<Long>> latencyByRoute;
        
        public CommunicationStatistics(int totalAgents, int activeAgents, int topicCount,
                                     int pendingRequests, Map<String, Integer> messageCountByType,
                                     Map<String, Integer> messageCountByAgent,
                                     Map<String, List<Long>> latencyByRoute) {
            this.totalAgents = totalAgents;
            this.activeAgents = activeAgents;
            this.topicCount = topicCount;
            this.pendingRequests = pendingRequests;
            this.messageCountByType = messageCountByType;
            this.messageCountByAgent = messageCountByAgent;
            this.latencyByRoute = latencyByRoute;
        }
        
        // Getters
        public int getTotalAgents() { return totalAgents; }
        public int getActiveAgents() { return activeAgents; }
        public int getTopicCount() { return topicCount; }
        public int getPendingRequests() { return pendingRequests; }
        public Map<String, Integer> getMessageCountByType() { return messageCountByType; }
        public Map<String, Integer> getMessageCountByAgent() { return messageCountByAgent; }
        public Map<String, List<Long>> getLatencyByRoute() { return latencyByRoute; }
        
        public double getAverageLatency(String fromAgent, String toAgent) {
            String route = fromAgent + "->" + toAgent;
            List<Long> latencies = latencyByRoute.get(route);
            if (latencies == null || latencies.isEmpty()) {
                return 0;
            }
            return latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        }
    }
    
    /**
     * Helper methods
     */
    
    private void deliverMessage(AgentMessage message, AgentEndpoint endpoint) {
        deliverMessage(message, endpoint.getHandler());
    }
    
    private void deliverMessage(AgentMessage message, MessageHandler handler) {
        // Deliver asynchronously to prevent blocking
        messageExecutor.submit(() -> {
            try {
                handler.handleMessage(message);
            } catch (Exception e) {
                System.err.println("Error delivering message: " + e.getMessage());
                handleDeliveryFailure(message, e);
            }
        });
    }
    
    private void publishMessage(AgentMessage message) {
        try {
            // Convert to Pub/Sub message
            Map<String, String> attributes = new HashMap<>();
            attributes.put("message_id", message.getMessageId());
            attributes.put("from_agent", message.getFromAgent());
            attributes.put("to_agent", message.getToAgent());
            attributes.put("type", message.getType().name());
            attributes.put("topic", message.getTopic());
            attributes.put("timestamp", String.valueOf(message.getTimestamp()));
            attributes.putAll(message.getHeaders());
            
            // Serialize payload
            String payloadJson = serializePayload(message.getPayload());
            
            PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
                .setData(ByteString.copyFromUtf8(payloadJson))
                .putAllAttributes(attributes)
                .build();
            
            publisher.publish(pubsubMessage);
            
        } catch (Exception e) {
            System.err.println("Failed to publish message: " + e.getMessage());
        }
    }
    
    private void handleIncomingMessage(PubsubMessage pubsubMessage) {
        try {
            // Reconstruct AgentMessage
            Map<String, String> attributes = pubsubMessage.getAttributesMap();
            
            String messageId = attributes.get("message_id");
            String fromAgent = attributes.get("from_agent");
            String toAgent = attributes.get("to_agent");
            AgentMessage.MessageType type = AgentMessage.MessageType.valueOf(attributes.get("type"));
            String topic = attributes.get("topic");
            
            // Deserialize payload
            Map<String, Object> payload = deserializePayload(
                pubsubMessage.getData().toStringUtf8());
            
            AgentMessage message = new AgentMessage(fromAgent, toAgent, type, topic, payload);
            message.headers.putAll(attributes);
            
            // Handle based on type
            switch (type) {
                case RESPONSE:
                    handleResponse(message);
                    break;
                    
                case BROADCAST:
                    handleBroadcast(message);
                    break;
                    
                default:
                    // Deliver to specific agent
                    if (toAgent != null && !toAgent.equals("*")) {
                        AgentEndpoint endpoint = agentEndpoints.get(toAgent);
                        if (endpoint != null && endpoint.isActive()) {
                            deliverMessage(message, endpoint);
                        }
                    }
            }
            
        } catch (Exception e) {
            System.err.println("Error handling incoming message: " + e.getMessage());
        }
    }
    
    private void handleResponse(AgentMessage response) {
        String correlationId = response.getCorrelationId();
        if (correlationId != null) {
            CompletableFuture<AgentMessage> pending = pendingRequests.remove(correlationId);
            if (pending != null) {
                pending.complete(response);
            }
        }
    }
    
    private void handleBroadcast(AgentMessage broadcast) {
        List<MessageHandler> handlers = topicSubscriptions.get(broadcast.getTopic());
        if (handlers != null) {
            for (MessageHandler handler : handlers) {
                deliverMessage(broadcast, handler);
            }
        }
    }
    
    private void handleDeliveryFailure(AgentMessage message, Exception error) {
        // In production, implement retry logic
        System.err.println("Message delivery failed: " + message.getMessageId());
        
        // Notify sender if it's a request
        if (message.getType() == AgentMessage.MessageType.REQUEST) {
            CompletableFuture<AgentMessage> pending = pendingRequests.remove(message.getMessageId());
            if (pending != null) {
                pending.completeExceptionally(error);
            }
        }
    }
    
    private void checkTimeouts() {
        long now = System.currentTimeMillis();
        
        // Check pending requests
        Iterator<Map.Entry<String, CompletableFuture<AgentMessage>>> iter = 
            pendingRequests.entrySet().iterator();
        
        while (iter.hasNext()) {
            Map.Entry<String, CompletableFuture<AgentMessage>> entry = iter.next();
            CompletableFuture<AgentMessage> future = entry.getValue();
            
            // Remove completed futures
            if (future.isDone()) {
                iter.remove();
            }
        }
        
        // Check agent heartbeats
        for (AgentEndpoint endpoint : agentEndpoints.values()) {
            if (!endpoint.isActive()) {
                broadcast("system.agent.inactive", Map.of(
                    "agent_id", endpoint.getAgentId(),
                    "last_heartbeat", endpoint.lastHeartbeat
                ));
            }
        }
    }
    
    private String serializePayload(Map<String, Object> payload) {
        // Simple JSON serialization - in production use proper JSON library
        return payload.toString();
    }
    
    private Map<String, Object> deserializePayload(String json) {
        // Simple deserialization - in production use proper JSON library
        return new HashMap<>();
    }
    
    public void shutdown() {
        try {
            // Unregister all agents
            new ArrayList<>(agentEndpoints.keySet()).forEach(this::unregisterAgent);
            
            // Shutdown executors
            messageExecutor.shutdown();
            timeoutScheduler.shutdown();
            
            if (!messageExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                messageExecutor.shutdownNow();
            }
            
            if (!timeoutScheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                timeoutScheduler.shutdownNow();
            }
            
            // Shutdown Pub/Sub
            publisher.shutdown();
            publisher.awaitTermination(30, TimeUnit.SECONDS);
            
            subscriber.stopAsync().awaitTerminated();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}