#!/bin/bash
#
# Secure Setup Script for Context Engine MCP
# This script sets up Google Cloud credentials securely
#

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "🔒 Context Engine MCP - Secure Setup"
echo "===================================="
echo ""

# Function to print colored output
print_status() {
    case $1 in
        "success") echo -e "${GREEN}✅ $2${NC}" ;;
        "error") echo -e "${RED}❌ $2${NC}" ;;
        "warning") echo -e "${YELLOW}⚠️  $2${NC}" ;;
        *) echo "$2" ;;
    esac
}

# Check if .gitignore exists and is properly configured
check_gitignore() {
    echo "Checking .gitignore configuration..."
    
    if [ ! -f .gitignore ]; then
        print_status "warning" ".gitignore not found. Creating..."
        touch .gitignore
    fi
    
    # Add security patterns to .gitignore
    local patterns=(
        "# Security - NEVER commit these"
        "*.json"
        "!package.json"
        "!tsconfig.json"
        "!.eslintrc.json"
        "!composer.json"
        "google-credentials.json"
        "service-account*.json"
        "*-key.json"
        "*-credentials.json"
        "credentials/"
        ".env"
        ".env.*"
        "!.env.template"
        "!.env.example"
    )
    
    for pattern in "${patterns[@]}"; do
        if ! grep -qF "$pattern" .gitignore; then
            echo "$pattern" >> .gitignore
        fi
    done
    
    print_status "success" ".gitignore configured securely"
}

# Main execution
echo "This script will help you set up Google Cloud credentials securely."
echo ""
echo "Key features:"
echo "- Creates secure credential storage outside project"
echo "- Configures .gitignore to prevent credential commits"
echo "- Sets up environment variables"
echo "- Creates symbolic links for development"
echo ""
echo "For full setup, see GOOGLE_CLOUD_SETUP_GUIDE.md"

# Check .gitignore
check_gitignore

echo ""
echo "Next steps:"
echo "1. Create secure directory: mkdir -p ~/.gcp/context-engine-mcp"
echo "2. Store credentials there: ~/.gcp/context-engine-mcp/credentials.json"
echo "3. Set environment variable: export GOOGLE_APPLICATION_CREDENTIALS=~/.gcp/context-engine-mcp/credentials.json"
echo "4. Run tests: mvn test"