/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.chatbot;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpGetPromptResult;
import dev.langchain4j.mcp.client.McpPrompt;
import dev.langchain4j.mcp.client.McpResource;
import dev.langchain4j.mcp.client.McpReadResourceResult;
import dev.langchain4j.mcp.client.McpResourceTemplate;
import dev.langchain4j.mcp.client.McpRoot;
import dev.langchain4j.service.tool.ToolExecutionResult;
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
        return delegate.listTools();
    }

    @Override
    public ToolExecutionResult executeTool(ToolExecutionRequest ter) {
        return executeTool(ter, null);
    }

    @Override
    public void close() throws Exception {
        delegate.close();
    }

    @Override
    public List<McpResource> listResources() {
        return delegate.listResources();
    }

    @Override
    public List<McpResourceTemplate> listResourceTemplates() {
        return delegate.listResourceTemplates();
    }

    @Override
    public McpReadResourceResult readResource(String string) {
        return delegate.readResource(string);
    }

    @Override
    public List<McpPrompt> listPrompts() {
        return delegate.listPrompts();
    }

    @Override
    public McpGetPromptResult getPrompt(String string, Map<String, Object> map) {
        return delegate.getPrompt(string, map);
    }

    @Override
    public void checkHealth() {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public String key() {
        return delegate.key();
    }

    @Override
    public List<ToolSpecification> listTools(InvocationContext ic) {
        return delegate.listTools(ic);
    }

    @Override
    public ToolExecutionResult executeTool(ToolExecutionRequest ter, InvocationContext ic) {
        Boolean accepted = acceptedTools.get(ter.name() + ter.arguments());
        boolean canCall = accepted != null && accepted;
        if (!canCall) {
            canCall = endpoint.canCallTool(ter.name(), ter.arguments());
        }
        if (canCall) {
            acceptedTools.put(ter.name() + ter.arguments(), true);
            ToolExecutionResult ret = delegate.executeTool(ter);
            endpoint.traceToolCalled(ter.name(), ter.arguments(), ret.resultText());
            endpoint.getRecorder().toolCalled(ter, ret.resultText());
            return ret;
        } else {
            String id = ter.id() == null ? "1" : ter.id();
            DeniedToolCallResponse response = new DeniedToolCallResponse(id);

            try {
                String resp = ChatBotWebSocketEndpoint.toJson(response);
                logger.info("User denied the tool " + resp);
                return new ToolExecutionResult.Builder().resultText(resp).build();
            } catch (JsonProcessingException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    @Override
    public List<McpResource> listResources(InvocationContext ic) {
        return delegate.listResources(ic);
    }

    @Override
    public List<McpResourceTemplate> listResourceTemplates(InvocationContext ic) {
        return delegate.listResourceTemplates(ic);
    }

    @Override
    public McpReadResourceResult readResource(String string, InvocationContext ic) {
        return delegate.readResource(string, ic);
    }

    @Override
    public void setRoots(List<McpRoot> list) {
        delegate.setRoots(list);
    }

}
