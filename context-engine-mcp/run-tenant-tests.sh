#!/bin/bash

# Run all tenant-aware tests for Google ADK services
# Requires GOOGLE_CLOUD_PROJECT environment variable

set -e

echo "🚀 Running All Tenant-Aware Tests"
echo "================================="
echo ""

# Check environment
if [ -z "$GOOGLE_CLOUD_PROJECT" ]; then
    echo "❌ Error: GOOGLE_CLOUD_PROJECT environment variable not set"
    echo "Please run: export GOOGLE_CLOUD_PROJECT=your-project-id"
    exit 1
fi

echo "✓ Using Google Cloud Project: $GOOGLE_CLOUD_PROJECT"
echo ""

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Function to run a test class
run_test() {
    local test_class=$1
    local test_name=$2
    
    echo -e "${YELLOW}Running $test_name...${NC}"
    
    if mvn test -Dtest=$test_class 2>&1 | tee test-output.log | grep -E "(Tests run:|FAILURES|ERROR)"; then
        if grep -q "FAILURES" test-output.log || grep -q "ERROR" test-output.log; then
            echo -e "${RED}✗ $test_name FAILED${NC}"
            return 1
        else
            echo -e "${GREEN}✓ $test_name PASSED${NC}"
            return 0
        fi
    else
        echo -e "${RED}✗ $test_name FAILED TO RUN${NC}"
        return 1
    fi
}

# If Maven is not available, use direct Java execution
if ! command -v mvn &> /dev/null; then
    echo "Maven not found, using direct Java execution..."
    
    # Compile test classes
    echo "Compiling test classes..."
    javac -cp "lib/*:." src/test/java/com/zamaz/adk/*.java
    
    # Run tests directly
    java -cp "lib/*:src/test/java:." org.junit.platform.console.ConsoleLauncher \
        --select-class com.zamaz.adk.TenantAwareUnitTest \
        --select-class com.zamaz.adk.TenantAwareIntegrationTest \
        --select-class com.zamaz.adk.TenantAwarePerformanceTest
else
    # Run tests with Maven
    echo "Running tests with Maven..."
    
    # Unit Tests
    echo ""
    echo "1️⃣ UNIT TESTS"
    echo "=============="
    run_test "com.zamaz.adk.TenantAwareUnitTest" "Unit Tests"
    UNIT_RESULT=$?
    
    # Integration Tests
    echo ""
    echo "2️⃣ INTEGRATION TESTS"
    echo "===================="
    run_test "com.zamaz.adk.TenantAwareIntegrationTest" "Integration Tests"
    INTEGRATION_RESULT=$?
    
    # Performance Tests
    echo ""
    echo "3️⃣ PERFORMANCE TESTS"
    echo "===================="
    run_test "com.zamaz.adk.TenantAwarePerformanceTest" "Performance Tests"
    PERFORMANCE_RESULT=$?
    
    # Summary
    echo ""
    echo "📊 TEST SUMMARY"
    echo "==============="
    
    if [ $UNIT_RESULT -eq 0 ]; then
        echo -e "${GREEN}✓ Unit Tests: PASSED${NC}"
    else
        echo -e "${RED}✗ Unit Tests: FAILED${NC}"
    fi
    
    if [ $INTEGRATION_RESULT -eq 0 ]; then
        echo -e "${GREEN}✓ Integration Tests: PASSED${NC}"
    else
        echo -e "${RED}✗ Integration Tests: FAILED${NC}"
    fi
    
    if [ $PERFORMANCE_RESULT -eq 0 ]; then
        echo -e "${GREEN}✓ Performance Tests: PASSED${NC}"
    else
        echo -e "${RED}✗ Performance Tests: FAILED${NC}"
    fi
    
    # Overall result
    if [ $UNIT_RESULT -eq 0 ] && [ $INTEGRATION_RESULT -eq 0 ] && [ $PERFORMANCE_RESULT -eq 0 ]; then
        echo ""
        echo -e "${GREEN}🎉 ALL TESTS PASSED!${NC}"
        exit 0
    else
        echo ""
        echo -e "${RED}❌ SOME TESTS FAILED${NC}"
        exit 1
    fi
fi

# Clean up
rm -f test-output.log