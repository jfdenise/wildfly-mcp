/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.chatbot;

import org.wildfly.ai.chatbot.tool.ToolHandler;
import org.wildfly.ai.chatbot.tool.SelectedTool;
import org.wildfly.ai.chatbot.tool.ToolDescription;
import org.wildfly.ai.chatbot.prompt.PromptHandler;
import org.wildfly.ai.chatbot.prompt.SelectedPrompt;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;
import java.io.IOException;
import java.util.logging.Logger;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.wildfly.ai.chatbot.MCPConfig.MCPServerSSEConfig;
import org.wildfly.ai.chatbot.MCPConfig.MCPServerStdioConfig;
import org.wildfly.ai.chatbot.WildFlyCredentialsHandler.UserPassword;

@ServerEndpoint(value = "/chatbot")
public class ChatBotWebSocketEndpoint {

    private static final Logger logger = Logger.getLogger(ChatBotWebSocketEndpoint.class.getName());

    @Inject
    @Named(value = "ollama")
    ChatLanguageModel ollama;
    @Inject
    @Named(value = "openai")
    ChatLanguageModel openai;
    @Inject
    @Named(value = "groq")
    ChatLanguageModel groq;
    @Inject
    @ConfigProperty(name="wildfly.chatbot.mcp.config.file")
    private Path mcpConfigFile;
    @Inject
    @ConfigProperty(name="wildfly.chatbot.llm.name")
    private String llmName;
    private boolean disabledAcceptance;
    private PromptHandler promptHandler;
    public ToolHandler toolHandler;
    private WildFlyCredentialsHandler credentialsHandler = new WildFlyCredentialsHandler();
    private Bot bot;
    private List<McpClient> clients = new ArrayList<>();
    private final List<McpTransport> transports = new ArrayList<>();
    private Session session;
    private final ExecutorService executor = Executors.newFixedThreadPool(1);
    private final BlockingQueue<String> workQueue = new ArrayBlockingQueue<>(1);

