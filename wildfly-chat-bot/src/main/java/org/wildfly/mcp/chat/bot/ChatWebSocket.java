/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.wildfly.mcp.chat.bot;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import java.util.List;
import java.util.Map.Entry;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebSocket()
public class ChatWebSocket {

    private static final Logger LOG = LoggerFactory.getLogger(ChatWebSocket.class);
    private Session session;
    private RemoteEndpoint remote;
    private final Bot bot;
    private final List<McpClient> clients;
    private final PromptHandler promptHandler;

    public ChatWebSocket(Bot bot, List<McpClient> clients, PromptHandler promptHandler) {
        this.bot = bot;
        this.clients = clients;
        this.promptHandler = promptHandler;
    }

    @OnWebSocketClose
    public void onWebSocketClose(int statusCode, String reason) {
        this.session = null;
        this.remote = null;
        LOG.info("WebSocket Close: {} - {}", statusCode, reason);
    }

    @OnWebSocketConnect
    public void onWebSocketConnect(Session session) {
        this.session = session;
        this.remote = this.session.getRemote();
        LOG.info("WebSocket Connect: {}", session);
        this.remote.sendString("\"Hello, I am a WildFly robot, how can I help? Type '/help' for builtin commands.", WriteCallback.NOOP);
    }

    @OnWebSocketError
    public void onWebSocketError(Throwable cause) {
        LOG.warn("WebSocket Error", cause);
    }

    @OnWebSocketMessage
    public void onWebSocketText(String message) {
        if (this.session != null && this.session.isOpen() && this.remote != null) {
            if ("/help".equals(message)) {
                StringBuilder help = new StringBuilder();
                help.append("<p><b>/tools</b>: List tools" + "<br></p>");
                help.append("<p><b>/prompt</b>: Get system prompt" + "<br></p>");
                help.append("<p><b>/prompt-list</b>: List prompts" + "<br></p>");
                help.append("<p><b>/prompt-run <prompt name></b>: Run the prompt" + "<br></p>");
                String table="<table>\n" +
"  <caption>\n" +
"    Front-end web developer course 2021\n" +
"  </caption>\n" +
"  <thead>\n" +
"    <tr>\n" +
"      <th scope=\"col\">Person</th>\n" +
"      <th scope=\"col\">Most interest in</th>\n" +
"      <th scope=\"col\">Age</th>\n" +
"    </tr>\n" +
"  </thead>\n" +
"  <tbody>\n" +
"    <tr>\n" +
"      <th scope=\"row\">Chris</th>\n" +
"      <td>HTML tables</td>\n" +
"      <td>22</td>\n" +
"    </tr>\n" +
"    <tr>\n" +
"      <th scope=\"row\">Dennis</th>\n" +
"      <td>Web accessibility</td>\n" +
"      <td>45</td>\n" +
"    </tr>\n" +
"    <tr>\n" +
"      <th scope=\"row\">Sarah</th>\n" +
"      <td>JavaScript frameworks</td>\n" +
"      <td>29</td>\n" +
"    </tr>\n" +
"    <tr>\n" +
"      <th scope=\"row\">Karen</th>\n" +
"      <td>Web performance</td>\n" +
"      <td>36</td>\n" +
"    </tr>\n" +
"  </tbody>\n" +
"  <tfoot>\n" +
"    <tr>\n" +
"      <th scope=\"row\" colspan=\"2\">Average age</th>\n" +
"      <td>33</td>\n" +
"    </tr>\n" +
"  </tfoot>\n" +
"</table>";
                this.remote.sendString(table, WriteCallback.NOOP);
                return;
            }
            if ("/prompt".equals(message)) {
                this.remote.sendString(promptHandler.getPrompt(), WriteCallback.NOOP);
                return;
            }
            if ("/prompt-list".equals(message)) {
                StringBuilder prompts = new StringBuilder();
                for (Entry<String, String> entry : promptHandler.getPrompts()) {
                    prompts.append("<p><b>" + entry.getKey() + "</b>:" + entry.getValue() + "<br></p>");
                }
                this.remote.sendString(prompts.toString(), WriteCallback.NOOP);
                return;
            }
            if (message.startsWith("/prompt-run")) {
                String name = message.substring("/prompt-run".length());
                String prompt = promptHandler.getPrompt(name.trim());
                if (prompt == null) {
                    this.remote.sendString("Hoops...prompt " + name.trim() + " doesn't exist...", WriteCallback.NOOP);
                } else {
                    String reply = bot.chat(prompt);
                    this.remote.sendString(reply, WriteCallback.NOOP);
                }
                return;
            }
            if ("/tools".equals(message)) {
                StringBuilder tools = new StringBuilder();
                for (McpClient client : clients) {
                    List<ToolSpecification> specs = client.listTools();
                    for (ToolSpecification s : specs) {
                        tools.append("<p><b>" + s.name() + "</b>: " + s.description() + "<br></p>");
                    }
                }
                this.remote.sendString(tools.toString(), WriteCallback.NOOP);
                return;
            }
            if (message.startsWith("/")) {
                this.remote.sendString("Invalid help command " + message, WriteCallback.NOOP);
            }
            String reply = bot.chat(message);
            this.remote.sendString(reply, WriteCallback.NOOP);
        }
    }
}
