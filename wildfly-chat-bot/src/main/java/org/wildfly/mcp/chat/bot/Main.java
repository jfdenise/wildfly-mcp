package org.wildfly.mcp.chat.bot;

import org.wildfly.mcp.chat.bot.MCPConfig.MCPServerConfig;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;
import jakarta.servlet.Servlet;
import java.net.URL;
import java.time.Duration;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.websocket.server.JettyWebSocketServlet;
import org.eclipse.jetty.websocket.server.JettyWebSocketServletFactory;

public class Main {

    public static void main(String[] args) throws Exception {
        ChatConfiguration config = ChatConfiguration.parseArguments(args);
        if (config.debug) {
            Logger logger1 = LogManager.getLogger("org.eclipse.jetty");
              Logger logger2 = LogManager.getLogger("dev.langchain4j");
            Configurator.setLevel(logger1.getName(), Level.DEBUG);      
            Configurator.setLevel(logger2.getName(), Level.DEBUG);      
        }
        MCPConfig mcpConfig = MCPConfig.parseConfig(config.configFile);
        List<McpClient> clients = new ArrayList<>();
        for (Entry<String, MCPServerConfig> entry : mcpConfig.mcpServers.entrySet()) {
            List<String> cmd = new ArrayList<>();
            cmd.add(entry.getValue().command);
            cmd.addAll(entry.getValue().args);
            McpTransport transport = new StdioMcpTransport.Builder()
                    .command(cmd)
                    .logEvents(true)
                    .build();
            McpClient mcpClient = new DefaultMcpClient.Builder()
                    .transport(transport)
                    .clientName(entry.getKey())
                    .build();
            clients.add(mcpClient);
        }
        ToolProvider toolProvider = McpToolProvider.builder()
                .mcpClients(clients)
                .build();
        ChatLanguageModel chatModel = null;
        if (config.chatModel.provider.equals("openai")) {
            chatModel = OpenAiChatModel.builder()
                    .apiKey(System.getenv("OPENAI_API_KEY"))
                    .modelName(config.chatModel.model)
                    .logRequests(config.debug)
                    .logResponses(config.debug)
                    .build();
        } else {
            if (config.chatModel.provider.equals("ollama")) {
                chatModel = OllamaChatModel.builder()
                        .modelName(config.chatModel.model + (config.chatModel.embedding == null ? "" : ":" + config.chatModel.embedding))
                        .baseUrl("http://127.0.0.1:11434")
                        .logRequests(config.debug)
                        .logResponses(config.debug)
                        .build();
            } else {
                if (config.chatModel.provider.equals("anthropic")) {
                    chatModel = AnthropicChatModel.builder()
                            // API key can be created here: https://console.anthropic.com/settings/keys
                            .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                            .modelName(config.chatModel.model + (config.chatModel.embedding == null ? "" : ":" + config.chatModel.embedding))
                            .logRequests(config.debug)
                            .logResponses(config.debug)
                            // Other parameters can be set as well
                            .build();
                } else {
                    throw new Exception("Unsupported model");
                }
            }
        }
        PromptHandler promptHandler = new PromptHandler();
        Bot bot = AiServices.builder(Bot.class)
                .chatLanguageModel(chatModel)
                .toolProvider(toolProvider)
                .systemMessageProvider(chatMemoryId -> {
                    return promptHandler.getPrompt();
                })
                .build();

        try {
            Server server = newServer(8888, bot, clients, promptHandler);
            server.start();
            server.join();
        } finally {
            for (McpClient client : clients) {
                client.close();
            }
        }
    }

    public static Server newServer(int port, Bot bot, List<McpClient> clients, PromptHandler promptHandler) {
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setIdleTimeout(Duration.ofMinutes(10).toMillis());
        connector.setPort(port);
        server.addConnector(connector);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);

        // Add websocket servlet
        Servlet websocketServlet = new JettyWebSocketServlet() {
            @Override
            protected void configure(JettyWebSocketServletFactory factory) {
                factory.setIdleTimeout(Duration.ofMinutes(10));
                factory.addMapping("/chatbot", (req, res) -> new ChatWebSocket(bot, clients, promptHandler));
            }
        };
        Servlet mapResource = new ImportMapServlet();
        context.addServlet(new ServletHolder(websocketServlet), "/chatbot");
        context.addServlet(new ServletHolder(mapResource), "/_importmap/*");
        JettyWebSocketServletContainerInitializer.configure(context, null);

        // Add default servlet (to serve the html/css/js)
        // Figure out where the static files are stored.
        URL urlStatics = Thread.currentThread().getContextClassLoader().getResource("META-INF/resources/index.html");
        Objects.requireNonNull(urlStatics, "Unable to find index.html in classpath");
        String urlBase = urlStatics.toExternalForm().replaceFirst("/[^/]*$", "/");
        ServletHolder defHolder = new ServletHolder("default", new DefaultServlet());
        System.out.println("ROOT URL " + urlBase);
        defHolder.setInitParameter("resourceBase", urlBase);
        defHolder.setInitParameter("dirAllowed", "true");
        context.addServlet(defHolder, "/");

        return server;
    }
}
