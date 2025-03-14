/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.chatbot.prompt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import dev.langchain4j.mcp.client.transport.McpTransport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.wildfly.ai.chatbot.prompt.PromptDescription.PromptArg;

public class PromptHandler {
    
    private static final String SYSTEM_PROMPT = """
                                              You are a smart AI agent that can answer any kind of questions. In addition, 
            you have tools to interact with running WildFly servers and the users
            will ask you to perform operations like getting status, readling log files, retrieving prometheus metrics. Make sure to 
            analyze the tools returned values and provide explaination to the user.
            """;

    private final List<McpTransport> transports;
    private final Map<String, McpTransport> promptToTransport = new HashMap<>();

    public PromptHandler(List<McpTransport> transports) {
        this.transports = transports;
    }

    public List<PromptDescription> getPrompts() throws Exception {
        List<PromptDescription> prompts = new ArrayList<>();

        for (McpTransport t : transports) {
            CompletableFuture<JsonNode> resp = t.executeOperationWithResponse(new ListPrompts(1l));
            System.out.println(resp.get().toPrettyString());
            ArrayNode promptsArray = (ArrayNode) resp.get().get("result").get("prompts");
            for (JsonNode p : promptsArray) {
                List<PromptArg> pargs = new ArrayList<>();
                if (p.has("arguments")) {
                    ArrayNode args = (ArrayNode) p.get("arguments");
                    for (JsonNode a : args) {
                        PromptArg pa = new PromptArg();
                        pa.name = a.get("name").asText();
                        pa.required = a.get("required").asText();
                        pa.description = a.get("description").asText();
                        pargs.add(pa);
                    }
                }
                promptToTransport.put(p.get("name").asText(), t);
                prompts.add(new PromptDescription(p.get("name").asText(), p.get("description").asText(), pargs));
            }
        }
        return prompts;
    }

    public String getPrompt(SelectedPrompt prompt) throws Exception {
        McpTransport t = promptToTransport.get(prompt.name);
        GetPrompt getPrompt = new GetPrompt(1l);
        getPrompt.params.name = prompt.name;
        for (SelectedPrompt.PromptArg arg : prompt.arguments) {
            if (arg.value != null) {
                getPrompt.params.arguments.put(arg.name, arg.value);
            }
        }
        CompletableFuture<JsonNode> resp = t.executeOperationWithResponse(getPrompt);
        ArrayNode messagesArray = (ArrayNode) resp.get().get("result").get("messages");
        StringBuilder builder = new StringBuilder();
        for (JsonNode m : messagesArray) {
            builder.append(m.get("content").get("text").asText()).append("\n");
        }
        return builder.toString();
    }

    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }
}
