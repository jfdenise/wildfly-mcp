/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.chatbot;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 *
 * @author jdenise
 */
public class MCPConfig {

    public static class MCPServerConfig {

        public String command;
        public List<String> args;
    }
    public Map<String, MCPServerConfig> mcpServers;

    public static MCPConfig parseConfig(Path configFile) throws Exception {
        String content = new String(Files.readAllBytes(configFile));
        return new ObjectMapper().readValue(content, MCPConfig.class);
    }
}
