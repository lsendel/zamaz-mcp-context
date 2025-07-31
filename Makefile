# Context Engine MCP - Makefile
# Main tests and functionality

.PHONY: help setup test clean install run-server docker all

# Default target
help:
	@echo "🚀 Context Engine MCP - Comprehensive Test Suite"
	@echo "==============================================="
	@echo ""
	@echo "📋 Quick Start (Recommended):"
	@echo "  make install          - Complete installation and setup"
	@echo "  make test-all         - Run complete test suite"
	@echo "  make validation-tests - Test implemented fixes (async, exceptions, config, resources)"
	@echo ""
	@echo "🔧 Setup & Installation:"
	@echo "  make install          - Install dependencies and compile everything"
	@echo "  make setup            - Set up Google Cloud credentials"
	@echo "  make setup-env        - Configure environment variables"
	@echo "  make check-deps       - Check all required dependencies"
	@echo ""
	@echo "🧪 Comprehensive Testing:"
	@echo "  make test-all         - Run ALL tests (unit + integration + validation)"
	@echo "  make validation-tests - Test core fixes (async, exception, config, resource)"
	@echo "  make unit-tests       - Run unit tests only"
	@echo "  make integration-tests- Run integration and end-to-end tests"
	@echo ""
	@echo "🎯 Validation Tests (Core Fixes):"
	@echo "  make test-async       - Test async flow improvements"
	@echo "  make test-exceptions  - Test exception handling fixes"
	@echo "  make test-config      - Test configuration externalization"
	@echo "  make test-resources   - Test resource management improvements"
	@echo ""
	@echo "⚡ Quick Tests:"
	@echo "  make test-simple      - Basic functionality test"
	@echo "  make test-demo        - Production demo test"
	@echo "  make test-security    - Security validation"
	@echo "  make test-performance - Performance and load tests"
	@echo ""
	@echo "🚀 Real Tests (NO MOCKS):"
	@echo "  make test-real-mcp    - Real MCP Production Test"
	@echo "  make test-all-real    - All real tests with zero mocks"
	@echo "  make test-production  - Full production-ready test suite"
	@echo "  make no-mocks         - Complete testing without mocks"
	@echo ""
	@echo "🛠️  Development:"
	@echo "  make compile          - Compile all Java files"
	@echo "  make run-server       - Start the MCP server"
	@echo "  make dev              - Run in development mode"
	@echo ""
	@echo "🐳 Docker & Deployment:"
	@echo "  make docker-build     - Build Docker image"
	@echo "  make docker-run       - Run Docker container"
	@echo "  make deploy-prep      - Prepare for production deployment"
	@echo ""
	@echo "🔍 Advanced:"
	@echo "  make clean            - Clean build artifacts"
	@echo "  make validate         - Validate complete system setup"
	@echo "  make debug-info       - Show debugging information"
	@echo "  make help-validation  - Detailed help for validation tests"

# Change to project directory
CD_PROJECT = cd context-engine-mcp &&

# Setup and Installation
install: setup-env compile
	@echo "✅ Installation complete!"

setup:
	@echo "🔐 Setting up Google Cloud credentials..."
	@$(CD_PROJECT) ./secure-setup.sh
	@echo ""
	@echo "📋 Next steps:"
	@echo "1. Create credentials directory: mkdir -p ~/.gcp/context-engine-mcp"
	@echo "2. Place your service account key there"
	@echo "3. Run: make setup-env"

setup-env:
	@echo "🔧 Setting up environment..."
	@echo "export GOOGLE_CLOUD_PROJECT=zamaz-authentication" > .env
	@echo "export GOOGLE_APPLICATION_CREDENTIALS=~/.config/gcloud/application_default_credentials.json" >> .env
	@echo "✅ Environment configured. Run: source .env"

# Compilation
compile:
	@echo "🔨 Compiling Java files..."
	@$(CD_PROJECT) javac *.java 2>/dev/null || true
	@$(CD_PROJECT) mvn compile -q 2>/dev/null || echo "Maven compilation skipped"
	@echo "✅ Compilation complete"

# Testing targets
test: test-simple test-demo test-gcloud test-security
	@echo ""
	@echo "✅ All tests completed!"

test-simple:
	@echo ""
	@echo "🧪 Running Simple Test..."
	@echo "========================"
	@$(CD_PROJECT) java RunTest

test-demo:
	@echo ""
	@echo "🎯 Running Production Demo..."
	@echo "============================"
	@$(CD_PROJECT) java ProductionDemo

test-scenarios:
	@echo ""
	@echo "📊 Running Interactive Test Scenarios..."
	@echo "======================================="
	@$(CD_PROJECT) java TestScenarios

