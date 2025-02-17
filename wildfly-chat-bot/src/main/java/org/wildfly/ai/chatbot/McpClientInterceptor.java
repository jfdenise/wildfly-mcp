/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.chatbot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.wildfly.ai.chatbot.WildFlyCredentialsHandler.UserPassword;
import org.wildfly.ai.chatbot.tool.ToolDescription;
import org.wildfly.ai.chatbot.tool.ToolDescription.ToolArg;
import org.wildfly.ai.chatbot.tool.ToolHandler;

public class McpClientInterceptor implements McpClient {

    private static final Logger logger = Logger.getLogger(McpClientInterceptor.class.getName());

    private final McpClient delegate;
    private final ChatBotWebSocketEndpoint endpoint;
    private final Map<String, Boolean> acceptedTools = new HashMap<>();
    private final WildFlyCredentialsHandler credentialsHandler;

    McpClientInterceptor(McpClient delegate, ChatBotWebSocketEndpoint endpoint, WildFlyCredentialsHandler credentialsHandler) {
        this.delegate = delegate;
        this.endpoint = endpoint;
        this.credentialsHandler = credentialsHandler;
    }

    @Override
    public List<ToolSpecification> listTools() {
        return delegate.listTools();
    }

    @Override
    public String executeTool(ToolExecutionRequest ter) {
        Boolean accepted = acceptedTools.get(ter.name() + ter.arguments());
        boolean canCall = accepted != null && accepted;
        if (!canCall) {
            canCall = endpoint.canCallTool(ter.name(), ter.arguments());
        }
        if (canCall) {
            acceptedTools.put(ter.name() + ter.arguments(), true);
            ToolDescription wildflyTool = endpoint.toolHandler.getWildFlyTool(ter.name());
            ToolExecutionRequest original = ter;
            ToolExecutionRequest actual = ter;
            if (wildflyTool != null) {
                boolean expectsCredentials = false;
                for (ToolArg a : wildflyTool.arguments) {
                    if (a.name.equals("userName")) {
                        expectsCredentials = true;
                        break;
                    }
                }
                if (expectsCredentials) {
                    /**
                     * If no host provided. Does nothing.
                     */
                    // Must possibly rewite the tool arguments with credentials if needed
                    try {
                        ObjectMapper objectMapper = new ObjectMapper();
                        JsonNode toolArguments = objectMapper.readTree(ter.arguments());
                        // Credentials are bound to host + port
                        String host = null;
                        String port = null;
                        if (!toolArguments.has("userPassword")) {
                            if (toolArguments.has("host")) {
                                host = toolArguments.get("host").asText();
                            }
                            if (toolArguments.has("port")) {
                                port = toolArguments.get("port").asText();
                            }
                            List<UserPassword> users = credentialsHandler.getUser(host, port);
                            UserPassword up = null;
                            if (users != null && !users.isEmpty()) {
                                if (users.size() > 1) {
                                   String name = endpoint.selectUser(wildflyTool.name, users);
                                   for(UserPassword u : users) {
                                       if (u.name.equals(name)) {
                                           up = u;
                                           break;
                                       }
                                   }
                                } else {
                                   up = users.get(0);
                                }
                                ObjectNode on = (ObjectNode) toolArguments;
                                on.put("userName", up.name);
                                on.put("userPassword", up.password);
                                String updatedArguments = new ObjectMapper().writeValueAsString(on);
                                // Need to re-write the request.
                                actual = ToolExecutionRequest.builder().arguments(updatedArguments).id(ter.id()).name(ter.name()).build();
                            }
                        }
                    } catch (JsonProcessingException ex) {
                        throw new RuntimeException(ex);
                    }
                }
                // handle credentials.
            }
            String ret = delegate.executeTool(actual);
            endpoint.traceToolCalled(actual.name(), original.arguments(), ret);
            return ret;
        } else {

            /**
             * From the MCP specification: { "jsonrpc": "2.0", "id": 2,
             * "result": { "content": [ { "type": "text", "text": "Current
             * weather in New York:\nTemperature: 72°F\nConditions: Partly
             * cloudy" } ], "isError": false } }
             */
            long id = ter.id() == null ? 1l : Long.parseLong(ter.id());
            DeniedToolCallResponse response = new DeniedToolCallResponse(id);

            try {
                String resp = ChatBotWebSocketEndpoint.toJson(response);
                logger.info("User denied the tool " + resp);
                return resp;
            } catch (JsonProcessingException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    @Override
    public void close() throws Exception {
        delegate.close();
    }

}