    @PostConstruct
    public void init() {
        try {
            logger.info("Initialize");
            MCPConfig mcpConfig = MCPConfig.parseConfig(mcpConfigFile);
            clients = new ArrayList<>();
            Map<String, McpClient> toolToClient = new HashMap<>();
            Map<String, ToolDescription> wildflyTools = new HashMap<>();
            if (mcpConfig.mcpServers != null) {
                for (Map.Entry<String, MCPServerStdioConfig> entry : mcpConfig.mcpServers.entrySet()) {
                    boolean isWildFly = entry.getKey().equals("wildfly");
                    List<String> cmd = new ArrayList<>();
                    cmd.add(entry.getValue().command);
                    cmd.addAll(entry.getValue().args);
                    McpTransport transport = new StdioMcpTransport.Builder()
                            .command(cmd)
                            .logEvents(true)
                            .build();
                    transports.add(transport);
                    McpClient mcpClient = new DefaultMcpClient.Builder()
                            .transport(transport)
                            .clientName(entry.getKey())
                            .build();
                                       // We discover wildfly tools
                    List<ToolDescription> tools = ToolHandler.getTools(mcpClient);
                    for(ToolDescription td : tools) {
                        toolToClient.put(td.name, mcpClient);
                        if (isWildFly) {
                            wildflyTools.put(td.name, td);
                        }
                    }
                    clients.add(new McpClientInterceptor(mcpClient, this, credentialsHandler));
                }
            }
            if (mcpConfig.mcpSSEServers != null) {
                for (Map.Entry<String, MCPServerSSEConfig> entry : mcpConfig.mcpSSEServers.entrySet()) {
                    McpTransport transport = new HttpMcpTransport.Builder()
                            .sseUrl(entry.getValue().url)
                            .build();
                    transports.add(transport);
                    McpClient mcpClient = new DefaultMcpClient.Builder()
                            .transport(transport)
                            .clientName(entry.getKey())
                            .build();
                    List<ToolDescription> tools = ToolHandler.getTools(mcpClient);
                    for(ToolDescription td : tools) {
                        toolToClient.put(td.name, mcpClient);
                    }
                    clients.add(new McpClientInterceptor(mcpClient, this, credentialsHandler));
                }
            }
            ToolProvider toolProvider = McpToolProvider.builder()
                    .mcpClients(clients)
                    .build();
            promptHandler = new PromptHandler(transports);
            toolHandler = new ToolHandler(toolToClient, clients, wildflyTools);
            ChatLanguageModel model = null;
            if (llmName != null) {
                if (llmName.equals("ollama")) {
                    model = ollama;
                } else {
                    if (llmName.equals("openai")) {
                        model = openai;
                    } else {
                        if (llmName.equals("groq")) {
                            model = groq;
                        } else {
                            throw new RuntimeException("Unknown llm model name " + llmName);
                        }
                    }
                }
            } else {
                model = ollama;
            }
            bot = AiServices.builder(Bot.class)
                    .chatLanguageModel(model)
                    .toolProvider(toolProvider)
                    .systemMessageProvider(chatMemoryId -> {
                        return promptHandler.getSystemPrompt();
                    })
                    .build();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @OnOpen
    public void onOpen(Session session) throws IOException {
        this.session = session;
        logger.info("New websocket session opened: " + session.getId());
        Map<String, String> args = new HashMap<>();
        args.put("kind", "simple_text");
        args.put("value", "Hello, I am a WildFly chatbot that can interact with your WildFly servers, how can I help?");
        session.getBasicRemote().sendText(toJson(args));
    }

    public static String toJson(Object msg) throws JsonProcessingException {
        ObjectWriter ow = new ObjectMapper().writer();
        return ow.writeValueAsString(msg);
    }

    void traceToolCalled(String tool, String args, String reply) {
        try {
            logger.info("Tool called: " + tool + args + " reply :" + reply);
            Map<String, String> map = new HashMap<>();
            map.put("kind", "tool_called");
            map.put("tool", tool);
            map.put("args", args);
            //map.put("reply", reply);
            map.put("loadingRequired", "true");
            session.getBasicRemote().sendText(toJson(map));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    boolean canCallTool(String tool, String args) {
        try {
            if(disabledAcceptance) {
                return true;
            }
            logger.info("Can call: " + tool + args);
            Map<String, String> map = new HashMap<>();
            map.put("kind", "tool_calling");
            map.put("tool", tool);
            map.put("args", args);
            session.getBasicRemote().sendText(toJson(map));
            String reply = workQueue.take();
            logger.info("Unlocked in waiting thread: " + reply);
            return Boolean.parseBoolean(reply);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return false;
    }
    
    String selectUser(String tool, List<UserPassword> users) {
        try {
            logger.info("Must select a user to call: " + tool);
            Map<String, Object> map = new HashMap<>();
            map.put("kind", "select_user");
            map.put("users", users);
            session.getBasicRemote().sendText(toJson(map));
            String reply = workQueue.take();
            logger.info("Unlocked in waiting thread: " + reply);
            return reply;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    @OnClose
    public void onClose(Session session) throws Exception {
        logger.info("Websoket session closed: " + session.getId());
        for (McpClient client : clients) {
            client.close();
        }
        session.getBasicRemote().sendText("The session has been closed...");
    }

    @OnMessage
    public void onMessage(String question, Session session) throws IOException {
        try {
            logger.info("NEW MESSAGE " + question);
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode msgObj = objectMapper.readTree(question);
            String kind = msgObj.get("kind").asText();
            if ("list_tools".equals(kind)) {
                List<ToolDescription> tools = toolHandler.getTools();
                Map<String, Object> map = new HashMap<>();
                map.put("kind", "tools");
                map.put("value", tools);
                logger.info("Tools sent to UI " + toJson(map));
                session.getBasicRemote().sendText(toJson(map));
                return;
            }
            if ("list_prompts".equals(kind)) {
                Map<String, Object> map = new HashMap<>();
                map.put("kind", "prompts");
                map.put("value", promptHandler.getPrompts());
                session.getBasicRemote().sendText(toJson(map));
                return;
            }
            if ("list_users".equals(kind)) {
                Map<String, Object> map = new HashMap<>();
                map.put("kind", "users");
                map.put("value", credentialsHandler.getUsers());
                session.getBasicRemote().sendText(toJson(map));
                return;
            }
            if ("add_user".equals(kind)) {
                JsonNode userNode = msgObj.get("value");
                UserPassword up = objectMapper.treeToValue(userNode, UserPassword.class);
                String msg;
                if (up.name == null || up.password == null) {
                    msg = "User can't be added, a name and a password must be set.";
                } else {
                    credentialsHandler.addUser(up);
                    msg = "User " + up.name + " added.";
                }
                Map<String, String> map = new HashMap<>();
                map.put("kind", "simple_text");
                map.put("value", msg);
                session.getBasicRemote().sendText(toJson(map));
                return;
            }
            if ("remove_user".equals(kind)) {
                ArrayNode userArgs = (ArrayNode) msgObj.get("value").get("arguments");
                UserPassword up = new UserPassword();
                for (JsonNode a : userArgs) {
                    String name = a.get("name").asText();
                    String value = a.get("value").asText();
                    if (name.equals("host")) {
                        up.host = value;
                    } else if (name.equals("port")) {
                        up.port = value;
                    } else if (name.equals("name")) {
                        up.name = value;
                    } else if (name.equals("password")) {
                        up.password = value;
                    }
                }
                boolean removed = credentialsHandler.removeUser(up);
                String msg;
                if (!removed) {
                    msg = "User " + up.name + " has not been removed. User doesn't exist";
                } else {
                    msg = "User " + up.name + " removed.";
                }
                Map<String, String> map = new HashMap<>();
                map.put("kind", "simple_text");
                map.put("value", msg);
                session.getBasicRemote().sendText(toJson(map));
                return;
            }
            if ("use_prompt".equals(kind)) {
                JsonNode promptNode = msgObj.get("value");
                SelectedPrompt prompt = objectMapper.treeToValue(promptNode, SelectedPrompt.class);
                logger.info("Prompt received from UI " + promptNode.toPrettyString());
                // Retrieve the prompt
                String userPrompt = promptHandler.getPrompt(prompt);
                // Ask The UI to emulate a user question (loading, ...)
                Map<String, String> map = new HashMap<>();
                map.put("kind", "emulate_user_question");
                map.put("value", userPrompt);
                session.getBasicRemote().sendText(toJson(map));
                
                return;
            }
            if ("call_tool".equals(kind)) {
                executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JsonNode toolNode = msgObj.get("value");
                            SelectedTool tool = objectMapper.treeToValue(toolNode, SelectedTool.class);
                            try {
                                disabledAcceptance = true;
                                String reply = toolHandler.executeTool(tool);
                                Map<String, String> map = new HashMap<>();
                                map.put("kind", "simple_text");
                                map.put("value", reply);
                                session.getBasicRemote().sendText(toJson(map));
                            } finally {
                                disabledAcceptance = false;
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            Map<String, String> map = new HashMap<>();
                            map.put("kind", "simple_text");
                            map.put("value", "Arghhh...An internal error occured " + ex.toString());
                            try {
                                session.getBasicRemote().sendText(toJson(map));
                            } catch (Exception e) {
                                e.printStackTrace();
                                logger.log(Level.SEVERE, "Error: " + e);
                            }
                        }
                    }
                });
                return;
            }
            if ("user_question".equals(kind)) {
                executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String reply = bot.chat(msgObj.get("value").asText());
                            if (reply == null || reply.isEmpty()) {
                                reply = "I have not been able to answer your question.";
                            }
                            Map<String, String> map = new HashMap<>();
                            map.put("kind", "simple_text");
                            map.put("value", reply);
                            session.getBasicRemote().sendText(toJson(map));
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            Map<String, String> map = new HashMap<>();
                            map.put("kind", "simple_text");
                            map.put("value", "Arghhh...An internal error occured " + ex.toString());
                            try {
                                session.getBasicRemote().sendText(toJson(map));
                            } catch (Exception e) {
                                e.printStackTrace();
                                logger.log(Level.SEVERE, "Error: " + e);
                            }
                        }
                    }
                }
                );
                return;
            }
            if ("tool_acceptance_reply".equals(kind)) {
                String reply = msgObj.get("value").asText();
                logger.info("Received tool acceptance message: " + reply);
                workQueue.offer(reply);
                return;
            }
            if ("select_user_reply".equals(kind)) {
                ArrayNode userArgs = (ArrayNode) msgObj.get("value").get("arguments");
                UserPassword up = new UserPassword();
                String selected = userArgs.get(0).get("value").asText();
                logger.info("Received selected user: " + selected);
                workQueue.offer(selected);
                return;
            }
            throw new Exception("Unknown message " + kind);
        } catch (Exception ex) {
            ex.printStackTrace();
            Map<String, String> map = new HashMap<>();
            map.put("kind", "simple_text");
            map.put("value", "Arghhh...An internal error occured " + ex.toString());
            session.getBasicRemote().sendText(toJson(map));
        }
    }

    // Exception handling
    @OnError
    public void error(Session session, Throwable t) {
        t.printStackTrace();
    }
}
