/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.chatbot;

import org.wildfly.ai.chatbot.http.HttpMcpTransport.TokenProvider;

/**
 *
 * @author jdenise
 */
public class OidcTokenProvider implements TokenProvider {

    private String token;

    OidcTokenProvider() {
    }
    void setToken(String token) {
        this.token = token;
    }
    @Override
    public String getToken() throws Exception {
        return token;
    }

}
