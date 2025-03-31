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

operation: `:resolve-expression(expression=<expression_value>})`
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

## syntax of the operation to get the data-source allocation-retry
The allocation retry element indicates the number of times that allocating a connection should be tried before throwing an exception
get the `data-source` `allocation-retry` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=allocation-retry)`

## syntax of the operation to get the data-source allocation-retry-wait-millis
The allocation retry wait millis element specifies the amount of time, in milliseconds, to wait between retrying to allocate a connection
get the `data-source` `allocation-retry-wait-millis` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=allocation-retry-wait-millis)`

## syntax of the operation to get the data-source allow-multiple-users
Specifies if multiple users will access the datasource through the getConnection(user, password) method and hence if the internal pool type should account for that
get the `data-source` `allow-multiple-users` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=allow-multiple-users)`

## syntax of the operation to get the data-source authentication-context
The Elytron authentication context which defines the javax.security.auth.Subject that is used to distinguish connections in the pool.
get the `data-source` `authentication-context` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=authentication-context)`

## syntax of the operation to get the data-source background-validation
An element to specify that connections should be validated on a background thread versus being validated prior to use. Changing this value can be done only on disabled datasource,  requires a server restart otherwise.
get the `data-source` `background-validation` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=background-validation)`

## syntax of the operation to get the data-source background-validation-millis
The background-validation-millis element specifies the amount of time, in milliseconds, that background validation will run. Changing this value can be done only on disabled datasource,  requires a server restart otherwise
get the `data-source` `background-validation-millis` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=background-validation-millis)`

## syntax of the operation to get the data-source blocking-timeout-wait-millis
The blocking-timeout-millis element specifies the maximum time, in milliseconds, to block while waiting for a connection before throwing an exception. Note that this blocks only while waiting for locking a connection, and will never throw an exception if creating a new connection takes an inordinately long time
get the `data-source` `blocking-timeout-wait-millis` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=blocking-timeout-wait-millis)`

## syntax of the operation to get the data-source capacity-decrementer-class
Class defining the policy for decrementing connections in the pool
get the `data-source` `capacity-decrementer-class` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=capacity-decrementer-class)`

## syntax of the operation to get the data-source capacity-decrementer-properties
Properties to be injected in class defining the policy for decrementing connections in the pool
get the `data-source` `capacity-decrementer-properties` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=capacity-decrementer-properties)`

## syntax of the operation to get the data-source capacity-incrementer-class
Class defining the policy for incrementing connections in the pool
get the `data-source` `capacity-incrementer-class` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=capacity-incrementer-class)`

## syntax of the operation to get the data-source capacity-incrementer-properties
Properties to be injected in class defining the policy for incrementing connections in the pool
get the `data-source` `capacity-incrementer-properties` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=capacity-incrementer-properties)`

## syntax of the operation to get the data-source check-valid-connection-sql
Specify an SQL statement to check validity of a pool connection. This may be called when managed connection is obtained from the pool
get the `data-source` `check-valid-connection-sql` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=check-valid-connection-sql)`

## syntax of the operation to get the data-source connectable
Enable the use of CMR. This feature means that a local resource can reliably participate in an XA transaction.
get the `data-source` `connectable` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=connectable)`

## syntax of the operation to get the data-source connection-listener-class
Speciefies class name extending org.jboss.jca.adapters.jdbc.spi.listener.ConnectionListener that provides a possible to listen for connection activation and passivation in order to perform actions before the connection is returned to the application or returned to the pool.
get the `data-source` `connection-listener-class` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=connection-listener-class)`

## syntax of the operation to get the data-source connection-listener-property
Properties to be injected in class specidied in connection-listener-class
get the `data-source` `connection-listener-property` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=connection-listener-property)`

## syntax of the operation to get the data-source connection-url
The JDBC driver connection URL
get the `data-source` `connection-url` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=connection-url)`

## syntax of the operation to get the data-source credential-reference
Credential (from Credential Store) to authenticate on data source
get the `data-source` `credential-reference` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=credential-reference)`

## syntax of the operation to get the data-source datasource-class
The fully qualified name of the JDBC datasource class
get the `data-source` `datasource-class` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=datasource-class)`

## syntax of the operation to get the data-source driver-class
The fully qualified name of the JDBC driver class
get the `data-source` `driver-class` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=driver-class)`

## syntax of the operation to get the data-source driver-name
Defines the JDBC driver the datasource should use. It is a symbolic name matching the the name of installed driver. In case the driver is deployed as a jar, the name is the name of the deployment unit
get the `data-source` `driver-name` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=driver-name)`

## syntax of the operation to get the data-source elytron-enabled
Enables Elytron security for handling authentication of connections. The Elytron authentication-context to be used will be current context if no context is specified (see authentication-context).
get the `data-source` `elytron-enabled` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=elytron-enabled)`

## syntax of the operation to get the data-source enabled
Specifies if the datasource should be enabled. Note this attribute will not be supported runtime in next versions.
get the `data-source` `enabled` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=enabled)`

