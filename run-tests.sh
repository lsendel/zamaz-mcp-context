#!/bin/bash

# Helper script to run tests from any directory

echo "🚀 Context Engine MCP Test Runner"
echo "================================="
echo ""

# Find the correct directory
if [ -d "context-engine-mcp" ]; then
    cd context-engine-mcp
elif [ -f "pom.xml" ]; then
    # Already in correct directory
    :
else
    echo "❌ Error: Cannot find context-engine-mcp directory"
    echo "Please run from zamaz-mcp-context or context-engine-mcp directory"
    exit 1
fi

echo "📁 Working directory: $(pwd)"
echo ""

# Menu
echo "Select test to run:"
echo "1. Simple Test (java RunTest)"
echo "2. Production Demo (java ProductionDemo)"
echo "3. Interactive Test Scenarios (java TestScenarios)"
echo "4. Google Cloud Live Test (java GoogleCloudLiveTest)"
echo "5. Maven Test Suite (requires Maven)"
echo "6. All Quick Tests"
echo ""

read -p "Enter choice (1-6): " choice

case $choice in
    1)
        echo "Running Simple Test..."
        java RunTest
        ;;
    2)
        echo "Running Production Demo..."
        java ProductionDemo
        ;;
    3)
        echo "Running Interactive Test Scenarios..."
        java TestScenarios
        ;;
    4)
        echo "Running Google Cloud Live Test..."
        export GOOGLE_CLOUD_PROJECT=zamaz-authentication
        java GoogleCloudLiveTest
        ;;
    5)
        echo "Running Maven Tests..."
        if command -v mvn &> /dev/null; then
            mvn test
        else
            echo "❌ Maven not found. Please install Maven first."
        fi
        ;;
    6)
        echo "Running All Quick Tests..."
        echo ""
        echo "=== Test 1: Simple Test ==="
        java RunTest
        echo ""
        echo "=== Test 2: Production Demo ==="
        java ProductionDemo
        echo ""
        echo "=== Test 3: Google Cloud Test ==="
        export GOOGLE_CLOUD_PROJECT=zamaz-authentication
        java GoogleCloudLiveTest
        ;;
    *)
        echo "Invalid choice!"
        ;;
esac