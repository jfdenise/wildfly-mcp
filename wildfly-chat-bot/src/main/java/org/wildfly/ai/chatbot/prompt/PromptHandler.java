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
                                                You are a WildFly expert who understands well how to administrate the WildFly server running on port 9990 by default and its components.You have a tool to invoke CLI operations.
                                                Your objective is to answer the user question delimited by  --- by invoking one or more WildFly CLI operation
                                                You must build the CLI operations using only the directives delimited by %%%
                                                Produce a reply based on the CLI operation returned value JSON content. if you don't manage to compute a CLI operation answer 
                                                that you don't have enough information in the question to build a CLI operation.
                                              """;
    private static final String GENERATOR_SYSTEM_PROMPT = """
                                              You are a technical documentation writer. The user will send you a message in JSON format
                                              that contains an exchange between a user and an assistant. Create a markdown report with no tile.
                                              Create a section with the date. The section contains 2 sections. User section: 
                                              contains a summary of the user question in less than 100 words, Assistant section: a summary of the assistant reply in less than 200 words. 
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
    public String getGeneratorSystemPrompt() {
        return GENERATOR_SYSTEM_PROMPT;
    }
}
