/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.wildfly.mcp.chat.bot;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 *
 * @author jdenise
 */
public class ChatConfiguration {
    public static class ChatModel {
        public final String provider;
        public final String model;
        public final String embedding;
        private ChatModel(String provider, String model, String embedding) {
            this.provider = provider;
            this.model = model;
            this.embedding = embedding;
        }
        private static ChatModel parseModel(String modelString) {
            String[] split = modelString.split(":");
            String provider = split[0];
            String model = split[1];
            String embedding = null;
            if(split.length == 3) {
                embedding = split[2];
            }
            return new ChatModel(provider, model, embedding);
        }
    }
    public final ChatModel chatModel;
    public final Path configFile;
    private ChatConfiguration(String chatModel, String path) {
        this.chatModel = ChatModel.parseModel(chatModel);
        configFile = Paths.get(path).toAbsolutePath();
    }

    public static ChatConfiguration parseArguments(String[] args) {
        // --config [path to the file]/mcp.json --model ollama:llama3.1:8b
        String model = "ollama:llama3.2:8b";
        String path ="./mcp.json";
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--model")) {
                model = args[i + 1];
                continue;
            }
            if (a.equals("--config")) {
                path = args[i + 1];
                continue;
            }
        }
        return new ChatConfiguration(model, path);
    }
}
