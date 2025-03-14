# WildFly Chat Bot deployed to OpenShift

You must be already logged into your OpenShift cluster.

* Create the secrets that contain WildFly admin user:

`oc create -f secrets.yaml`

* Deploy your WildFly [helloworld quickstart](https://github.com/wildfly/quickstart/tree/main/helloworld) application:

`helm install wildfly-app -f helm-wildfly.yaml wildfly/wildfly`

* Patch the deployed application service (generated by helm chart) to expose the management port (used by the WildFly chat bot):

```
oc patch service/wildfly-app --type='json' -p '[{"op": "replace", "path": "/spec/ports/0/name", "value":"http"}]'

oc patch service/wildfly-app -p '{"spec": {"ports": [{"port": 9990, "targetPort": "admin", "protocol": "TCP", "name": "admin"}]}}'
```

* Deploy Ollama:

```
oc create -f ollama.yaml
```

* Deploy the web chat image:

`helm install wildfly-chat-bot -f helm.yaml wildfly/wildfly`

* Access to the Web chat (Make sure that all your deployments are up and running), its URL is the output of the following command:

```
echo https://$(oc get route wildfly-chat-bot --template='{{ .spec.host }}')
```

## Configuring the WildFly chat bot

The env variables defined in the [WildFly chat bot README](../../../wildfly-chat-bot/README.md) can be used.
