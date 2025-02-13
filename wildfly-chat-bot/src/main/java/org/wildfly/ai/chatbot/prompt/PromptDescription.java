/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.chatbot.prompt;

import java.util.Collections;
import java.util.List;

public class PromptDescription {

    public static class PromptArg {

        public String name;
        public String description;
        public String required;
    }
    public final String name;
    public final String description;
    public final List<PromptArg> arguments;

    public PromptDescription(String name, String description, List<PromptArg> args) {
        this.name = name;
        this.description = description;
        this.arguments = Collections.unmodifiableList(args);
    }
}
