/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.chatbot.prompt;

import dev.langchain4j.mcp.client.McpPromptArgument;
import java.util.ArrayList;
import java.util.List;

public class PromptDescription {

    public static class PromptArg {

        public String name;
        public String description;
        public String required;
    }
    public final String name;
    public final String description;
    public final List<PromptArg> arguments = new ArrayList<>();

    public PromptDescription(String name, String description, List<McpPromptArgument> args) {
        this.name = name;
        this.description = description;
        for(McpPromptArgument arg : args) {
            PromptArg pa = new PromptArg();
            pa.description = arg.description();
            pa.name = arg.name();
            pa.required = arg.required() ? "true" : "false";
            arguments.add(pa);
        }
    }
}