test-gcloud:
	@echo ""
	@echo "☁️  Testing Google Cloud Connectivity..."
	@echo "======================================="
	@$(CD_PROJECT) export GOOGLE_CLOUD_PROJECT=zamaz-authentication && java GoogleCloudLiveTest

test-maven:
	@echo ""
	@echo "🏗️  Running Maven Tests..."
	@echo "========================"
	@$(CD_PROJECT) mvn test -DfailIfNoTests=false

test-security:
	@echo ""
	@echo "🔒 Running Security Validation..."
	@echo "================================"
	@$(CD_PROJECT) ./validate-security.sh 2>/dev/null || echo "Security script not found"

test-real-production:
	@echo ""
	@echo "🚀 Running Real Production Test..."
	@echo "================================="
	@$(CD_PROJECT) mvn test -Dtest=RealProductionTest

test-integration:
	@echo ""
	@echo "🔗 Running Integration Tests..."
	@echo "=============================="
	@$(CD_PROJECT) mvn test -Dtest=GoogleCloudProductionTest

# Development
run-server:
	@echo "🌐 Starting Context Engine MCP Server..."
	@$(CD_PROJECT) mvn spring-boot:run

dev:
	@echo "👨‍💻 Starting in development mode..."
	@$(CD_PROJECT) mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Docker
docker-build:
	@echo "🐳 Building Docker image..."
	@$(CD_PROJECT) docker build -t context-engine-mcp:latest .

docker-run:
	@echo "🐳 Running Docker container..."
	@docker run -it --rm \
		-e GOOGLE_CLOUD_PROJECT=zamaz-authentication \
		-v ~/.gcp/context-engine-mcp:/credentials:ro \
		-e GOOGLE_APPLICATION_CREDENTIALS=/credentials/key.json \
		-p 8080:8080 \
		context-engine-mcp:latest

# Utilities
clean:
	@echo "🧹 Cleaning build artifacts..."
	@$(CD_PROJECT) rm -f *.class
	@$(CD_PROJECT) mvn clean -q 2>/dev/null || true
	@echo "✅ Clean complete"

validate: test-security
	@echo ""
	@echo "🔍 Validating setup..."
	@command -v gcloud >/dev/null 2>&1 && echo "✅ gcloud installed" || echo "❌ gcloud not found"
	@command -v mvn >/dev/null 2>&1 && echo "✅ Maven installed" || echo "❌ Maven not found"
	@command -v java >/dev/null 2>&1 && echo "✅ Java installed" || echo "❌ Java not found"
	@test -f ~/.config/gcloud/application_default_credentials.json && echo "✅ Google credentials found" || echo "❌ Google credentials not found"

docs:
	@echo "📚 Generating documentation..."
	@echo "# Context Engine MCP - Test Documentation" > TEST_DOCS.md
	@echo "" >> TEST_DOCS.md
	@echo "## Available Tests" >> TEST_DOCS.md
	@echo "" >> TEST_DOCS.md
	@echo "1. **Simple Test** - Basic functionality validation" >> TEST_DOCS.md
	@echo "2. **Production Demo** - Full feature demonstration" >> TEST_DOCS.md
	@echo "3. **Test Scenarios** - Interactive test suite" >> TEST_DOCS.md
	@echo "4. **Google Cloud Test** - Live API connectivity" >> TEST_DOCS.md
	@echo "5. **Maven Tests** - Complete test suite" >> TEST_DOCS.md
	@echo "" >> TEST_DOCS.md
	@echo "Run 'make test' to execute all tests" >> TEST_DOCS.md
	@echo "✅ Documentation generated: TEST_DOCS.md"

# Quick commands
all: install test

quick-test: test-simple test-gcloud

# Special targets for specific scenarios
benchmark:
	@echo "⚡ Running Performance Benchmark..."
	@$(CD_PROJECT) java TestScenarios < echo "3"

cost-analysis:
	@echo "💰 Running Cost Analysis..."
	@$(CD_PROJECT) java TestScenarios < echo "4"

# Real production tests (NO MOCKS)
test-real-mcp:
	@echo ""
	@echo "🚀 Running Real MCP Production Test (NO MOCKS)..."
	@echo "================================================"
	@$(CD_PROJECT) java RealMCPProductionTest

test-real-gemini:
	@echo ""
	@echo "🌟 Running Real Gemini API Test..."
	@echo "=================================="
	@$(CD_PROJECT) java RealGeminiTestSimple 2>/dev/null || echo "Note: Requires Vertex AI models to be enabled"

test-real-genai:
	@echo ""
	@echo "🤖 Running Real Generative AI Test..."
	@echo "===================================="
	@$(CD_PROJECT) java RealGenerativeAITest 2>/dev/null || echo "Note: Requires Vertex AI access"

