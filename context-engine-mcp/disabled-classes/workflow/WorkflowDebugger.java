package com.zamaz.adk.workflow;

import com.google.cloud.firestore.*;
import com.google.cloud.storage.*;
import com.google.cloud.monitoring.v3.*;
import com.zamaz.adk.core.TenantContext;
import com.zamaz.adk.workflow.WorkflowEngine.*;
import com.zamaz.adk.workflow.WorkflowStreamingService.WorkflowEvent;
import com.zamaz.adk.workflow.WorkflowStateManager.PersistentState;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.function.Consumer;

/**
 * Workflow Debugger - Advanced debugging and replay capabilities
 * Enables time-travel debugging, breakpoints, and execution analysis
 */
public class WorkflowDebugger {
    private final Firestore firestore;
    private final Storage storage;
    private final MetricServiceClient metricClient;
    private final String bucketName;
    
    // Debugging state
    private final Map<String, DebugSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, ExecutionTrace> executionTraces = new ConcurrentHashMap<>();
    private final Map<String, Set<Breakpoint>> breakpoints = new ConcurrentHashMap<>();
    private final Map<String, ReplayController> replayControllers = new ConcurrentHashMap<>();
    
    // Event recording
    private final BlockingQueue<RecordedEvent> eventQueue = new LinkedBlockingQueue<>();
    private final ExecutorService recordingExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean recording = true;
    
    // Configuration
    private static final int MAX_TRACE_SIZE = 10000;
    private static final long TRACE_RETENTION = TimeUnit.DAYS.toMillis(7);
    
    public WorkflowDebugger(Firestore firestore, Storage storage,
                          String bucketName) {
        this.firestore = firestore;
        this.storage = storage;
        this.bucketName = bucketName;
        
        try {
            this.metricClient = MetricServiceClient.create();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create metric client", e);
        }
        
        // Start event recording
        recordingExecutor.submit(this::processRecordedEvents);
    }
    
    /**
     * Debug session for active debugging
     */
    public static class DebugSession {
        private final String sessionId;
        private final String workflowExecutionId;
        private final TenantContext tenantContext;
        private final DebugMode mode;
        private final Map<String, Object> debugContext;
        private final List<DebugCommand> commandHistory;
        private volatile DebugState state;
        private volatile String currentNodeId;
        private volatile int currentStep;
        private final List<Consumer<DebugEvent>> eventListeners;
        
        public enum DebugMode {
            STEP_BY_STEP,      // Pause at each node
            BREAKPOINT,        // Pause at breakpoints only
            WATCH,             // Monitor without pausing
            REPLAY,            // Replay recorded execution
            TIME_TRAVEL        // Navigate through execution history
        }
        
        public enum DebugState {
            INITIALIZED, RUNNING, PAUSED, STEPPING, FINISHED, ERROR
        }
        
        public DebugSession(String sessionId, String workflowExecutionId,
                          TenantContext tenantContext, DebugMode mode) {
            this.sessionId = sessionId;
            this.workflowExecutionId = workflowExecutionId;
            this.tenantContext = tenantContext;
            this.mode = mode;
            this.debugContext = new HashMap<>();
            this.commandHistory = new ArrayList<>();
            this.state = DebugState.INITIALIZED;
            this.currentStep = 0;
            this.eventListeners = new CopyOnWriteArrayList<>();
        }
        
        public void addEventListener(Consumer<DebugEvent> listener) {
            eventListeners.add(listener);
        }
        
        public void removeEventListener(Consumer<DebugEvent> listener) {
            eventListeners.remove(listener);
        }
        
        private void notifyListeners(DebugEvent event) {
            eventListeners.forEach(listener -> {
                try {
                    listener.accept(event);
                } catch (Exception e) {
                    System.err.println("Error notifying debug listener: " + e.getMessage());
                }
            });
        }
        
        // Getters and state management
        public String getSessionId() { return sessionId; }
        public String getWorkflowExecutionId() { return workflowExecutionId; }
        public TenantContext getTenantContext() { return tenantContext; }
        public DebugMode getMode() { return mode; }
        public Map<String, Object> getDebugContext() { return debugContext; }
        public List<DebugCommand> getCommandHistory() { return commandHistory; }
        public DebugState getState() { return state; }
        public String getCurrentNodeId() { return currentNodeId; }
        public int getCurrentStep() { return currentStep; }
        
        public void setState(DebugState state) { 
            this.state = state;
            notifyListeners(new DebugEvent(DebugEvent.Type.STATE_CHANGED, state));
        }
        
        public void setCurrentNode(String nodeId) { 
            this.currentNodeId = nodeId;
            notifyListeners(new DebugEvent(DebugEvent.Type.NODE_CHANGED, nodeId));
        }
        
