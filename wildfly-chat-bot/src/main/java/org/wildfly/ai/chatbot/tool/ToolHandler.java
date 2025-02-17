/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.chatbot.tool;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonSchemaElementHelper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.wildfly.ai.chatbot.tool.SelectedTool.ToolArg;

public class ToolHandler {

    private final List<McpClient> clients;
    private final Map<String, McpClient> toolToClient;
    private final Map<String, ToolDescription> wildflyTools;

    public ToolHandler(Map<String, McpClient> toolToClient, List<McpClient> clients, Map<String, ToolDescription> wildflyTools) {
        this.toolToClient = toolToClient;
        this.clients = clients;
        this.wildflyTools = wildflyTools;
    }

    public static List<ToolDescription> getTools(McpClient client) {
        List<ToolDescription> tools = new ArrayList<>();
        List<ToolSpecification> specs = client.listTools();
        for (ToolSpecification s : specs) {
            List<ToolDescription.ToolArg> arguments = new ArrayList<>();
            List<String> required = s.parameters().required();
            for (Map.Entry<String, JsonSchemaElement> entry : s.parameters().properties().entrySet()) {
                JsonSchemaElement schema = entry.getValue();
                String name = entry.getKey();
                Map<String, Object> map = JsonSchemaElementHelper.toMap(schema);
                String description = (String) map.get("description");
                ToolDescription.ToolArg arg = new ToolDescription.ToolArg();
                arg.description = description;
                arg.name = name;
                arg.required = required.contains(name) ? "true" : "false";
                arguments.add(arg);
            }
            ToolDescription td = new ToolDescription(s.name(), s.description(), arguments);
            tools.add(td);
        }
        return tools;
    }

    public List<ToolDescription> getTools() throws Exception {
        List<ToolDescription> tools = new ArrayList<>();
        for (McpClient client : clients) {
            List<ToolSpecification> specs = client.listTools();
            for (ToolSpecification s : specs) {
                toolToClient.put(s.name(), client);
                List<ToolDescription.ToolArg> arguments = new ArrayList<>();
                List<String> required = s.parameters().required();
                for (Map.Entry<String, JsonSchemaElement> entry : s.parameters().properties().entrySet()) {
                    // Support only String tools...
                    String name = entry.getKey();
                    JsonSchemaElement schema = entry.getValue();
                    Map<String, Object> map = JsonSchemaElementHelper.toMap(schema);
                    ToolDescription.ToolArg arg = new ToolDescription.ToolArg();
                    arg.description = (String) map.get("description");
                    arg.name = name;
                    arg.required = required.contains(name) ? "true" : "false";
                    arguments.add(arg);
                }
                ToolDescription td = new ToolDescription(s.name(), s.description(), arguments);
                tools.add(td);
            }
        }
        return tools;
    }

    public String executeTool(SelectedTool tool) throws Exception {
        McpClient client = toolToClient.get(tool.name);
        StringBuilder jsonArguments = new StringBuilder("{");
        Iterator<ToolArg> it = tool.arguments.iterator();
        while (it.hasNext()) {
            ToolArg arg = it.next();
            jsonArguments.append("\"" + arg.name + "\": " + "\"" + arg.value + "\"");
            if (it.hasNext()) {
                jsonArguments.append(",");
            }
        }
        jsonArguments.append("}");
        System.out.println("JSON String " + jsonArguments);
        ToolExecutionRequest req = ToolExecutionRequest.builder().arguments(jsonArguments.toString()).id("1").name(tool.name).build();

        return client.executeTool(req);
    }
    
    public ToolDescription getWildFlyTool(String name) {
        return wildflyTools.get(name);
    }
}
