package com.zamaz.adk.e2e;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.*;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Paths;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests for the Chat Console using Playwright
 * Tests real LLM integration without any mocks
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ChatConsoleE2ETest {
    
    @LocalServerPort
    private int port;
    
    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;
    
    @BeforeAll
    void setupPlaywright() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
            .setHeadless(System.getenv("CI") != null) // Headless in CI, headed locally
            .setSlowMo(50) // Slow down for debugging
        );
    }
    
    @BeforeEach
    void setupPage() {
        context = browser.newContext(new Browser.NewContextOptions()
            .setViewportSize(1280, 720)
            .setRecordVideoDir(Paths.get("target/videos"))
        );
        
        page = context.newPage();
        page.navigate("http://localhost:" + port + "/console.html");
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }
    
    @AfterEach
    void teardownPage() {
        if (page != null) {
            page.screenshot(new Page.ScreenshotOptions()
                .setPath(Paths.get("target/screenshots/test-" + 
                    System.currentTimeMillis() + ".png"))
            );
            page.close();
        }
        if (context != null) {
            context.close();
        }
    }
    
    @AfterAll
    void teardownPlaywright() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }
    
    @Test
    @DisplayName("Should load chat console successfully")
    void shouldLoadChatConsole() {
        // Verify page loaded
        assertThat(page.title()).contains("ADK Agent Console");
        
        // Check main elements are present
        assertThat(page.locator("h1").textContent()).contains("ADK Agent Console");
        assertThat(page.locator(".status-indicator").textContent()).contains("Connected");
        
        // Verify agents are listed
        Locator agentList = page.locator(".agent-list .agent-item");
        assertThat(agentList.count()).isGreaterThan(0);
        
        // Verify input area is present
        assertThat(page.locator("#messageInput").isVisible()).isTrue();
        assertThat(page.locator("#sendButton").isVisible()).isTrue();
    }
    
    @Test
    @DisplayName("Should send message and receive real LLM response")
    void shouldSendMessageAndReceiveResponse() {
        // Type a message
        Locator messageInput = page.locator("#messageInput");
        messageInput.fill("What tools are available?");
        
        // Send the message
        page.locator("#sendButton").click();
        
        // Wait for response with timeout
        Locator response = page.locator(".message:has(.agent-avatar)").last()
            .locator(".message-content");
        
        response.waitFor(new Locator.WaitForOptions()
            .setTimeout(30000) // 30 seconds for real LLM response
        );
        
        // Verify response is not a demo/mock response
        String responseText = response.textContent();
        assertThat(responseText).isNotEmpty();
        assertThat(responseText).doesNotContain("demo mode");
        assertThat(responseText).doesNotContain("Currently running in demo mode");
        
        // Response should mention actual tools or capabilities
        assertThat(responseText.toLowerCase()).satisfiesAnyOf(
            text -> assertThat(text).contains("tool"),
            text -> assertThat(text).contains("available"),
            text -> assertThat(text).contains("help"),
            text -> assertThat(text).contains("assist")
        );
    }
    
    @Test
    @DisplayName("Should switch between agents")
    void shouldSwitchBetweenAgents() {
        // Click on Code Analyzer agent
        Locator codeAgent = page.locator(".agent-item:has-text('Code Analyzer')");
        codeAgent.click();
        
        // Verify agent is selected
        assertThat(codeAgent.getAttribute("class")).contains("active");
        
        // Verify header updated
        assertThat(page.locator("#activeAgentName").textContent()).isEqualTo("Code Analyzer");
        
        // Send a code-related message
        Locator messageInput = page.locator("#messageInput");
        messageInput.fill("Analyze this code: function add(a, b) { return a + b; }");
        page.locator("#sendButton").click();
        
        // Wait for response
        page.waitForSelector(".message:has(.agent-avatar)", new Page.WaitForSelectorOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(30000)
        );
        
        // Verify response is code-related
        String response = page.locator(".message:has(.agent-avatar)").last()
            .locator(".message-content").textContent();
        
        assertThat(response.toLowerCase()).satisfiesAnyOf(
            text -> assertThat(text).contains("function"),
            text -> assertThat(text).contains("code"),
            text -> assertThat(text).contains("analysis"),
            text -> assertThat(text).contains("simple")
        );
    }
    
    @Test
    @DisplayName("Should maintain conversation history")
    void shouldMaintainConversationHistory() {
        // Send first message
        Locator messageInput = page.locator("#messageInput");
        messageInput.fill("Remember that my favorite color is blue");
        page.locator("#sendButton").click();
        
        // Wait for first response
        page.waitForTimeout(2000);
        
        // Send follow-up message
        messageInput.fill("What is my favorite color?");
        page.locator("#sendButton").click();
        
        // Wait for second response
        Locator secondResponse = page.locator(".message:has(.agent-avatar)").nth(1)
            .locator(".message-content");
        secondResponse.waitFor(new Locator.WaitForOptions().setTimeout(30000));
        
        // Verify the agent remembers the context
        String responseText = secondResponse.textContent();
        assertThat(responseText.toLowerCase()).contains("blue");
    }
    
    @Test
    @DisplayName("Should handle multi-line input")
    void shouldHandleMultiLineInput() {
        Locator messageInput = page.locator("#messageInput");
        
        // Type multi-line message using Shift+Enter
        messageInput.fill("This is line one");
        messageInput.press("Shift+Enter");
        messageInput.type("This is line two");
        
        // Verify textarea expanded
        assertThat(messageInput.evaluate("el => el.scrollHeight > el.clientHeight"))
            .isEqualTo(true);
        
        // Send message
        page.locator("#sendButton").click();
        
        // Verify message was sent correctly
        Locator sentMessage = page.locator(".message:has(.user-avatar)").last()
            .locator(".message-content");
        
        assertThat(sentMessage.innerHTML()).contains("This is line one<br>This is line two");
    }
    
    @Test
    @DisplayName("Should show typing indicator while waiting for response")
    void shouldShowTypingIndicator() {
        // Send a message
        page.locator("#messageInput").fill("Explain quantum computing in detail");
        page.locator("#sendButton").click();
        
        // Typing indicator should appear
        Locator typingIndicator = page.locator("#typingIndicator");
        assertThat(typingIndicator.isVisible()).isTrue();
        
        // Wait for response
        page.waitForSelector(".message:has(.agent-avatar)", new Page.WaitForSelectorOptions()
            .setTimeout(30000)
        );
        
        // Typing indicator should disappear
        assertThat(typingIndicator.isVisible()).isFalse();
    }
    
    @Test
    @DisplayName("Should test MCP-specific commands")
    void shouldTestMCPCommands() {
        // Test context storage
        page.locator("#messageInput").fill("Store this context: Project name is Zamaz MCP");
        page.locator("#sendButton").click();
        page.waitForTimeout(2000);
        
        // Test context retrieval
        page.locator("#messageInput").fill("What is the project name?");
        page.locator("#sendButton").click();
        
        // Wait for response
        Locator response = page.locator(".message:has(.agent-avatar)").last()
            .locator(".message-content");
        response.waitFor(new Locator.WaitForOptions().setTimeout(30000));
        
        // Verify context was stored and retrieved
        String responseText = response.textContent();
        assertThat(responseText).containsIgnoringCase("Zamaz MCP");
    }
    
    @Test
    @DisplayName("Should handle errors gracefully")
    void shouldHandleErrorsGracefully() {
        // Send an invalid/problematic message
        page.locator("#messageInput").fill("[INVALID JSON: {{{");
        page.locator("#sendButton").click();
        
        // Wait for response
        page.waitForTimeout(5000);
        
        // Should still get a response (error handling)
        Locator messages = page.locator(".message:has(.agent-avatar)");
        assertThat(messages.count()).isGreaterThan(0);
        
        // Response should not show raw error
        String response = messages.last().locator(".message-content").textContent();
        assertThat(response).doesNotContain("Exception");
        assertThat(response).doesNotContain("Error:");
    }
    
    @Test
    @DisplayName("Should maintain agent-specific context")
    void shouldMaintainAgentSpecificContext() {
        // Send message to General Assistant
        page.locator("#messageInput").fill("My name is Test User");
        page.locator("#sendButton").click();
        page.waitForTimeout(2000);
        
        // Switch to Code Analyzer
        page.locator(".agent-item:has-text('Code Analyzer')").click();
        
        // Ask about name (should not know in this context)
        page.locator("#messageInput").fill("What is my name?");
        page.locator("#sendButton").click();
        page.waitForTimeout(2000);
        
        // Switch back to General Assistant
        page.locator(".agent-item:has-text('General Assistant')").click();
        
        // Ask about name again (should remember)
        page.locator("#messageInput").fill("What is my name?");
        page.locator("#sendButton").click();
        
        // Wait for response
        Locator response = page.locator(".message:has(.agent-avatar)").last()
            .locator(".message-content");
        response.waitFor(new Locator.WaitForOptions().setTimeout(30000));
        
        // Should remember the name in General Assistant context
        assertThat(response.textContent()).containsIgnoringCase("Test User");
    }
}