## syntax of the operation to get the data-source enlistment-trace
Defines if WildFly/IronJacamar should record enlistment traces
get the `data-source` `enlistment-trace` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=enlistment-trace)`

## syntax of the operation to get the data-source exception-sorter-class-name
An org.jboss.jca.adapters.jdbc.ExceptionSorter that provides an isExceptionFatal(SQLException) method to validate if an exception should broadcast an error
get the `data-source` `exception-sorter-class-name` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=exception-sorter-class-name)`

## syntax of the operation to get the data-source exception-sorter-module
The name of the module which makes the implementation of org.jboss.jca.adapters.jdbc.ExceptionSorter available
get the `data-source` `exception-sorter-module` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=exception-sorter-module)`

## syntax of the operation to get the data-source exception-sorter-properties
The exception sorter properties
get the `data-source` `exception-sorter-properties` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=exception-sorter-properties)`

## syntax of the operation to get the data-source flush-strategy
Specifies how the pool should be flush in case of an error.
get the `data-source` `flush-strategy` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=flush-strategy)`

## syntax of the operation to get the data-source idle-timeout-minutes
The idle-timeout-minutes elements specifies the maximum time, in minutes, a connection may be idle before being closed. The actual maximum time depends also on the IdleRemover scan time, which is half of the smallest idle-timeout-minutes value of any pool. Changing this value can be done only on disabled datasource, requires a server restart otherwise.
get the `data-source` `idle-timeout-minutes` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=idle-timeout-minutes)`

## syntax of the operation to get the data-source initial-pool-size
The initial-pool-size element indicates the initial number of connections a pool should hold.
get the `data-source` `initial-pool-size` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=initial-pool-size)`

## syntax of the operation to get the data-source jndi-name
Specifies the JNDI name for the datasource
get the `data-source` `jndi-name` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=jndi-name)`

## syntax of the operation to get the data-source jta
Enable Jakarta Transactions integration
get the `data-source` `jta` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=jta)`

## syntax of the operation to get the data-source max-pool-size
The max-pool-size element specifies the maximum number of connections for a pool. No more connections will be created in each sub-pool
get the `data-source` `max-pool-size` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=max-pool-size)`

## syntax of the operation to get the data-source mcp
Defines the ManagedConnectionPool implementation, f.ex. org.jboss.jca.core.connectionmanager.pool.mcp.SemaphoreArrayListManagedConnectionPool
get the `data-source` `mcp` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=mcp)`

## syntax of the operation to get the data-source min-pool-size
The min-pool-size element specifies the minimum number of connections for a pool
get the `data-source` `min-pool-size` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=min-pool-size)`

## syntax of the operation to get the data-source new-connection-sql
Specifies an SQL statement to execute whenever a connection is added to the connection pool
get the `data-source` `new-connection-sql` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=new-connection-sql)`

## syntax of the operation to get the data-source password
Specifies the password used when creating a new connection
get the `data-source` `password` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=password)`

## syntax of the operation to get the data-source pool-fair
Defines if pool use should be fair
get the `data-source` `pool-fair` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=pool-fair)`

## syntax of the operation to get the data-source pool-prefill
Should the pool be prefilled. Changing this value can be done only on disabled datasource, requires a server restart otherwise.
get the `data-source` `pool-prefill` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=pool-prefill)`

## syntax of the operation to get the data-source pool-use-strict-min
Specifies if the min-pool-size should be considered strictly
get the `data-source` `pool-use-strict-min` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=pool-use-strict-min)`

## syntax of the operation to get the data-source prepared-statements-cache-size
The number of prepared statements per connection in an LRU cache
get the `data-source` `prepared-statements-cache-size` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=prepared-statements-cache-size)`

## syntax of the operation to get the data-source query-timeout
Any configured query timeout in seconds. If not provided no timeout will be set
get the `data-source` `query-timeout` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=query-timeout)`

## syntax of the operation to get the data-source reauth-plugin-class-name
The fully qualified class name of the reauthentication plugin implementation
get the `data-source` `reauth-plugin-class-name` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=reauth-plugin-class-name)`

## syntax of the operation to get the data-source reauth-plugin-properties
The properties for the reauthentication plugin
get the `data-source` `reauth-plugin-properties` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=reauth-plugin-properties)`

## syntax of the operation to get the data-source security-domain
Specifies the PicketBox security domain which defines the PicketBox javax.security.auth.Subject that are used to distinguish connections in the pool
get the `data-source` `security-domain` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=security-domain)`

