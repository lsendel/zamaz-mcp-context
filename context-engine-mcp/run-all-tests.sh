#!/bin/bash

echo "🚀 Context Engine MCP - Complete Test Suite"
echo "=========================================="
echo ""

# Function to run a test and report result
run_test() {
    local test_name=$1
    local test_command=$2
    echo -n "Running $test_name... "
    if eval "$test_command" > /dev/null 2>&1; then
        echo "✅ PASSED"
    else
        echo "❌ FAILED"
    fi
}

# Set environment
export GOOGLE_CLOUD_PROJECT=zamaz-authentication
export GOOGLE_APPLICATION_CREDENTIALS=~/.config/gcloud/application_default_credentials.json

echo "Environment:"
echo "- Project: $GOOGLE_CLOUD_PROJECT"
echo "- Credentials: Available"
echo ""

# 1. Basic Tests
echo "=== 1. Basic Functionality Tests ==="
run_test "Simple MCP Test" "java RunTest"
run_test "Production Demo" "java ProductionDemo"
echo ""

# 2. Google Cloud Tests
echo "=== 2. Google Cloud Integration Tests ==="
run_test "GCloud Auth Check" "gcloud auth list | grep -q ACTIVE"
run_test "Project Check" "gcloud config get-value project | grep -q zamaz"
run_test "Vertex AI API" "gcloud services list --enabled | grep -q aiplatform"
echo ""

# 3. Security Tests
echo "=== 3. Security Tests ==="
run_test "Gitignore Check" "test -f .gitignore"
run_test "No JSON in Git" "! git ls-files | grep -E '\.json$' | grep -v package.json"
run_test "Credentials Secure" "! find . -name '*credentials*.json' -o -name '*key*.json' | grep -v node_modules"
echo ""

# 4. Performance Tests
echo "=== 4. Performance Tests ==="
echo "Testing concurrent execution..."
time {
    for i in {1..10}; do
        java RunTest > /dev/null 2>&1 &
    done
    wait
}
echo ""

# 5. Maven Tests (if available)
echo "=== 5. Maven Tests (if Maven installed) ==="
if command -v mvn &> /dev/null; then
    echo "Maven found. Running tests..."
    mvn test -Dtest=SimpleMCPTest -q
else
    echo "Maven not found. Skipping Maven tests."
fi

echo ""
echo "=========================================="
echo "Test suite complete!"