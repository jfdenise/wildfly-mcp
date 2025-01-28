/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.wildfly.mcp.chat.bot;

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
