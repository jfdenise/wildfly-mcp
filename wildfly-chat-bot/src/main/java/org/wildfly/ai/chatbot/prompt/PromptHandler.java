/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.chatbot.prompt;

import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpGetPromptResult;
import dev.langchain4j.mcp.client.McpPrompt;
import dev.langchain4j.mcp.client.McpPromptMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PromptHandler {
    class PromptComparator implements Comparator<PromptDescription> {

        @Override
        public int compare(PromptDescription a, PromptDescription b) {
            return a.name.compareToIgnoreCase(b.name);
        }
    }
    private static final String SYSTEM_PROMPT = """
                                              You are a smart AI agent that can answer any kind of questions. In addition, 
                                              you have tools to interact with running WildFly servers that run by default on localhost and port 9990. The user
                                              will ask you to perform operations. Make sure to analyze the tools returned values and help the user.
            """;
    private static final String GENERATOR_SYSTEM_PROMPT = """
                                              You are a technical documentation writer. The user will send you a message in JSON format
                                              that contains an exchange between a user and an assistant. Create a markdown report with no tile.
                                              Create a section with the date. The section contains 2 sections. User section: 
                                              contains a summary of the user question in less than 100 words, Assistant section: a summary of the assistant reply in less than 200 words. 
            """;
    private final List<McpClient> clients;
    private final Map<String, McpClient> promptToClient = new HashMap<>();

    public PromptHandler(List<McpClient> clients) {
        this.clients = clients;
    }

    public List<PromptDescription> getPrompts() throws Exception {
        List<PromptDescription> prompts = new ArrayList<>();
        for (McpClient client : clients) {
            for (McpPrompt prompt : client.listPrompts()) {
                promptToClient.put(prompt.name(), client);
                prompts.add(new PromptDescription(prompt.name(), prompt.description(),prompt.arguments()));
            }
        }
        Collections.sort(prompts, new PromptComparator());
        return prompts;
    }

    public String getPrompt(SelectedPrompt prompt) throws Exception {
        McpClient client = promptToClient.get(prompt.name);
        Map<String, Object> args = new HashMap<>();
        for (SelectedPrompt.PromptArg arg : prompt.arguments) {
            if (arg.value != null) {
                args.put(arg.name, arg.value);
            }
        }
        McpGetPromptResult res = client.getPrompt(prompt.name, args);
        StringBuilder builder = new StringBuilder();
        for(McpPromptMessage msg : res.messages()) {
            if(ContentType.TEXT.equals(msg.content().toContent().type())) {
                TextContent text = (TextContent)msg.content().toContent();
                builder.append(text.text()).append("\n");
            } else {
                builder.append(msg.content().toContent()).append("\n");
            }
            
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
