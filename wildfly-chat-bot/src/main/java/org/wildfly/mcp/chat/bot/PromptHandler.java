/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.wildfly.mcp.chat.bot;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 *
 * @author jdenise
 */
public class PromptHandler {

    private static final String DEFAULT_PROMPT = """
            You have tools to interact with running WildFly servers and the users
            will ask you to perform operations like getting status, readling log files, retrieving prometheus metrics.
            """;
    private static final String PROMETHEUS = """
                                             using available tools, get Prometheus metrics from wildfly server.
                                             You will repeat the invocation 3 times, being sure to wait 2 seconds between each invocation.
                                             After all the 3 invocation has been completed you will organize the data in an HTML table.
                                             """;
    Map<String, String> prompts = new HashMap<>();
    private String prompt = "default";

    public PromptHandler() {
        prompts.put("default", DEFAULT_PROMPT);
        prompts.put("prometheus", PROMETHEUS);
    }

    public Set<Entry<String, String>> getPrompts() {
        return prompts.entrySet();
    }

    public String getPrompt() {
        return prompts.get("default");
    }

    public String getPrompt(String prompt) {
        return prompts.get(prompt);
    }
}
