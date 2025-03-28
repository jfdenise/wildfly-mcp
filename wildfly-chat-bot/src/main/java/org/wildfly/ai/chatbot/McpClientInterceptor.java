/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.chatbot;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.ResourceRef;
import dev.langchain4j.mcp.client.ResourceResponse;
import dev.langchain4j.mcp.client.ResourceTemplateRef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class McpClientInterceptor implements McpClient {

    private static final Logger logger = Logger.getLogger(McpClientInterceptor.class.getName());

    private final McpClient delegate;
    private final ChatBotWebSocketEndpoint endpoint;
    private final Map<String, Boolean> acceptedTools = new HashMap<>();

    McpClientInterceptor(McpClient delegate, ChatBotWebSocketEndpoint endpoint) {
        this.delegate = delegate;
        this.endpoint = endpoint;
    }

    @Override
    public List<ToolSpecification> listTools() {
        List<ToolSpecification> specs = delegate.listTools();
        List<ToolSpecification> ret = new ArrayList<>();
        for (ToolSpecification spec : specs) {
            if (spec.name().equals("invokeWildFlyCLIOperation")) {
                ret.add(spec);
                break;
            }
        }
        return ret;
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
            String ret = delegate.executeTool(ter);
            endpoint.traceToolCalled(ter.name(), ter.arguments(), ret);
            endpoint.getRecorder().toolCalled(ter, ret);
            return ret;
        } else {
            String id = ter.id() == null ? "1" : ter.id();
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

    @Override
    public List<ResourceRef> listResources() {
        return delegate.listResources();
    }

    @Override
    public List<ResourceTemplateRef> listResourceTemplates() {
        return delegate.listResourceTemplates();
    }

    @Override
    public ResourceResponse readResource(String string) {
        return delegate.readResource(string);
    }

}
