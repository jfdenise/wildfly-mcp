# system property

## syntax of operations to update or create a system property

operation: `/system-property=<property name>:<operation>(<operation_arguments>)`
The possible values for `<operation>` are: `add`, `remove` or `write-attribute`.
If `add` is used, the `<operation_arguments>` is `value=<value of the system property>`.
If `remove` is used, the `<operation_arguments>` is empty.
If `write-attribute` is used, the `<operation_arguments>` is `name=value, value=<new value of the system property>`.

## syntax of the operation to get the value of a system property

operation: `/system-property=<property name>:read-attribute(name=value)`

## operation to list all the system property

operation: `:read-children-resources(child-type=system-property)`
An empty result returned by this operation means that no system properties are set.

# expression

## get or resolve the value of the expression

operation: `:resolve-expression(expression=<expression_value>})`
An `<expression_value>` can be an environment variable or a system property. Examples of expressions are `${JBOSS_HOME}` and `${jboss.node.name}`.

# interface

The interface references the IP address. 

## operation to list all the interface

operation: `:read-children-resources(child-type=interface, include-runtime=true)`
An empty result returned by this operation means that no IP interface are set.

## syntax of the operation to get an IP interface

operation: `/interface=<interface_name>:read-resource(include-runtime=true)`

## operation to get the public IP interface

operation: `/interface=public:read-resource(include-runtime=true)`

## operation to get the management IP interface

operation: `/interface=management:read-resource(include-runtime=true)`

## interface interesting attributes

* `resolved-address`: contains the actual IP address.
* `inet-address` : contains the expression used to compute the `resolved-address` attribute.

# core management

## operation to get the core management HTTP interface

The operation: `/core-service=management/management-interface=http-interface:read-resource`

## core management HTTP interface interesting attributes

* `http-authentication-factory`: elytron `http-authentication-factory` name used to secure the access to the management interface.
* `socket-binding` : The name of the socket binding name used for the management interface socket.

# socket binding

IP ports associated to an IP interface

## operation to get all the socket binding

operation: `/socket-binding-group=standard-sockets:read-resource(recursive=true)`

## syntax of the operation to get a socket binding

operation: `/socket-binding-group=standard-sockets/socket-binding=<socket binding name>:read-resource`

## socket binding interesting attributes

* `port`: The IP port
* `interface`: The interface resource name. If the `interface` attribute of the socket binding resource is not defined, the `interface` attribute value has to be retrieved
by calling the operation `/socket-binding-group=standard-sockets:read-attribute(name=default-interface)`

# deployment

## operation to get all the deployments

// getting the list of deployments is very general kind of question that can lead to a vague question of
// what are the deployments. With a small definition of what a deployment is, it provides better match.
Deployments are all the user applications.

operation: `:read-children-resources(child-type=deployment, include-runtime=true)`

## syntax of the operation to get a deployment

operation: `/deployment=<deployment name>:read-resource(include-runtime=true)`
The returned deployment contains all the attributes of the deployment, in particular its status. 

## syntax of the operation to get the paths of the file contained in a deployment

operation: `/deployment=<deployment name>:browse-content()`

## syntax of the operation to get the content of a file contained in a deployment

operation: `/deployment=<deployment name>:read-content(path=<file path>)`
 
# extension

extension contains the JBoss Modules module name. 
The name of an extension is composed of a list of words separated by a '.', for example `org.wildfly.extension.elytron`.

## syntax of the operation to get a extension

operation: `/extension=<extension name>:read-resource(recursive=true, include-runtime=true)`

## operation to get all the extension

operation: `:read-children-resources(child-type=extension, recursive=true, include-runtime=true)`

## extension interesting attributes

* `module`: The JBoss Modules module name.

# path

The name of a path is composed of a list of words separated by a '.', for example `jboss.server.config.dir`.

## syntax of the operation to get a path

operation: `/path=<path name>:read-resource(recursive=true)`

## operation to get all the path

operation: `:read-children-resources(child-type=path, recursive=true)`

# subsystem

a subsystem resource is a customizable feature of the server. A subsystem has a name
a subsystem contains a set of attributes.

## syntax of the operation to get a subsystem

operation: `/subsystem=<subsystem name>:read-resource()`

## operation to get all the subsystems

operation: `:read-children-resources(child-type=subsystem)`

## subsystem interesting attributes

* `capabilities`: The capabilities that the subsystem exposes to the other subsystems.

# `bean-validation` subsystem

## operation to get the `bean-validation` subsystem

operation: `/subsystem=bean-validation:read-resource()`

# datasources subsystem

Contains all the installed JDBC driver and all the defined datasources. A datasource references an installed JDBC driver.

## syntax of the operation to get all the JDBC driver

operation: `/subsystem=datasources:read-children-resources(child-type=jdbc-driver`

## syntax of the operation to get a JDBC driver

Well known JDBC drivers are: h2, postgresql, mariadb, mysql, oracle, mssqlserver.
operation: `/subsystem=datasources/jdbc-driver=<jdbc driver name>:read-resource`


