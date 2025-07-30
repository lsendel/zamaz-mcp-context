# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is the Zamaz MCP (Model Context Protocol) server project, intended to bridge Zamaz's e-commerce technology stack with AI capabilities. Zamaz is a retail technology-driven e-commerce company specializing in Amazon FBA operations, inventory forecasting, and supply chain management.

## Initial Setup Commands

Since this is a new project, start by initializing it:

```bash
# Initialize Node.js project
npm init -y

# Install MCP SDK and TypeScript
npm install @modelcontextprotocol/sdk
npm install -D typescript @types/node tsx

# Initialize TypeScript
npx tsc --init
```

## Development Commands

Once the project is set up, use these commands:

```bash
# Run the MCP server
npm run dev

# Build TypeScript
npm run build

# Run tests
npm test

# Type checking
npm run typecheck

# Linting
npm run lint
```

## Architecture Guidelines

### MCP Server Structure

The MCP server should follow this pattern:

```typescript
// src/server/index.ts
import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';

// Initialize server with resources, tools, and prompts
```

### Resource Implementation

Resources should provide read-only access to Zamaz data:
- Inventory levels and forecasts
- Sales analytics
- Product catalog
- Supply chain status

### Tool Implementation

Tools should enable actions on Zamaz systems:
- Calculate inventory forecasts
- Optimize pricing strategies
- Generate reports
- Analyze market trends

## Key Considerations

1. **Security**: Never expose sensitive credentials or API keys in the MCP server
2. **Rate Limiting**: Implement appropriate rate limiting for external API calls
3. **Error Handling**: Provide clear error messages for debugging
4. **Testing**: Write unit tests for all resources and tools
5. **Documentation**: Keep README.md updated with usage examples

## Zamaz-Specific Context

When implementing features, consider:
- Amazon FBA constraints and requirements
- Multi-channel inventory synchronization
- Real-time sales data processing
- Supply chain optimization needs
- Brand portfolio management (Ecomoist, Bella Dispensa, etc.)

## MCP Best Practices

1. Keep resources focused and single-purpose
2. Make tool names descriptive and action-oriented
3. Use TypeScript for better type safety
4. Follow the MCP SDK examples for standard patterns
5. Test with the MCP Inspector during development