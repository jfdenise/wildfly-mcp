# WildFly server

A server has a `name`, a `product-name`, a `version`, a `running-mode`, a `server-state` and a `suspend-state`.

## get the server

get all the information on the server.
information on the server is composed of attributes and children.

operation: `/:read-resource(include-runtime=true)`

## get the name

get the server `name` attribute.
operation: `/:read-attribute(name=name)`

## get the product name

get the server `product-name` attribute.
operation: `/:read-attribute(name=product-name)`

## get the version

get the server `version` attribute.
operation: `/:read-attribute(name=version)`

## get the running mode

In which mode the server is running.
get the server `running-mode` attribute.
operation: `/:read-attribute(name=running-mode)`

## get the server state

What is the state of the server.
get the server `server-state` attribute.
operation: `/:read-attribute(name=server-state)`

## get the suspend state

get the server `suspend-state` attribute.
operation: `/:read-attribute(name=suspend-state)`

# system property

## syntax of the operation to add or create a system property

operation: `/system-property=<property name>:add(value=<value of the system property>)`

## syntax of the operation to remove or delete a system property

operation: `/system-property=<property name>:remove()`

## syntax of the operation to update a system property

operation: `/system-property=<property name>:write-attribute(name=value, value=<value of the system property>)`

## syntax of the operation to get the value of a system property

operation: `/system-property=<property name>:read-attribute(name=value)`

To get the list of all the system property use '*' for `<property name>`.
An empty result returned by this operation means that no system properties are set.

# expression

## get or resolve the value of the expression

operation: `:resolve-expression(expression=<expression_value>)`
An `<expression_value>` can be an environment variable or a system property. Examples of expressions are `${JBOSS_HOME}` and `${jboss.node.name}`.

# interface

The interface references the IP address. 

## syntax of the operation to get an IP interface

operation: `/interface=<interface_name>:read-resource(include-runtime=true)`

To get the list of all the interfaces use '*' for `<interface_name>`.

## operation to get the public IP interface

operation: `/interface=public:read-resource(include-runtime=true)`

## operation to get the management IP interface

operation: `/interface=management:read-resource(include-runtime=true)`

# core management

## operation to get the core management HTTP interface

The operation: `/core-service=management/management-interface=http-interface:read-resource`

## core management HTTP interface interesting attributes

* `http-authentication-factory`: elytron `http-authentication-factory` name used to secure the access to the management interface.
* `socket-binding` : The name of the socket binding name used for the management interface socket.

# socket binding

A socket-binding contains the IP socket information: the port on the interface the server is bound to.
If the `interface` attribute of the socket binding resource is not defined, the `interface` attribute value has to be retrieved
by calling the operation `/socket-binding-group=standard-sockets:read-attribute(name=default-interface)`
Some well known socket-bindings are: http, https, management-http and management-https.

## syntax of the operation to get a socket binding

operation: `/socket-binding-group=standard-sockets/socket-binding=<socket binding name>:read-resource`
To get the list of all the interfaces use '*' for `<socket binding name>`.

# deployment

A deployment is a user application deployed in the server.
Example for <deployment name> is `myapp.war`.

## syntax of the operation to get a deployment

operation: `/deployment=<deployment name>:read-resource(include-runtime=true)`

The returned deployment contains all the attributes of the deployment, in particular its status.
To get the list of all the deployment use '*' for `<deployment name>`.

## syntax of the operation to get the files contained in a deployment

operation: `/deployment=<deployment name>:browse-content()`
Examples of returned paths: `WEB-INF/``web.xml`, `WEB-INF/``jboss-web.xml`, `WEB-INF/``classes/persistence.xml`.

syntax of the operation to get the content of a file contained in the deployment
operation: `/deployment=<deployment name>:read-content(path=<file path>)`

# extension

extension references the JBoss Modules module name. 
The name of an extension is composed of a list of words separated by a '.', for example `org.wildfly.extension.elytron`.

## syntax of the operation to get a extension

operation: `/extension=<extension name>:read-resource(recursive=true, include-runtime=true)`
To get the list of all the extension use '*' for `<extension name>`.

# path

The name of a path is composed of a list of words separated by a '.', for example `jboss.server.config.dir`.

## syntax of the operation to get a path

operation: `/path=<path name>:read-resource(recursive=true)`
To get the list of all the path use '*' for `<path name>`.

# subsystem

A subsystem has a name
a subsystem contains a set of attributes.

## syntax of the operation to get a subsystem

operation: `/subsystem=<subsystem name>:read-resource()`
To get the list of all the subsystem use '*' for `<subsystem name>`.

# `bean-validation` subsystem

## operation to get the `bean-validation` subsystem

operation: `/subsystem=bean-validation:read-resource()`

# datasources subsystem

Contains all the installed JDBC driver and all the defined `data-source` and `xa-data-source`. 

## syntax of the operation to get a JDBC driver

Well known JDBC drivers are: h2, postgresql, mariadb, mysql, oracle, mssqlserver.
operation: `/subsystem=datasources/jdbc-driver=<jdbc driver name>:read-resource`
To get the list of all the driver use '*' for `<jdbc driver name>`.

## syntax of the operation to get a data-source

operation: `/subsystem=datasources/data-source=<data-source name>:read-resource`
To get the list of all the `data-sources` use '*' for `<data-source name>`.
