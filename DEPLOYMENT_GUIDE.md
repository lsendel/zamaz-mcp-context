# 🚀 Context Engine MCP - Deployment & Console Guide

This guide covers deployment processes, console operations, and production management for the Context Engine MCP ADK component.

## 🎯 Overview

The Context Engine MCP is a production-ready ADK (Application Development Kit) component that provides:
- ✅ **Multi-tenant workflow orchestration**
- ✅ **Agent-based processing with real LLM integration**
- ✅ **Async flow management with proper resource handling**
- ✅ **Configuration externalization and environment management**
- ✅ **Comprehensive monitoring and health checks**

## 🛠️ Deployment Options

### **Option 1: Local Development Deployment**

#### **Quick Start**
```bash
# Complete setup and validation
make install
make test-all
make run-server
```

#### **Development Server**
```bash
# Start development server with hot reload
make dev

# Access endpoints:
# - Health: http://localhost:8080/health
# - API: http://localhost:8080/api/v1
# - Swagger: http://localhost:8080/swagger-ui.html
```

### **Option 2: Docker Container Deployment**

#### **Build and Deploy**
```bash
# Build production Docker image
make docker-build

# Run container with full configuration
make docker-run

# Test container deployment
make docker-test
```

#### **Custom Docker Deployment**
```bash
# Build with custom tag
docker build -t zamaz/context-engine-mcp:v1.0.0 .

# Run with production configuration
docker run -d \
  --name context-engine-mcp \
  -p 8080:8080 \
  -e GOOGLE_CLOUD_PROJECT=zamaz-production \
  -e SPRING_PROFILES_ACTIVE=production \
  -v /path/to/credentials:/credentials:ro \
  -e GOOGLE_APPLICATION_CREDENTIALS=/credentials/key.json \
  zamaz/context-engine-mcp:v1.0.0
```

### **Option 3: Production Cloud Deployment**

#### **Google Cloud Run**
```bash
# Prepare for Cloud Run deployment
make deploy-prep

# Build and deploy to Cloud Run
gcloud run deploy context-engine-mcp \
  --source . \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated \
  --set-env-vars GOOGLE_CLOUD_PROJECT=zamaz-production
```

#### **Kubernetes Deployment**
```yaml
# deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: context-engine-mcp
spec:
  replicas: 3
  selector:
    matchLabels:
      app: context-engine-mcp
  template:
    metadata:
      labels:
        app: context-engine-mcp
    spec:
      containers:
      - name: context-engine-mcp
        image: zamaz/context-engine-mcp:v1.0.0
        ports:
        - containerPort: 8080
        env:
        - name: GOOGLE_CLOUD_PROJECT
          value: "zamaz-production"
        - name: SPRING_PROFILES_ACTIVE
          value: "production"
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1000m"
```

## 🖥️ Console and Management

### **Web Console Access**

#### **Health Dashboard**
```bash
# Access health endpoints
curl http://localhost:8080/health
curl http://localhost:8080/api/v1/org/zamaz-enterprise/health
```

**Response:**
```json
{
  "status": "healthy",
  "tenant": "zamaz-enterprise",
  "timestamp": 1704067200000,
  "components": {
    "database": "UP",
    "vertexAI": "UP",
    "memoryStore": "UP"
  }
}
```

#### **Metrics Console**
```bash
# Get tenant metrics
curl http://localhost:8080/api/v1/org/zamaz-enterprise/metrics?time_range=24h
```

**Response:**
```json
{
  "tenant": "zamaz-enterprise",
  "timeRange": "24h",
  "workflows": {
    "total": 150,
    "active": 12,
    "completed": 138,
    "failed": 0
  },
  "agents": {
    "requests": 2340,
    "avgLatency": 234,
    "successRate": 99.8
  },
  "memory": {
    "usedMB": 512,
    "availableMB": 1536
  }
}
```

### **Command Line Console**

#### **System Status**
```bash
# Check system status
make validate

# Get debug information
make debug-info

# View recent logs
make logs
```

#### **Interactive Console**
```bash
# Start interactive test console
make test-scenarios

# Available operations:
# 1. Workflow Operations
# 2. Agent Orchestration  
# 3. Memory Management
# 4. Performance Testing
# 5. Configuration Management
```

### **Management API**

#### **Tenant Management**
```bash
# Create new tenant workflow
curl -X POST http://localhost:8080/api/v1/org/new-tenant/workflow/create \
  -H "Content-Type: application/json" \
  -d '{
    "definition": {
      "name": "tenant-onboarding",
      "nodes": [...],
      "edges": [...]
    }
  }'
```

#### **Agent Orchestration**
```bash
# Orchestrate multi-agent workflow
curl -X POST http://localhost:8080/api/v1/org/zamaz-enterprise/agents/orchestrate \
  -H "Content-Type: application/json" \
  -d '{
    "request_id": "orchestration-001",
    "description": "Analyze Q4 inventory optimization",
    "preferred_agents": ["DATA_PROCESSOR", "PLANNING_AGENT"],
    "context": {
      "inventory_value": 2500000,
      "target_margin": 0.28
    }
  }'
```

## 📊 Monitoring and Observability

### **Health Checks**

#### **Application Health**
```bash
# Basic health check
curl http://localhost:8080/health

# Detailed health with components
curl http://localhost:8080/health/details

# Tenant-specific health
curl http://localhost:8080/api/v1/org/{orgId}/health
```

#### **Performance Monitoring**
```bash
# Performance metrics
make test-performance

# Load testing
make test-load

# Memory monitoring
make test-memory
```

### **Logging**

#### **Application Logs**
```bash
# View application logs
tail -f server.log

# Docker container logs
docker logs context-engine-mcp

# Kubernetes logs
kubectl logs deployment/context-engine-mcp
```

