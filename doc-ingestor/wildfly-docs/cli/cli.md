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

# expression

## get or resolve the value of the expression

The syntax of an operation to resolve an expression is: `:resolve-expression(expression=<expression_value>})`
An `<expression_value>` can be an environment variable or a system property. Examples of expressions are `${JBOSS_HOME}` and `${jboss.node.name}`.

# interface

The interface references the IP address. 

## operation to list all the interface

The operation to list all the interface is: `:read-children-resources(child-type=interface, include-runtime=true)`
An empty result returned by this operation means that no IP interface are set.

## syntax of the operation to get an interface

The syntax of an operation to get the resource of the IP interface is: `/interface=<interface_name>:read-resource(include-runtime=true)`

## operation to get the public IP interface

operation to get the public interface is: `/interface=public:read-resource(include-runtime=true)`

## operation to get the management IP interface

operation to get the management interface is: `/interface=management:read-resource(include-runtime=true)`

## interface interesting attributes

* `resolved-addres`: contains the actual IP address.
* `inet-address` : contains the expression used to compute the `resolved-address` attribute.

# core management

## operation to get the core management HTTP interface

The operation to get the core management HTTP interface is: `/core-service=management/management-interface=http-interface:read-resource`

## core management HTTP interface interesting attributes

* `http-authentication-factory`: elytron http-authentication-factory name used to secure the access to the management interface.
* `socket-binding` : The name of the socket binding name used for the management interface's socket.

# socket binding

IP ports associated to an IP interface

## operation to get all the socket binding

The operation to get all the socket binding resources is: `/socket-binding-group=standard-sockets:read-resource(recursive=true)`

## syntax of the operation to get a socket binding

The operation to get a socket binding resources is: `/socket-binding-group=standard-sockets/socket-binding=<socket binding name>:read-resource`

## socket binding interesting attributes

* `port`: The IP port
* `interface`: The interface resource name. If the `interface` attribute of the socket binding resource is not defined, the `interface` attribute value has to be retrieved
by calling the operation `/socket-binding-group=standard-sockets:read-attribute(name=default-interface)`

# deployment


## operation to get all the deployments

// getting the list of deployments is very general kind of question that can lead to a vague question of
// what are the deployments. With a small definition of what a deployment is, it provides better match.
Deployments are all the user applications.

The operation to get all the deployments is: `:read-children-resources(child-type=deployment, include-runtime=true)`

## syntax of the operation to get a deployment

The operation to get a deployment is: `/deployment=<deployment name>:read-resource(include-runtime=true)`
The returned deployment contains all the attributes of the deployment, in particular its status. 

## syntax of the operation to get the paths of the file contained in a deployment

The operation to get the paths of the file contained in a deployment is: `/deployment=<deployment name>:browse-content()`

## syntax of the operation to get the content of a file contained in a deployment

The operation to get the content of a file contained in a deployment is: `/deployment=<deployment name>:read-content(path=<file path>)`
 
# extension

extension contains the JBoss Modules module name. 
The name of an extension is composed of a list of words separated by a '.', for example `org.wildfly.extension.elytron`.

## syntax of the operation to get a extension

The operation to get an extension is: `/extension=<extension name>:read-resource(recursive=true, include-runtime=true)`

## operation to get all the extension

The operation to get all the extension is: `:read-children-resources(child-type=extension, recursive=true, include-runtime=true)`

## extension interesting attributes

* `module`: The JBoss Modules module name.

# path

The name of a path is composed of a list of words separated by a '.', for example `jboss.server.config.dir`.

## syntax of the operation to get a path

The operation to get a path is: `/path=<path name>:read-resource(recursive=true)`

## operation to get all the path

The operation to get all the path is: `:read-children-resources(child-type=path, recursive=true)`

# subsystem

a subsystem resource is a customizable feature of the server. A subsystem has a name


## syntax of the operation to get a subsystem

A subsystem contains a set of attributes.
The operation to get a subsystem is: `/subsystem=<subsystem name>:read-resource()`

## operation to get all the subsystems

The operation to get all the subsystem is: `:read-children-resources(child-type=subsystem)`

