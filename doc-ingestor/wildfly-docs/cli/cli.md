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

# IP address

## operation to list all the IP interface resources

The operation to list all the IP interface is: `:read-children-resources(child-type=interface, include-runtime=true)`
An empty result returned by this operation means that no IP interface are set.

## syntax of the operation to get a named IP interface resource

The syntax of an operation to get the resource of the IP interface is: `/interface=<interface_name>:read-resource(include-runtime=true)`
The `<interface_name>` can be `management`, `public` or your own interface name.

## IP address interesting attributes

* `resolved-addres`: contains the actual IP address.
* `inet-address` : contains the expression used to compute the `resolved-address` attribute.

# management HTTP interface

Also known as the `management interface`.

## operation to get the management http interface resource

The operation to get the management http interface resource is: `/core-service=management/management-interface=http-interface:read-resource`

## management HTTP interface interesting attributes

* `http-authentication-factory`: elytron http-authentication-factory name used to secure the access to the management interface.
* `socket-binding` : The name of the socket binding name used for the management interface's socket.

# socket binding

IP ports associated to an IP interface

## operation to get all the socket binding resources

The operation to get all the socket binding resources is: `/socket-binding-group=standard-sockets:read-resource(recursive=true)`

## syntax of the operation to get a socket binding resource

The operation to get a socket binding resources is: `/socket-binding-group=standard-sockets/socket-binding=<socket binding name>:read-resource`

## socket binding interesting attributes

* `port`: The IP port
* `interface`: The interface resource name. If the `interface` attribute of the socket binding resource is not defined, the `interface` attribute value has to be retrieved
by calling the operation `/socket-binding-group=standard-sockets:read-attribute(name=default-interface)`

# deployment

## operation to get all the deployment resources

The operation to get all the deployment resources is: `:read-children-resources(child-type=deployment, include-runtime=true)`

## syntax of the operation to get a deployment resource

The operation to get a deployment resource is: `/deployment=<deployment name>:read-resource(include-runtime=true)`

## syntax of the operation to get the status of a deployment 

The operation to get the status of a deployment resource is: `/deployment=<deployment name>:read-resource(include-runtime=true)`
The value of the attribute `status` reflects the status of the deployment. `OK` means that the deployment is in a good state.

## syntax of the operation to get the paths of the file contained in a deployment

The operation to get the paths of the file contained in a deployment is: `/deployment=<deployment name>:browse-content()`

## syntax of the operation to get the content of a file contained in a deployment

The operation to get the content of a file contained in a deployment is: `/deployment=<deployment name>:read-content(path=<file path>)`
 
#  extension

extension contains the JBoss Modules module name that implements a subsystem. 
The name of an extension is composed of a list of words separated by a '.', for example `org.wildfly.extension.elytron`.

## syntax of the operation to get a extension

The operation to get an extension is: `/extension=<extension name>:read-resource(recursive=true, include-runtime=true)`

## operation to get all the extension

The operation to get all the extension is: `:read-children-resources(child-type=extension, recursive=true, include-runtime=true)`

## extension interesting attributes

* `module`: The JBoss Modules module name.
