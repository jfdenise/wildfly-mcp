/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.chatbot;

import dev.langchain4j.mcp.client.McpCallContext;
import dev.langchain4j.mcp.client.McpHeadersSupplier;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author jdenise
 */
public class TokenHeadersSupplier implements McpHeadersSupplier {

    private final DefaultTokenProvider provider;

    TokenHeadersSupplier(DefaultTokenProvider provider) {
        this.provider = provider;
    }

    @Override
    public Map<String, String> apply(McpCallContext t) {
        Map<String, String> map = new HashMap<>();
        try {
            String token = provider.getToken();
            map.put("Authorization", "Bearer " + token);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return map;
    }

}
