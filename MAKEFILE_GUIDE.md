# Makefile Quick Reference

## Most Common Commands

### 🚀 Quick Start
```bash
make install          # Install and compile everything
make test            # Run all tests
make run-server      # Start the MCP server
```

### 🧪 Testing Commands
```bash
make test-simple     # Run basic functionality test
make test-demo       # Run production demo
make test-scenarios  # Interactive test menu
make test-gcloud     # Test Google Cloud APIs
make test-maven      # Full Maven test suite
make test-security   # Security validation
```

### 👨‍💻 Development
```bash
make dev             # Start in development mode
make compile         # Compile all Java files
make clean           # Clean build artifacts
```

### 🔧 Setup
```bash
make setup           # Configure credentials
make setup-env       # Set environment variables
make validate        # Check installation
```

### 🐳 Docker
```bash
make docker-build    # Build Docker image
make docker-run      # Run container
```

### ⚡ Shortcuts
```bash
make t               # Alias for 'make test'
make r               # Alias for 'make run-server'
make c               # Alias for 'make compile'
make d               # Alias for 'make dev'
```

## Workflow Examples

### First Time Setup
```bash
make install
make setup
source .env
make test
```

### Daily Development
```bash
make compile
make test-simple
make dev
```

### Before Committing
```bash
make test-security
make test
make clean
```

### Full Testing
```bash
make all             # Install and test everything
make test-real-production  # Production test with Google Cloud
```

## Tips

- Run `make` or `make help` to see all available commands
- Most commands work from the root directory (`zamaz-mcp-context`)
- The Makefile automatically changes to the correct subdirectory
- Use `make validate` to check if everything is installed correctly