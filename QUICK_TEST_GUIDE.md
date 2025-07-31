# Context Engine MCP - Quick Test Guide

## 🚀 Quick Setup (5 minutes)

```bash
# 1. Install
cd context-engine-mcp
npm install
npm run build

# 2. Add to Claude config
# macOS: ~/Library/Application Support/Claude/claude_desktop_config.json
{
  "mcpServers": {
    "context-engine": {
      "command": "node",
      "args": ["$(pwd)/build/mcp-server.js"]
    }
  }
}

# 3. Restart Claude Desktop
```

## ✅ Test MCP Tools in Claude

### Test 1: Code Classification
```
Use the classify_code tool on this:
public class PaymentService { 
    void processPayment(Order o) { 
        gateway.charge(o.getTotal()); 
    } 
}
```

### Test 2: Code Pruning
```
Use prune_code to remove boilerplate from this code, focusing on "payment processing":

import java.util.*;
import com.example.*;

/** Payment service for processing transactions */
@Service
@Transactional
public class PaymentService {
    
    // Constructor
    public PaymentService() { }
    
    // Getters and setters
    public Gateway getGateway() { return gateway; }
    public void setGateway(Gateway g) { this.gateway = g; }
    
    // Main logic
    public Receipt processPayment(Order order) {
        validate(order);
        return gateway.charge(order.getTotal());
    }
}
```

### Test 3: Dependency Detection
```
Use detect_dependencies on this code:

public class OrderService {
    private UserService userService;
    private PaymentService paymentService;
    private EmailService emailService;
    
    public Order createOrder(Long userId, List<Item> items) {
        User user = userService.findById(userId);
        Payment payment = paymentService.process(calculateTotal(items));
        emailService.sendConfirmation(user.getEmail());
        return new Order(user, items, payment);
    }
}
```

### Test 4: Smart Summarization
```
Use summarize_code on this simple class:
public class Calculator { 
    int add(int a, int b) { return a + b; } 
}

Then try it on this complex one:
public class DataProcessor {
    public Result analyze(Stream<Data> stream, Config config) {
        return stream
            .filter(d -> d.getTimestamp().isAfter(config.start))
            .collect(Collectors.groupingBy(Data::getCategory))
            .entrySet().parallelStream()
            .map(e -> processCategory(e.getKey(), e.getValue()))
            .reduce(Result::merge)
            .orElse(Result.empty());
    }
}
```

### Test 5: Context Optimization
```
Use optimize_context to reduce this code by 70%:

[Paste any large code file here]
```

### Test 6: Cost Estimation
```
Use estimate_cost to calculate:
- Operation: classify
- Code size: 1000 characters  
- Count: 5000 files

How much would this cost with different models?
```

## 🧪 Test REST API

```bash
# Test classification
curl -X POST http://localhost:8080/api/code/classify \
  -H "Content-Type: application/json" \
  -d '{"code": "public class Test { }"}'

# Test pruning
curl -X POST http://localhost:8080/api/code/prune \
  -H "Content-Type: application/json" \
  -d '{"code": "/* comments */ public class Test { }", "task": "core logic"}'

# Test dependencies
curl -X POST http://localhost:8080/api/code/dependencies \
  -H "Content-Type: application/json" \
  -d '{"code": "class A { B b; C c; }"}'
```

## 📋 Verification Checklist

- [ ] MCP server appears in Claude's tool list
- [ ] `classify_code` returns type, purpose, and complexity
- [ ] `prune_code` reduces code size by 50%+
- [ ] `detect_dependencies` lists all external classes
- [ ] `summarize_code` uses Gemini for simple, Claude for complex
- [ ] `optimize_context` achieves 70%+ reduction
- [ ] `estimate_cost` shows comparison between models

## 🎯 Real-World Test Scenario

```
I have a Java project with 100 service classes. For each class:
1. Classify its type and purpose
2. Extract dependencies
3. Create a one-line summary
4. Calculate the total cost

Start with this sample:
public class UserAuthService {
    private TokenService tokenService;
    private UserRepository userRepo;
    
    public Token authenticate(String email, String password) {
        User user = userRepo.findByEmail(email);
        if (user != null && user.checkPassword(password)) {
            return tokenService.generate(user);
        }
        throw new AuthException("Invalid credentials");
    }
}
```

## 🔍 Debug Commands

```bash
# Check if MCP server is running
ps aux | grep mcp-server

# View Claude logs (macOS)
tail -f ~/Library/Logs/Claude/mcp.log

# Test server directly
echo '{"jsonrpc":"2.0","method":"tools/list","id":1}' | node build/mcp-server.js

# Run in debug mode
DEBUG=mcp:* node build/mcp-server.js
```

## 💡 Expected Results

### Classification Result
```json
{
  "type": "service",
  "purpose": "user authentication",
  "complexity": "medium",
  "dependencies": ["TokenService", "UserRepository", "User", "Token"],
  "cost": "$0.000013"
}
```

### Pruning Result
```json
{
  "original_size": 445,
  "pruned_size": 123,
  "reduction_percent": 72,
  "cost": "$0.000026"
}
```

### Cost Comparison
```json
{
  "gemini_flash": "$1.25",
  "gemini_pro": "$6.25", 
  "claude_3": "$75.00"
}
```

## 🚨 Common Issues

1. **"Tool not found"** → Restart Claude Desktop
2. **"Permission denied"** → `chmod +x build/mcp-server.js`
3. **"Cannot find module"** → Run `npm install` again
4. **"No response"** → Check server is running: `npm run dev`

---

🎉 If all tests pass, Context Engine MCP is working correctly!