#### **Structured Logging**
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "level": "INFO",
  "logger": "com.zamaz.adk.integration.UnifiedContextEngine",
  "message": "Workflow executed successfully",
  "context": {
    "workflowId": "workflow-001",
    "tenantId": "zamaz-enterprise",
    "executionTime": 1234,
    "status": "COMPLETED"
  }
}
```

## 🔧 Configuration Management

### **Environment Configuration**

#### **Development Environment**
```bash
# Configure development environment
make setup-env

# Environment variables
export GOOGLE_CLOUD_PROJECT=zamaz-development
export SPRING_PROFILES_ACTIVE=development
export DEBUG_ENABLED=true
```

#### **Production Environment**
```bash
# Production configuration in application-production.yml
google:
  cloud:
    project: zamaz-production
    location: us-central1
    
ai:
  models:
    gemini:
      pro:
        temperature: 0.7
        maxOutputTokens: 2048

resources:
  shutdown:
    gracefulTimeoutSeconds: 60
    forceTimeoutSeconds: 30
```

### **Runtime Configuration**

#### **Dynamic Configuration Updates**
```bash
# Update configuration via API
curl -X PUT http://localhost:8080/admin/config \
  -H "Content-Type: application/json" \
  -d '{
    "ai.models.gemini.pro.temperature": 0.8,
    "context.engine.maxConcurrentWorkflows": 100
  }'
```

## 🚀 Production Operations

### **Deployment Pipeline**

#### **CI/CD Pipeline**
```yaml
# .github/workflows/deploy.yml
name: Deploy Context Engine MCP
on:
  push:
    branches: [main]

jobs:
  test-and-deploy:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    
    - name: Setup Java
      uses: actions/setup-java@v3
      with:
        java-version: '21'
        
    - name: Install and Test
      run: |
        make install
        make test-all
        make validation-tests
        
    - name: Build Docker Image
      run: make docker-build
      
    - name: Deploy to Production
      run: make deploy-prep
```

### **Scaling Operations**

#### **Horizontal Scaling**
```bash
# Kubernetes horizontal scaling
kubectl scale deployment context-engine-mcp --replicas=5

# Docker Swarm scaling
docker service scale context-engine-mcp=5
```

#### **Vertical Scaling**
```yaml
# Update resource limits
resources:
  requests:
    memory: "2Gi"
    cpu: "1000m"
  limits:
    memory: "4Gi"
    cpu: "2000m"
```

### **Backup and Recovery**

#### **Data Backup**
```bash
# Backup tenant configurations
curl http://localhost:8080/admin/backup/tenants > tenants-backup.json

# Backup workflow definitions
curl http://localhost:8080/admin/backup/workflows > workflows-backup.json
```

#### **Disaster Recovery**
```bash
# Restore from backup
curl -X POST http://localhost:8080/admin/restore/tenants \
  -H "Content-Type: application/json" \
  -d @tenants-backup.json
```

## 🔒 Security Operations

### **Access Control**

#### **API Authentication**
```bash
# Service account authentication
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json

# API key authentication
curl -H "Authorization: Bearer $API_KEY" \
  http://localhost:8080/api/v1/org/zamaz-enterprise/health
```

#### **Network Security**
```bash
# Configure firewall rules
gcloud compute firewall-rules create context-engine-mcp-allow \
  --allow tcp:8080 \
  --source-ranges 10.0.0.0/8 \
  --target-tags context-engine-mcp
```

### **Security Monitoring**
```bash
# Run security validation
make test-security

# Check for vulnerabilities
make validate

# Audit security logs
grep "SECURITY" server.log
```

## 📈 Performance Optimization

### **Performance Tuning**

#### **JVM Optimization**
```bash
# Production JVM settings
export JAVA_OPTS="-Xmx4g -Xms2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Start with optimized settings
java $JAVA_OPTS -jar context-engine-mcp.jar
```

#### **Database Optimization**
```yaml
# Connection pool configuration
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000
```

### **Performance Monitoring**
```bash
# Monitor performance metrics
make test-performance

# Load testing
make test-load

# Memory profiling
java -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -jar app.jar
```

## 🆘 Troubleshooting

### **Common Issues**

#### **Startup Issues**
```bash
# Check dependencies
make check-deps

# Validate configuration
make validate

# View startup logs
tail -f server.log | grep -i error
```

#### **Performance Issues**
```bash
# Check memory usage
make test-memory

# Profile performance
make test-performance

# Check thread pools
jstack <pid> | grep -A 5 "pool"
```

#### **Integration Issues**
```bash
# Test Google Cloud connectivity
make test-gcloud

# Validate API endpoints
make test-real-mcp

# Check authentication
gcloud auth list
```

## 📞 Support and Maintenance

### **Health Monitoring**
```bash
# Automated health checks
watch -n 30 'curl -s http://localhost:8080/health | jq .'

# Alert on failures
curl http://localhost:8080/health | grep -q "healthy" || echo "ALERT: Service unhealthy"
```

### **Maintenance Operations**
```bash
# Graceful shutdown
curl -X POST http://localhost:8080/admin/shutdown

# Restart service
systemctl restart context-engine-mcp

# Update configuration
make setup-env && systemctl reload context-engine-mcp
```

---

## 🎯 Quick Reference

### **Essential Commands**
```bash
# Setup and install
make install

# Test everything
make test-all

# Start server
make run-server

# Deploy to production
make deploy-prep

# Monitor health
curl http://localhost:8080/health
```

### **Support Contacts**
- **Technical Support**: Use `make debug-info` for system diagnostics
- **Documentation**: See `TESTING_GUIDE.md` for testing procedures
- **Issues**: Create issues in the project repository

**🚀 The Context Engine MCP ADK component is now production-ready with comprehensive deployment and console management capabilities!**