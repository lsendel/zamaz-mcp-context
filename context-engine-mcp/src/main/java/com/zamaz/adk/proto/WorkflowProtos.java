package com.zamaz.adk.proto;

/**
 * Protocol Buffer classes for workflow operations
 * In a real implementation, these would be generated from .proto files
 */
public class WorkflowProtos {
    
    public static class WorkflowRequest {
        private String id;
        private String type;
        private String payload;
        
        public WorkflowRequest() {}
        
        public WorkflowRequest(String id, String type, String payload) {
            this.id = id;
            this.type = type;
            this.payload = payload;
        }
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getPayload() { return payload; }
        public void setPayload(String payload) { this.payload = payload; }
    }
    
    public static class WorkflowResponse {
        private String id;
        private String status;
        private String result;
        private String error;
        
        public WorkflowResponse() {}
        
        public WorkflowResponse(String id, String status, String result) {
            this.id = id;
            this.status = status;
            this.result = result;
        }
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getResult() { return result; }
        public void setResult(String result) { this.result = result; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }
}