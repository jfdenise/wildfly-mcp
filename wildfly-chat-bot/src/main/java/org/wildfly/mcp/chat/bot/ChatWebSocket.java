/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.wildfly.mcp.chat.bot;

import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebSocket()
public class ChatWebSocket {

    private static final Logger LOG = LoggerFactory.getLogger(ChatWebSocket.class);
    private Session session;
    private RemoteEndpoint remote;
    private final Bot bot;

    public ChatWebSocket(Bot bot) {
        this.bot = bot;
    }

    @OnWebSocketClose
    public void onWebSocketClose(int statusCode, String reason) {
        this.session = null;
        this.remote = null;
        LOG.info("WebSocket Close: {} - {}", statusCode, reason);
    }

    @OnWebSocketConnect
    public void onWebSocketConnect(Session session) {
        this.session = session;
        this.remote = this.session.getRemote();
        LOG.info("WebSocket Connect: {}", session);
        this.remote.sendString("Hello, I am a WildFly robot, how can I help?", WriteCallback.NOOP);
    }

    @OnWebSocketError
    public void onWebSocketError(Throwable cause) {
        LOG.warn("WebSocket Error", cause);
    }

    @OnWebSocketMessage
    public void onWebSocketText(String message) {
        if (this.session != null && this.session.isOpen() && this.remote != null) {
            String reply = bot.chat(message);
            this.remote.sendString(reply, WriteCallback.NOOP);
        }
    }
}
