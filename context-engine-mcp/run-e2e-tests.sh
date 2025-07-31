#!/bin/bash

echo "🎭 Running E2E Tests with Playwright"
echo "===================================="

# Set up environment
export GOOGLE_CLOUD_PROJECT=${GOOGLE_CLOUD_PROJECT:-zamaz-authentication}
export TEST_GCP_PROJECT=$GOOGLE_CLOUD_PROJECT

# Create directories for test artifacts
mkdir -p target/screenshots
mkdir -p target/videos

# Install Playwright browsers if not already installed
echo "📦 Installing Playwright browsers..."
mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"

# Run the tests
echo "🚀 Starting E2E tests..."
mvn test -Dtest=ChatConsoleE2ETest -DfailIfNoTests=false

# Check test results
if [ $? -eq 0 ]; then
    echo "✅ All E2E tests passed!"
else
    echo "❌ Some tests failed. Check screenshots and videos in target/ directory"
    exit 1
fi

# Optional: Open test report
if command -v open &> /dev/null; then
    echo "📊 Opening test report..."
    open target/surefire-reports/index.html 2>/dev/null || true
fi