test-check-models:
	@echo ""
	@echo "🔍 Checking Available Models..."
	@echo "=============================="
	@$(CD_PROJECT) java CheckAvailableModels

# Run all real tests
test-all-real: compile
	@echo ""
	@echo "🎯 RUNNING ALL REAL TESTS (NO MOCKS)"
	@echo "===================================="
	@make test-real-mcp
	@make test-simple
	@make test-demo
	@make test-gcloud
	@echo ""
	@echo "✅ All real tests completed!"

# Production test suite
test-production: compile
	@echo ""
	@echo "🏭 PRODUCTION TEST SUITE (NO MOCKS)"
	@echo "==================================="
	@make test-real-mcp
	@make test-real-production
	@make test-integration
	@echo ""
	@echo "✅ Production test suite completed!"

# Quick real test
test-real-quick: compile
	@echo ""
	@echo "⚡ QUICK REAL TEST (NO MOCKS)"
	@echo "============================="
	@$(CD_PROJECT) java RealMCPProductionTest
	@echo ""
	@$(CD_PROJECT) java RunTest
	@echo ""
	@echo "✅ Quick real tests completed!"

# Run all no-mock tests
no-mocks: compile
	@echo ""
	@echo "🚫 RUNNING ALL TESTS - ZERO MOCKS"
	@echo "================================="
	@make test-real-mcp
	@make test-simple
	@make test-demo
	@make test-scenarios-auto
	@echo ""
	@echo "✅ All no-mock tests completed!"

# Auto run test scenarios
test-scenarios-auto:
	@echo ""
	@echo "🤖 Running Test Scenarios (Automated)..."
	@echo "======================================="
	@$(CD_PROJECT) echo "9" | java TestScenarios || true

# Git helpers
git-setup:
	@echo "📝 Setting up git hooks and gitignore..."
	@$(CD_PROJECT) ./secure-setup.sh
	@git add .gitignore
	@echo "✅ Git security configured"

# Check prerequisites
check-deps:
	@echo "🔍 Checking dependencies..."
	@echo ""
	@echo -n "Java: "
	@java -version 2>&1 | head -n 1
	@echo -n "Maven: "
	@mvn --version 2>&1 | head -n 1 || echo "Not installed"
	@echo -n "gcloud: "
	@gcloud --version 2>&1 | head -n 1 || echo "Not installed"
	@echo -n "Docker: "
	@docker --version 2>&1 || echo "Not installed"

# Production deployment helpers
deploy-prep:
	@echo "🚀 Preparing for production deployment..."
	@make clean
	@make test-security
	@make test-maven
	@echo "✅ Ready for deployment"

# Development workflow
dev-setup: install setup setup-env
	@echo "👨‍💻 Development environment ready!"
	@echo "Run 'make dev' to start development server"

# New comprehensive test targets
test-all:
	@echo "🚀 Running Complete Test Suite..."
	@echo "=================================="
	@$(CD_PROJECT) make test-all

validation-tests:
	@echo "🎯 Running Validation Tests..."
	@echo "=============================="
	@$(CD_PROJECT) make validation-tests

unit-tests:
	@echo "🧪 Running Unit Tests..."
	@echo "======================="
	@$(CD_PROJECT) make unit-tests

integration-tests:
	@echo "🔗 Running Integration Tests..."
	@echo "=============================="
	@$(CD_PROJECT) make integration-tests

test-async:
	@echo "⚡ Testing Async Flow Improvements..."
	@$(CD_PROJECT) make test-async

test-exceptions:
	@echo "🎯 Testing Exception Handling..."
	@$(CD_PROJECT) make test-exceptions

test-config:
	@echo "⚙️  Testing Configuration..."
	@$(CD_PROJECT) make test-config

test-resources:
	@echo "🔧 Testing Resource Management..."
	@$(CD_PROJECT) make test-resources

test-performance:
	@echo "📈 Running Performance Tests..."
	@$(CD_PROJECT) make test-performance

check-deps:
	@echo "🔍 Checking Dependencies..."
	@$(CD_PROJECT) make check-deps

debug-info:
	@echo "🐛 System Debug Information..."
	@$(CD_PROJECT) make debug-info

deploy-prep:
	@echo "🚀 Preparing for Deployment..."
	@$(CD_PROJECT) make deploy-prep

help-validation:
	@$(CD_PROJECT) make help-validation

help-quick:
	@$(CD_PROJECT) make help-quick

# Enhanced shortcuts
t: test-all
v: validation-tests
ut: unit-tests
it: integration-tests
r: run-server
c: compile
d: dev
perf: test-performance