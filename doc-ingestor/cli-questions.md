# Undertow

Example of add an application-security-domain named foo to undertow. There is 2 ways that can't be mixed:
1) Example Add the security domain foo with an elytron security-domain named bar
/subsystem=undertow/application-security-domain=foo:add(security-domain=bar)
2) Example Add the security domain foo with an elytron http-authentication-factory named bar
/subsystem=undertow/application-security-domain=foo:add(http-authentication-factory=bar)

# Logger

TODO

# system property

## syntax of operations to update or create a system property

The syntax of an operation to create or update a system-property resource is: `/system-property=<property name>:<operation>(<operation_arguments>)`
The possible values for <operation> are: `add`, `remove` or `write-attribute`.
If `add` is used, the <operation_arguments> is `value=<value of the system property>`.
If `remove` is used, the <operation_arguments> is empty.
If `write-attribute` is used, the <operation_arguments> is `name=value, value=<new value of the system property>`.

## syntax of the operation to get the value of a system property

The syntax of an operation to get the value of a system-property resource is: `/system-property=<property name>:read-attribute(name=value)`


## operation to list all the system property

The operation to list all the system property is: `:read-children-resources(child-type=system-property)`
An empty result returned by this operation means that no system properties are set.

# get or resolve the value of the expression

An expression starts with `${` and end with `}`.
The syntax of an operation to resolve an expression is: `:resolve-expression(expression=<expression_value>})`
An `<expression_value>` can be an environment variable or a system property. Examples of expressions are `${JBOSS_HOME}` and `${jboss.node.name}`.

# interface

## operation to list all the interface resources

Can you get all the interfaces?
Can you list all interfaces?

## syntax of the operation to get a named interface resource

Could you get the management interface?
Could you get the public interface?
Could you get the foo interface?

# management HTTP interface

## operation to get the management http interface resource

Could you get the management interface?

## interesting attributes

* `http-authentication-factory`: elytron http-authentication-factory name used to secure the access to the management interface.
* `socket-binding` : The name of the socket binding name used for the management interface's socket.

# socket binding

What are the active socket bindings?
What are all the socket bindings?
What is the interface of the http socket binding?

# deployment

## operation to get all the deployment resources


## syntax of the operation to get a deployment resource


## syntax of the operation to get the status of a deployment 

Retrieve the list of deployments and give me their status.
What is the status of servlet-security.war deployment?

## syntax of the operation to get the paths of the file contained in a deployment

What are the files in servlet-security.war deployment?

## syntax of the operation to get the content of a file contained in a deployment

What is the content of the file web.xml in servlet-security.war?
Can you retrieve all the deployments and for each of them show me the content of their web.xml?

# extension

## syntax of the operation to get a extension

Could you retrieve the extension org.jboss.as.clustering.infinispan and show me its resource?

## operation to get all the extension resources

What are all the extensions?

