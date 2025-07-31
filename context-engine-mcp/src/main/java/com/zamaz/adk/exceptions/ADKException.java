package com.zamaz.adk.exceptions;

/**
 * Base exception class for all ADK-related exceptions
 * Provides common error handling functionality and context
 */
public abstract class ADKException extends RuntimeException {
    private final String errorCode;
    private final ErrorSeverity severity;
    private final String component;
    private final Object context;
    
    public enum ErrorSeverity {
        INFO, WARNING, ERROR, CRITICAL
    }
    
    protected ADKException(String message, String errorCode, ErrorSeverity severity, 
                          String component, Object context) {
        super(message);
        this.errorCode = errorCode;
        this.severity = severity;
        this.component = component;
        this.context = context;
    }
    
    protected ADKException(String message, Throwable cause, String errorCode, 
                          ErrorSeverity severity, String component, Object context) {
        super(message, cause);
        this.errorCode = errorCode;
        this.severity = severity;
        this.component = component;
        this.context = context;
    }
    
    public String getErrorCode() { return errorCode; }
    public ErrorSeverity getSeverity() { return severity; }
    public String getComponent() { return component; }
    public Object getContext() { return context; }
    
    /**
     * Get structured error information for logging and monitoring
     */
    public ErrorInfo getErrorInfo() {
        return new ErrorInfo(
            getClass().getSimpleName(),
            errorCode,
            getMessage(),
            severity,
            component,
            context,
            System.currentTimeMillis()
        );
    }
    
    public static class ErrorInfo {
        private final String exceptionType;
        private final String errorCode;
        private final String message;
        private final ErrorSeverity severity;
        private final String component;
        private final Object context;
        private final long timestamp;
        
        public ErrorInfo(String exceptionType, String errorCode, String message, 
                        ErrorSeverity severity, String component, Object context, long timestamp) {
            this.exceptionType = exceptionType;
            this.errorCode = errorCode;
            this.message = message;
            this.severity = severity;
            this.component = component;
            this.context = context;
            this.timestamp = timestamp;
        }
        
        // Getters
        public String getExceptionType() { return exceptionType; }
        public String getErrorCode() { return errorCode; }
        public String getMessage() { return message; }
        public ErrorSeverity getSeverity() { return severity; }
        public String getComponent() { return component; }
        public Object getContext() { return context; }
        public long getTimestamp() { return timestamp; }
        
        @Override
        public String toString() {
            return String.format(
                "ErrorInfo{type='%s', code='%s', message='%s', severity=%s, component='%s', timestamp=%d}",
                exceptionType, errorCode, message, severity, component, timestamp
            );
        }
    }
}