        public void incrementStep() { 
            currentStep++;
            notifyListeners(new DebugEvent(DebugEvent.Type.STEP_CHANGED, currentStep));
        }
    }
    
    /**
     * Debug event
     */
    public static class DebugEvent {
        private final Type type;
        private final Object data;
        private final long timestamp;
        
        public enum Type {
            STATE_CHANGED, NODE_CHANGED, STEP_CHANGED, BREAKPOINT_HIT,
            VARIABLE_CHANGED, ERROR_OCCURRED, EXECUTION_COMPLETED
        }
        
        public DebugEvent(Type type, Object data) {
            this.type = type;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
        
        // Getters
        public Type getType() { return type; }
        public Object getData() { return data; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Debug command
     */
    public static class DebugCommand {
        private final CommandType type;
        private final Map<String, Object> parameters;
        private final long timestamp;
        private final String userId;
        
        public enum CommandType {
            CONTINUE,          // Resume execution
            STEP_OVER,         // Execute current node
            STEP_INTO,         // Step into sub-workflow
            STEP_OUT,          // Complete current level
            PAUSE,             // Pause execution
            SET_BREAKPOINT,    // Set breakpoint
            REMOVE_BREAKPOINT, // Remove breakpoint
            INSPECT_VARIABLE,  // Inspect variable value
            MODIFY_VARIABLE,   // Change variable value
            JUMP_TO_NODE,      // Jump to specific node
            RESTART,           // Restart workflow
            TERMINATE          // Stop execution
        }
        
        public DebugCommand(CommandType type, Map<String, Object> parameters, String userId) {
            this.type = type;
            this.parameters = parameters;
            this.timestamp = System.currentTimeMillis();
            this.userId = userId;
        }
        
        // Getters
        public CommandType getType() { return type; }
        public Map<String, Object> getParameters() { return parameters; }
        public long getTimestamp() { return timestamp; }
        public String getUserId() { return userId; }
    }
    
    /**
     * Breakpoint definition
     */
    public static class Breakpoint {
        private final String breakpointId;
        private final BreakpointType type;
        private final String location;
        private final String condition;
        private final boolean enabled;
        private final Map<String, Object> metadata;
        private int hitCount;
        
        public enum BreakpointType {
            NODE,              // Break at specific node
            EDGE,              // Break at edge transition
            CONDITION,         // Break when condition met
            EXCEPTION,         // Break on exception
            VARIABLE_CHANGE,   // Break on variable change
            PERFORMANCE        // Break on performance threshold
        }
        
        public Breakpoint(String breakpointId, BreakpointType type, String location,
                        String condition, boolean enabled, Map<String, Object> metadata) {
            this.breakpointId = breakpointId;
            this.type = type;
            this.location = location;
            this.condition = condition;
            this.enabled = enabled;
            this.metadata = metadata;
            this.hitCount = 0;
        }
        
        public boolean shouldBreak(ExecutionContext context) {
            if (!enabled) return false;
            
            switch (type) {
                case NODE:
                    return context.getCurrentNodeId().equals(location);
                    
                case CONDITION:
                    return evaluateCondition(condition, context);
                    
                case VARIABLE_CHANGE:
                    String varName = (String) metadata.get("variable");
                    Object oldValue = metadata.get("last_value");
                    Object newValue = context.getVariable(varName);
                    if (!Objects.equals(oldValue, newValue)) {
                        metadata.put("last_value", newValue);
                        return true;
                    }
                    return false;
                    
                case PERFORMANCE:
                    Long threshold = (Long) metadata.get("threshold_ms");
                    Long nodeStartTime = (Long) context.getMetadata("node_start_time");
                    if (nodeStartTime != null && threshold != null) {
                        return System.currentTimeMillis() - nodeStartTime > threshold;
                    }
                    return false;
                    
                default:
                    return false;
            }
        }
        
        private boolean evaluateCondition(String condition, ExecutionContext context) {
            // Simple condition evaluation - in production would use expression engine
            if (condition.contains("==")) {
                String[] parts = condition.split("==");
                if (parts.length == 2) {
                    String varName = parts[0].trim();
                    String expectedValue = parts[1].trim();
                    Object actualValue = context.getVariable(varName);
                    return expectedValue.equals(String.valueOf(actualValue));
                }
            }
            return false;
        }
        
        public void recordHit() {
            hitCount++;
        }
        
        // Getters
        public String getBreakpointId() { return breakpointId; }
        public BreakpointType getType() { return type; }
        public String getLocation() { return location; }
        public String getCondition() { return condition; }
        public boolean isEnabled() { return enabled; }
        public Map<String, Object> getMetadata() { return metadata; }
        public int getHitCount() { return hitCount; }
    }
    
    /**
     * Execution trace for recording
     */
    public static class ExecutionTrace {
        private final String traceId;
        private final String workflowExecutionId;
        private final TenantContext tenantContext;
        private final List<TraceEvent> events;
        private final Map<String, StateSnapshot> stateSnapshots;
        private final Map<String, PerformanceMetrics> performanceMetrics;
        private final long startTime;
        private long endTime;
        private ExecutionStatus status;
        
        public enum ExecutionStatus {
            RUNNING, COMPLETED, FAILED, TERMINATED
        }
        
        public ExecutionTrace(String traceId, String workflowExecutionId,
                            TenantContext tenantContext) {
            this.traceId = traceId;
            this.workflowExecutionId = workflowExecutionId;
            this.tenantContext = tenantContext;
            this.events = new ArrayList<>();
            this.stateSnapshots = new LinkedHashMap<>();
            this.performanceMetrics = new HashMap<>();
            this.startTime = System.currentTimeMillis();
            this.status = ExecutionStatus.RUNNING;
        }
        
        public void addEvent(TraceEvent event) {
            if (events.size() < MAX_TRACE_SIZE) {
                events.add(event);
            }
        }
        
        public void captureStateSnapshot(String snapshotId, State state) {
            StateSnapshot snapshot = new StateSnapshot(snapshotId, state, 
                System.currentTimeMillis());
            stateSnapshots.put(snapshotId, snapshot);
        }
        
        public void recordPerformance(String nodeId, long executionTime, 
                                    Map<String, Object> metrics) {
            performanceMetrics.put(nodeId, new PerformanceMetrics(
                nodeId, executionTime, metrics));
        }
        
        public void complete(ExecutionStatus status) {
            this.status = status;
            this.endTime = System.currentTimeMillis();
        }
        
        // Getters
        public String getTraceId() { return traceId; }
        public String getWorkflowExecutionId() { return workflowExecutionId; }
        public TenantContext getTenantContext() { return tenantContext; }
        public List<TraceEvent> getEvents() { return events; }
        public Map<String, StateSnapshot> getStateSnapshots() { return stateSnapshots; }
        public Map<String, PerformanceMetrics> getPerformanceMetrics() { return performanceMetrics; }
        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }
        public ExecutionStatus getStatus() { return status; }
        public long getDuration() { return endTime - startTime; }
    }
    
    /**
     * Trace event
     */
    public static class TraceEvent {
        private final String eventId;
        private final EventType type;
        private final String nodeId;
        private final Map<String, Object> data;
        private final long timestamp;
        private final int sequenceNumber;
        
        public enum EventType {
            NODE_ENTER, NODE_EXIT, NODE_ERROR,
            EDGE_TRAVERSE, STATE_CHANGE, VARIABLE_SET,
            BREAKPOINT_HIT, USER_ACTION, SYSTEM_EVENT
        }
        
        public TraceEvent(String eventId, EventType type, String nodeId,
                        Map<String, Object> data, int sequenceNumber) {
            this.eventId = eventId;
            this.type = type;
            this.nodeId = nodeId;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
            this.sequenceNumber = sequenceNumber;
        }
        
        // Getters
        public String getEventId() { return eventId; }
        public EventType getType() { return type; }
        public String getNodeId() { return nodeId; }
        public Map<String, Object> getData() { return data; }
        public long getTimestamp() { return timestamp; }
        public int getSequenceNumber() { return sequenceNumber; }
    }
    
    /**
     * State snapshot
     */
    public static class StateSnapshot {
        private final String snapshotId;
        private final Map<String, Object> variables;
        private final String currentNodeId;
        private final Set<String> visitedNodes;
        private final long timestamp;
        
        public StateSnapshot(String snapshotId, State state, long timestamp) {
            this.snapshotId = snapshotId;
            this.variables = new HashMap<>(state.getVariables());
            this.currentNodeId = state.getCurrentNodeId();
            this.visitedNodes = new HashSet<>(state.getVisitedNodes());
            this.timestamp = timestamp;
        }
        
        // Getters
        public String getSnapshotId() { return snapshotId; }
        public Map<String, Object> getVariables() { return variables; }
        public String getCurrentNodeId() { return currentNodeId; }
        public Set<String> getVisitedNodes() { return visitedNodes; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Performance metrics
     */
    public static class PerformanceMetrics {
        private final String nodeId;
        private final long executionTime;
        private final Map<String, Object> customMetrics;
        
        public PerformanceMetrics(String nodeId, long executionTime,
                                Map<String, Object> customMetrics) {
            this.nodeId = nodeId;
            this.executionTime = executionTime;
            this.customMetrics = customMetrics;
        }
        
        // Getters
        public String getNodeId() { return nodeId; }
        public long getExecutionTime() { return executionTime; }
        public Map<String, Object> getCustomMetrics() { return customMetrics; }
    }
    
    /**
     * Recorded event for async processing
     */
    private static class RecordedEvent {
        private final String workflowExecutionId;
        private final TraceEvent event;
        
        public RecordedEvent(String workflowExecutionId, TraceEvent event) {
            this.workflowExecutionId = workflowExecutionId;
            this.event = event;
        }
    }
    
    /**
     * Replay controller for execution replay
     */
    public static class ReplayController {
        private final ExecutionTrace trace;
        private int currentEventIndex;
        private final Map<String, Consumer<TraceEvent>> eventHandlers;
        private volatile ReplayState state;
        private double playbackSpeed;
        
        public enum ReplayState {
            READY, PLAYING, PAUSED, FINISHED
        }
        
        public ReplayController(ExecutionTrace trace) {
            this.trace = trace;
            this.currentEventIndex = 0;
            this.eventHandlers = new HashMap<>();
            this.state = ReplayState.READY;
            this.playbackSpeed = 1.0;
        }
        
        public void play() {
            state = ReplayState.PLAYING;
            // Replay logic would be implemented here
        }
        
        public void pause() {
            state = ReplayState.PAUSED;
        }
        
        public void stepForward() {
            if (currentEventIndex < trace.getEvents().size() - 1) {
                currentEventIndex++;
                processEvent(trace.getEvents().get(currentEventIndex));
            }
        }
        
        public void stepBackward() {
            if (currentEventIndex > 0) {
                currentEventIndex--;
                // Would need to restore state to this point
            }
        }
        
        public void jumpToEvent(int eventIndex) {
            if (eventIndex >= 0 && eventIndex < trace.getEvents().size()) {
                currentEventIndex = eventIndex;
                // Would need to restore state to this point
            }
        }
        
        public void setPlaybackSpeed(double speed) {
            this.playbackSpeed = Math.max(0.1, Math.min(10.0, speed));
        }
        
        public void registerEventHandler(String eventType, Consumer<TraceEvent> handler) {
            eventHandlers.put(eventType, handler);
        }
        
        private void processEvent(TraceEvent event) {
            Consumer<TraceEvent> handler = eventHandlers.get(event.getType().name());
            if (handler != null) {
                handler.accept(event);
            }
        }
        
        // Getters
        public ExecutionTrace getTrace() { return trace; }
        public int getCurrentEventIndex() { return currentEventIndex; }
        public ReplayState getState() { return state; }
        public double getPlaybackSpeed() { return playbackSpeed; }
        
        public TraceEvent getCurrentEvent() {
            if (currentEventIndex >= 0 && currentEventIndex < trace.getEvents().size()) {
                return trace.getEvents().get(currentEventIndex);
            }
            return null;
        }
    }
    
    /**
     * Start debug session
     */
    public DebugSession startDebugSession(String workflowExecutionId,
                                        TenantContext tenantContext,
                                        DebugSession.DebugMode mode) {
        String sessionId = UUID.randomUUID().toString();
        DebugSession session = new DebugSession(sessionId, workflowExecutionId,
            tenantContext, mode);
        
        activeSessions.put(sessionId, session);
        
        // Initialize based on mode
        switch (mode) {
            case REPLAY:
                initializeReplay(session);
                break;
            case TIME_TRAVEL:
                initializeTimeTravel(session);
                break;
            default:
                session.setState(DebugSession.DebugState.RUNNING);
        }
        
        return session;
    }
    
    /**
     * Execute debug command
     */
    public CompletableFuture<DebugCommandResult> executeCommand(String sessionId,
                                                               DebugCommand command) {
        return CompletableFuture.supplyAsync(() -> {
            DebugSession session = activeSessions.get(sessionId);
            if (session == null) {
                return new DebugCommandResult(false, "Session not found");
            }
            
            session.getCommandHistory().add(command);
            
            switch (command.getType()) {
                case CONTINUE:
                    return handleContinue(session);
                    
                case STEP_OVER:
                    return handleStepOver(session);
                    
                case STEP_INTO:
                    return handleStepInto(session);
                    
                case PAUSE:
                    return handlePause(session);
                    
                case SET_BREAKPOINT:
                    return handleSetBreakpoint(session, command.getParameters());
                    
                case REMOVE_BREAKPOINT:
                    return handleRemoveBreakpoint(session, command.getParameters());
                    
                case INSPECT_VARIABLE:
                    return handleInspectVariable(session, command.getParameters());
                    
                case MODIFY_VARIABLE:
                    return handleModifyVariable(session, command.getParameters());
                    
                case JUMP_TO_NODE:
                    return handleJumpToNode(session, command.getParameters());
                    
                case RESTART:
                    return handleRestart(session);
                    
                case TERMINATE:
                    return handleTerminate(session);
                    
                default:
                    return new DebugCommandResult(false, "Unknown command");
            }
        });
    }
    
    /**
     * Debug command result
     */
    public static class DebugCommandResult {
        private final boolean success;
        private final String message;
        private final Map<String, Object> data;
        
        public DebugCommandResult(boolean success, String message) {
            this(success, message, new HashMap<>());
        }
        
        public DebugCommandResult(boolean success, String message, Map<String, Object> data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Map<String, Object> getData() { return data; }
    }
    
    /**
     * Set breakpoint
     */
    public String setBreakpoint(String workflowId, Breakpoint breakpoint) {
        breakpoints.computeIfAbsent(workflowId, k -> new HashSet<>()).add(breakpoint);
        
        // Persist breakpoint
        persistBreakpoint(workflowId, breakpoint);
        
        return breakpoint.getBreakpointId();
    }
    
    /**
     * Remove breakpoint
     */
    public boolean removeBreakpoint(String workflowId, String breakpointId) {
        Set<Breakpoint> workflowBreakpoints = breakpoints.get(workflowId);
        if (workflowBreakpoints != null) {
            boolean removed = workflowBreakpoints.removeIf(
                bp -> bp.getBreakpointId().equals(breakpointId));
            
            if (removed) {
                // Remove from persistence
                firestore.collection("breakpoints")
                    .document(breakpointId)
                    .delete();
            }
            
            return removed;
        }
        return false;
    }
    
    /**
     * Record workflow event
     */
    public void recordEvent(String workflowExecutionId, WorkflowEvent event) {
        if (!recording) return;
        
        ExecutionTrace trace = executionTraces.computeIfAbsent(workflowExecutionId,
            k -> new ExecutionTrace(UUID.randomUUID().toString(), k, null));
        
        // Convert WorkflowEvent to TraceEvent
        TraceEvent traceEvent = new TraceEvent(
            UUID.randomUUID().toString(),
            mapEventType(event.getEventType()),
            event.getNodeId(),
            event.getData(),
            trace.getEvents().size()
        );
        
        trace.addEvent(traceEvent);
        
        // Queue for async processing
        eventQueue.offer(new RecordedEvent(workflowExecutionId, traceEvent));
    }
    
    /**
     * Capture state snapshot
     */
    public void captureStateSnapshot(String workflowExecutionId, State state) {
        ExecutionTrace trace = executionTraces.get(workflowExecutionId);
        if (trace != null) {
            String snapshotId = "snapshot_" + System.currentTimeMillis();
            trace.captureStateSnapshot(snapshotId, state);
        }
    }
    
    /**
     * Start execution replay
     */
    public ReplayController startReplay(String traceId) {
        ExecutionTrace trace = loadTrace(traceId);
        if (trace == null) {
            throw new IllegalArgumentException("Trace not found: " + traceId);
        }
        
        ReplayController controller = new ReplayController(trace);
        replayControllers.put(traceId, controller);
        
        return controller;
    }
    
    /**
     * Get execution analysis
     */
    public ExecutionAnalysis analyzeExecution(String workflowExecutionId) {
        ExecutionTrace trace = executionTraces.get(workflowExecutionId);
        if (trace == null) {
            trace = loadTrace(workflowExecutionId);
        }
        
        if (trace == null) {
            return null;
        }
        
        // Analyze performance
        Map<String, Long> nodeExecutionTimes = new HashMap<>();
        Map<String, Integer> nodeVisitCounts = new HashMap<>();
        List<String> criticalPath = findCriticalPath(trace);
        Map<String, List<String>> bottlenecks = findBottlenecks(trace);
        
        // Analyze errors
        List<TraceEvent> errorEvents = trace.getEvents().stream()
            .filter(e -> e.getType() == TraceEvent.EventType.NODE_ERROR)
            .collect(Collectors.toList());
        
        // Calculate statistics
        long totalDuration = trace.getDuration();
        double avgNodeExecutionTime = trace.getPerformanceMetrics().values().stream()
            .mapToLong(PerformanceMetrics::getExecutionTime)
            .average()
            .orElse(0);
        
        return new ExecutionAnalysis(
            workflowExecutionId,
            trace.getStatus(),
            totalDuration,
            nodeExecutionTimes,
            nodeVisitCounts,
            criticalPath,
            bottlenecks,
            errorEvents,
            avgNodeExecutionTime
        );
    }
    
    /**
     * Execution analysis result
     */
    public static class ExecutionAnalysis {
        private final String workflowExecutionId;
        private final ExecutionTrace.ExecutionStatus status;
        private final long totalDuration;
        private final Map<String, Long> nodeExecutionTimes;
        private final Map<String, Integer> nodeVisitCounts;
        private final List<String> criticalPath;
        private final Map<String, List<String>> bottlenecks;
        private final List<TraceEvent> errors;
        private final double averageNodeExecutionTime;
        
        public ExecutionAnalysis(String workflowExecutionId, ExecutionTrace.ExecutionStatus status,
                               long totalDuration, Map<String, Long> nodeExecutionTimes,
                               Map<String, Integer> nodeVisitCounts, List<String> criticalPath,
                               Map<String, List<String>> bottlenecks, List<TraceEvent> errors,
                               double averageNodeExecutionTime) {
            this.workflowExecutionId = workflowExecutionId;
            this.status = status;
            this.totalDuration = totalDuration;
            this.nodeExecutionTimes = nodeExecutionTimes;
            this.nodeVisitCounts = nodeVisitCounts;
            this.criticalPath = criticalPath;
            this.bottlenecks = bottlenecks;
            this.errors = errors;
            this.averageNodeExecutionTime = averageNodeExecutionTime;
        }
        
        // Getters
        public String getWorkflowExecutionId() { return workflowExecutionId; }
        public ExecutionTrace.ExecutionStatus getStatus() { return status; }
        public long getTotalDuration() { return totalDuration; }
        public Map<String, Long> getNodeExecutionTimes() { return nodeExecutionTimes; }
        public Map<String, Integer> getNodeVisitCounts() { return nodeVisitCounts; }
        public List<String> getCriticalPath() { return criticalPath; }
        public Map<String, List<String>> getBottlenecks() { return bottlenecks; }
        public List<TraceEvent> getErrors() { return errors; }
        public double getAverageNodeExecutionTime() { return averageNodeExecutionTime; }
    }
    
    /**
     * Check breakpoints
     */
    public boolean checkBreakpoints(String workflowId, ExecutionContext context) {
        Set<Breakpoint> workflowBreakpoints = breakpoints.get(workflowId);
        if (workflowBreakpoints == null) return false;
        
        for (Breakpoint breakpoint : workflowBreakpoints) {
            if (breakpoint.shouldBreak(context)) {
                breakpoint.recordHit();
                
                // Notify debug sessions
                activeSessions.values().stream()
                    .filter(session -> session.getWorkflowExecutionId()
                        .equals(context.getExecutionId()))
                    .forEach(session -> {
                        session.setState(DebugSession.DebugState.PAUSED);
                        session.notifyListeners(new DebugEvent(
                            DebugEvent.Type.BREAKPOINT_HIT, breakpoint));
                    });
                
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Export trace for analysis
     */
    public CompletableFuture<String> exportTrace(String traceId, ExportFormat format) {
        return CompletableFuture.supplyAsync(() -> {
            ExecutionTrace trace = executionTraces.get(traceId);
            if (trace == null) {
                trace = loadTrace(traceId);
            }
            
            if (trace == null) {
                throw new IllegalArgumentException("Trace not found: " + traceId);
            }
            
            String exportData;
            switch (format) {
                case JSON:
                    exportData = exportToJson(trace);
                    break;
                case CSV:
                    exportData = exportToCsv(trace);
                    break;
                case CHROME_TRACE:
                    exportData = exportToChromeTrace(trace);
                    break;
                default:
                    throw new UnsupportedOperationException("Export format not supported: " + format);
            }
            
            // Store export
            String exportPath = String.format("exports/%s/%s.%s",
                traceId, System.currentTimeMillis(), format.name().toLowerCase());
            
            BlobId blobId = BlobId.of(bucketName, exportPath);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
            storage.create(blobInfo, exportData.getBytes(StandardCharsets.UTF_8));
            
            return exportPath;
        });
    }
    
    public enum ExportFormat {
        JSON, CSV, CHROME_TRACE
    }
    
    /**
     * Helper methods
     */
    
    private void initializeReplay(DebugSession session) {
        // Load trace for replay
        ExecutionTrace trace = loadTrace(session.getWorkflowExecutionId());
        if (trace != null) {
            ReplayController controller = new ReplayController(trace);
            replayControllers.put(session.getSessionId(), controller);
            session.setState(DebugSession.DebugState.PAUSED);
        }
    }
    
    private void initializeTimeTravel(DebugSession session) {
        // Similar to replay but with bidirectional navigation
        initializeReplay(session);
    }
    
    private DebugCommandResult handleContinue(DebugSession session) {
        session.setState(DebugSession.DebugState.RUNNING);
        return new DebugCommandResult(true, "Execution resumed");
    }
    
    private DebugCommandResult handleStepOver(DebugSession session) {
        session.setState(DebugSession.DebugState.STEPPING);
        session.incrementStep();
        return new DebugCommandResult(true, "Stepped to next node");
    }
    
    private DebugCommandResult handleStepInto(DebugSession session) {
        // Implementation for stepping into sub-workflows
        return new DebugCommandResult(true, "Stepped into sub-workflow");
    }
    
    private DebugCommandResult handlePause(DebugSession session) {
        session.setState(DebugSession.DebugState.PAUSED);
        return new DebugCommandResult(true, "Execution paused");
    }
    
    private DebugCommandResult handleSetBreakpoint(DebugSession session,
                                                  Map<String, Object> parameters) {
        String nodeId = (String) parameters.get("nodeId");
        String condition = (String) parameters.get("condition");
        
        Breakpoint breakpoint = new Breakpoint(
            UUID.randomUUID().toString(),
            Breakpoint.BreakpointType.NODE,
            nodeId,
            condition,
            true,
            parameters
        );
        
        String breakpointId = setBreakpoint(session.getWorkflowExecutionId(), breakpoint);
        
        return new DebugCommandResult(true, "Breakpoint set",
            Map.of("breakpointId", breakpointId));
    }
    
    private DebugCommandResult handleRemoveBreakpoint(DebugSession session,
                                                    Map<String, Object> parameters) {
        String breakpointId = (String) parameters.get("breakpointId");
        boolean removed = removeBreakpoint(session.getWorkflowExecutionId(), breakpointId);
        
        return new DebugCommandResult(removed,
            removed ? "Breakpoint removed" : "Breakpoint not found");
    }
    
    private DebugCommandResult handleInspectVariable(DebugSession session,
                                                   Map<String, Object> parameters) {
        String variableName = (String) parameters.get("variableName");
        
        // In real implementation, would get from execution context
        Object value = session.getDebugContext().get(variableName);
        
        return new DebugCommandResult(true, "Variable inspected",
            Map.of("value", value != null ? value : "null"));
    }
    
    private DebugCommandResult handleModifyVariable(DebugSession session,
                                                  Map<String, Object> parameters) {
        String variableName = (String) parameters.get("variableName");
        Object newValue = parameters.get("newValue");
        
        // In real implementation, would modify execution context
        session.getDebugContext().put(variableName, newValue);
        
        return new DebugCommandResult(true, "Variable modified");
    }
    
    private DebugCommandResult handleJumpToNode(DebugSession session,
                                              Map<String, Object> parameters) {
        String targetNodeId = (String) parameters.get("targetNodeId");
        
        // In real implementation, would modify execution flow
        session.setCurrentNode(targetNodeId);
        
        return new DebugCommandResult(true, "Jumped to node: " + targetNodeId);
    }
    
    private DebugCommandResult handleRestart(DebugSession session) {
        session.setState(DebugSession.DebugState.INITIALIZED);
        session.getDebugContext().clear();
        return new DebugCommandResult(true, "Workflow restarted");
    }
    
    private DebugCommandResult handleTerminate(DebugSession session) {
        session.setState(DebugSession.DebugState.FINISHED);
        activeSessions.remove(session.getSessionId());
        return new DebugCommandResult(true, "Debug session terminated");
    }
    
    private TraceEvent.EventType mapEventType(WorkflowEvent.EventType workflowEventType) {
        return switch (workflowEventType) {
            case NODE_STARTED -> TraceEvent.EventType.NODE_ENTER;
            case NODE_COMPLETED -> TraceEvent.EventType.NODE_EXIT;
            case NODE_FAILED -> TraceEvent.EventType.NODE_ERROR;
            case EDGE_TRAVERSED -> TraceEvent.EventType.EDGE_TRAVERSE;
            case STATE_UPDATED -> TraceEvent.EventType.STATE_CHANGE;
            default -> TraceEvent.EventType.SYSTEM_EVENT;
        };
    }
    
    private void processRecordedEvents() {
        while (recording) {
            try {
                RecordedEvent recorded = eventQueue.poll(1, TimeUnit.SECONDS);
                if (recorded != null) {
                    // Batch process events
                    List<RecordedEvent> batch = new ArrayList<>();
                    batch.add(recorded);
                    eventQueue.drainTo(batch, 99);
                    
                    // Persist batch
                    persistEventBatch(batch);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    private void persistEventBatch(List<RecordedEvent> batch) {
        // Group by execution ID
        Map<String, List<RecordedEvent>> byExecution = batch.stream()
            .collect(Collectors.groupingBy(RecordedEvent::workflowExecutionId));
        
        // Persist each group
        byExecution.forEach((executionId, events) -> {
            ExecutionTrace trace = executionTraces.get(executionId);
            if (trace != null) {
                persistTrace(trace);
            }
        });
    }
    
    private void persistTrace(ExecutionTrace trace) {
        // Store trace metadata in Firestore
        Map<String, Object> traceData = new HashMap<>();
        traceData.put("trace_id", trace.getTraceId());
        traceData.put("workflow_execution_id", trace.getWorkflowExecutionId());
        traceData.put("start_time", trace.getStartTime());
        traceData.put("end_time", trace.getEndTime());
        traceData.put("status", trace.getStatus().name());
        traceData.put("event_count", trace.getEvents().size());
        
        firestore.collection("execution_traces")
            .document(trace.getTraceId())
            .set(traceData);
        
        // Store detailed events in Cloud Storage
        String tracePath = String.format("traces/%s/events.json", trace.getTraceId());
        BlobId blobId = BlobId.of(bucketName, tracePath);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
        
        // Serialize events to JSON
        String eventsJson = serializeEvents(trace.getEvents());
        storage.create(blobInfo, eventsJson.getBytes(StandardCharsets.UTF_8));
    }
    
    private void persistBreakpoint(String workflowId, Breakpoint breakpoint) {
        Map<String, Object> data = new HashMap<>();
        data.put("breakpoint_id", breakpoint.getBreakpointId());
        data.put("workflow_id", workflowId);
        data.put("type", breakpoint.getType().name());
        data.put("location", breakpoint.getLocation());
        data.put("condition", breakpoint.getCondition());
        data.put("enabled", breakpoint.isEnabled());
        data.put("metadata", breakpoint.getMetadata());
        
        firestore.collection("breakpoints")
            .document(breakpoint.getBreakpointId())
            .set(data);
    }
    
    private ExecutionTrace loadTrace(String traceId) {
        // Load from Firestore and Cloud Storage
        // Implementation would reconstruct trace from persisted data
        return executionTraces.get(traceId);
    }
    
    private List<String> findCriticalPath(ExecutionTrace trace) {
        // Analyze trace to find longest execution path
        // Simple implementation - would be more sophisticated in production
        return trace.getEvents().stream()
            .filter(e -> e.getType() == TraceEvent.EventType.NODE_ENTER)
            .map(TraceEvent::getNodeId)
            .distinct()
            .collect(Collectors.toList());
    }
    
    private Map<String, List<String>> findBottlenecks(ExecutionTrace trace) {
        Map<String, List<String>> bottlenecks = new HashMap<>();
        
        // Find nodes with longest execution times
        trace.getPerformanceMetrics().entrySet().stream()
            .filter(e -> e.getValue().getExecutionTime() > 1000) // > 1 second
            .forEach(e -> bottlenecks.computeIfAbsent("slow_nodes", k -> new ArrayList<>())
                .add(e.getKey()));
        
        return bottlenecks;
    }
    
    private String serializeEvents(List<TraceEvent> events) {
        // Simple JSON serialization
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < events.size(); i++) {
            if (i > 0) json.append(",");
            TraceEvent event = events.get(i);
            json.append("{")
                .append("\"eventId\":\"").append(event.getEventId()).append("\",")
                .append("\"type\":\"").append(event.getType()).append("\",")
                .append("\"nodeId\":\"").append(event.getNodeId()).append("\",")
                .append("\"timestamp\":").append(event.getTimestamp()).append(",")
                .append("\"sequenceNumber\":").append(event.getSequenceNumber())
                .append("}");
        }
        json.append("]");
        return json.toString();
    }
    
    private String exportToJson(ExecutionTrace trace) {
        // Export trace as JSON
        return serializeEvents(trace.getEvents());
    }
    
    private String exportToCsv(ExecutionTrace trace) {
        StringBuilder csv = new StringBuilder();
        csv.append("EventID,Type,NodeID,Timestamp,SequenceNumber\n");
        
        for (TraceEvent event : trace.getEvents()) {
            csv.append(event.getEventId()).append(",")
               .append(event.getType()).append(",")
               .append(event.getNodeId()).append(",")
               .append(event.getTimestamp()).append(",")
               .append(event.getSequenceNumber()).append("\n");
        }
        
        return csv.toString();
    }
    
    private String exportToChromeTrace(ExecutionTrace trace) {
        // Export in Chrome Trace Format for visualization
        StringBuilder chromeTrace = new StringBuilder("[");
        
        for (int i = 0; i < trace.getEvents().size(); i++) {
            if (i > 0) chromeTrace.append(",");
            TraceEvent event = trace.getEvents().get(i);
            
            chromeTrace.append("{")
                .append("\"name\":\"").append(event.getNodeId()).append("\",")
                .append("\"cat\":\"").append(event.getType()).append("\",")
                .append("\"ph\":\"X\",") // Complete event
                .append("\"ts\":").append(event.getTimestamp() * 1000).append(",") // microseconds
                .append("\"dur\":1000,") // 1ms duration
                .append("\"pid\":1,")
                .append("\"tid\":1")
                .append("}");
        }
        
        chromeTrace.append("]");
        return chromeTrace.toString();
    }
    
    public void shutdown() {
        recording = false;
        recordingExecutor.shutdown();
        
        try {
            if (!recordingExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                recordingExecutor.shutdownNow();
            }
            
            // Persist remaining traces
            executionTraces.values().forEach(this::persistTrace);
            
            metricClient.close();
        } catch (Exception e) {
            recordingExecutor.shutdownNow();
        }
    }
}