# WildFly Chat BOT

A chat bot for WildFly. Listens on http://localhost:8888.

```
java -jar ./target/wildfly-chat-bot-1.0.0.Final-SNAPSHOT-client.jar --config ./mcp.json --model ollama:llama3.2:3b [--debug] [--port=8888]
```

Available chat models:

* ollama:<model>:<embedding>
* openai:<model>
* antropic:<model>