## syntax of the operation to get the data-source set-tx-query-timeout
Whether to set the query timeout based on the time remaining until transaction timeout. Any configured query timeout will be used if there is no transaction
get the `data-source` `set-tx-query-timeout` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=set-tx-query-timeout)`

## syntax of the operation to get the data-source share-prepared-statements
Whether to share prepared statements, i.e. whether asking for same statement twice without closing uses the same underlying prepared statement
get the `data-source` `share-prepared-statements` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=share-prepared-statements)`

## syntax of the operation to get the data-source spy
Enable spying of SQL statements
get the `data-source` `spy` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=spy)`

## syntax of the operation to get the data-source stale-connection-checker-class-name
An org.jboss.jca.adapters.jdbc.StaleConnectionChecker that provides an isStaleConnection(SQLException) method which if it returns true will wrap the exception in an org.jboss.jca.adapters.jdbc.StaleConnectionException
get the `data-source` `stale-connection-checker-class-name` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=stale-connection-checker-class-name)`

## syntax of the operation to get the data-source stale-connection-checker-module
The name of the module which makes the implementation of org.jboss.jca.adapters.jdbc.StaleConnectionChecker available
get the `data-source` `stale-connection-checker-module` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=stale-connection-checker-module)`

## syntax of the operation to get the data-source stale-connection-checker-properties
The stale connection checker properties
get the `data-source` `stale-connection-checker-properties` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=stale-connection-checker-properties)`

## syntax of the operation to get the data-source statistics-enabled
Define whether runtime statistics are enabled or not.
get the `data-source` `statistics-enabled` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=statistics-enabled)`

## syntax of the operation to get the data-source track-statements
Whether to check for unclosed statements when a connection is returned to the pool, result sets are closed, a statement is closed or return to the prepared statement cache. Valid values are: "false" - do not track statements, "true" - track statements and result sets and warn when they are not closed, "nowarn" - track statements but do not warn about them being unclosed
get the `data-source` `track-statements` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=track-statements)`

## syntax of the operation to get the data-source tracking
Defines if IronJacamar should track connection handles across transaction boundaries
get the `data-source` `tracking` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=tracking)`

## syntax of the operation to get the data-source transaction-isolation
Set the java.sql.Connection transaction isolation level. Valid values are: TRANSACTION_READ_UNCOMMITTED, TRANSACTION_READ_COMMITTED, TRANSACTION_REPEATABLE_READ, TRANSACTION_SERIALIZABLE and TRANSACTION_NONE. Different values are used to set customLevel using TransactionIsolation#customLevel
get the `data-source` `transaction-isolation` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=transaction-isolation)`

## syntax of the operation to get the data-source url-delimiter
Specifies the delimiter for URLs in connection-url for HA datasources
get the `data-source` `url-delimiter` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=url-delimiter)`

## syntax of the operation to get the data-source url-selector-strategy-class-name
A class that implements org.jboss.jca.adapters.jdbc.URLSelectorStrategy
get the `data-source` `url-selector-strategy-class-name` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=url-selector-strategy-class-name)`

## syntax of the operation to get the data-source use-ccm
Enable the use of a cached connection manager
get the `data-source` `use-ccm` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=use-ccm)`

## syntax of the operation to get the data-source use-fast-fail
Whether to fail a connection allocation on the first try if it is invalid (true) or keep trying until the pool is exhausted of all potential connections (false)
get the `data-source` `use-fast-fail` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=use-fast-fail)`

## syntax of the operation to get the data-source use-java-context
Setting this to false will bind the datasource into global JNDI
get the `data-source` `use-java-context` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=use-java-context)`

## syntax of the operation to get the data-source use-try-lock
Any configured timeout for internal locks on the resource adapter objects in seconds
get the `data-source` `use-try-lock` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=use-try-lock)`

## syntax of the operation to get the data-source user-name
Specify the user name used when creating a new connection
get the `data-source` `user-name` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=user-name)`

## syntax of the operation to get the data-source valid-connection-checker-class-name
An org.jboss.jca.adapters.jdbc.ValidConnectionChecker that provides an isValidConnection(Connection) method to validate a connection. If an exception is returned that means the connection is invalid. This overrides the check-valid-connection-sql element
get the `data-source` `valid-connection-checker-class-name` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=valid-connection-checker-class-name)`

## syntax of the operation to get the data-source valid-connection-checker-module
The name of the module which makes the implementation of org.jboss.jca.adapters.jdbc.ValidConnectionChecker available
get the `data-source` `valid-connection-checker-module` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=valid-connection-checker-module)`

## syntax of the operation to get the data-source valid-connection-checker-properties
The valid connection checker properties
get the `data-source` `valid-connection-checker-properties` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=valid-connection-checker-properties)`

## syntax of the operation to get the data-source validate-on-match
The validate-on-match element specifies if connection validation should be done when a connection factory attempts to match a managed connection. This is typically exclusive to the use of background validation
get the `data-source` `validate-on-match` attribute.
operation: `/subsystem=datasources/data-source=<data-source name>:read-attribute(name=validate-on-match)`