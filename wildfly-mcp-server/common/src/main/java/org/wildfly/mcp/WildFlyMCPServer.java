/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.sun.management.OperatingSystemMXBean;

import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.TextContent;
import java.util.List;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.rest.client.reactive.Url;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.core.Response;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.conn.HttpHostConnectException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.resteasy.reactive.ClientWebApplicationException;
import org.wildfly.mcp.WildFlyManagementClient.AddLoggerRequest;
import org.wildfly.mcp.WildFlyManagementClient.GetLoggersRequest;
import org.wildfly.mcp.WildFlyManagementClient.GetLoggersResponse;
import org.wildfly.mcp.WildFlyManagementClient.GetLoggingFileRequest;
import org.wildfly.mcp.WildFlyManagementClient.GetLoggingFileResponse;
import org.wildfly.mcp.WildFlyManagementClient.RemoveLoggerRequest;

public class WildFlyMCPServer {

    static final Logger LOGGER = Logger.getLogger("org.wildfly.mcp.WildFlyMCPServer");

    public record Status(
            String name,
            String outcome,
            List<HealthValue> data) {

    }

    public record HealthValue(
            String value) {

    }

    WildFlyManagementClient wildflyClient = new WildFlyManagementClient();
    @RestClient
    WildFlyMetricsClient wildflyMetricsClient;
    @RestClient
    WildFlyHealthClient wildflyHealthClient;

    @Tool(description = "Get the list of the enabled logging categories")
    @RolesAllowed("admin")
    ToolResponse getWildFlyLoggingCategories(
            @ToolArg(name = "host", required = false) String host,
            @ToolArg(name = "port", required = false) String port) {
        Server server = new Server(host, port);
        try {
            User user = new User();
            GetLoggersResponse response = wildflyClient.call(new GetLoggersRequest(server, user));
            Set<String> enabled = new TreeSet<>();
            for (String e : response.result) {
                enabled.addAll(getHighLevelCategory(e));
            }
            return buildResponse("The list of enabled logging caterories is: " + enabled);
        } catch (Exception ex) {
            return handleException(ex, server, "retrieving the logging categories");
        }
    }

    @Tool(description = "Gets the server configuration.")
    @RolesAllowed("admin")
    ToolResponse getWildFlyServerConfiguration(
            @ToolArg(name = "host", required = false) String host,
            @ToolArg(name = "port", required = false) String port) {
        Server server = new Server(host, port);
        try {
            User user = new User();
            CommandContext ctx = CommandContextFactory.getInstance().newCommandContext();
            // This call, if done with the Monitor role, will be filtered. No sensitive information present.
            ModelNode mn = ctx.buildRequest(":read-resource(recursive=true)");
            String value = wildflyClient.call(server, user, mn.toJSONString(false), false);
            ModelNode node = ModelNode.fromJSONString(value);
            ModelNode result = node.get("result");
            // enforce some cleanup
            result.remove("extension");
            result.remove("core-service");
            result.remove("path");
            result.remove("system-properties");
            result.get("subsystem").remove("elytron");            
            cleanupUndefined(result);
            // add back but empty.
            result.get("subsystem").get("elytron");
            return buildResponse(result.toJSONString(false));
        } catch (Exception ex) {
            return handleException(ex, server, "retrieving the server configuration");
        }
    }

    @Tool(description = "Get all the file paths contained inside an application deployment.")
    @RolesAllowed("admin")
    ToolResponse getDeploymentPaths(
            @ToolArg(name = "host", required = false) String host,
            @ToolArg(name = "port", required = false) String port,
            @ToolArg(name = "name", required = false) String name) {
        Server server = new Server(host, port);
        try {
            User user = new User();
            CommandContext ctx = CommandContextFactory.getInstance().newCommandContext();
            name = (name == null || name.isEmpty()) ? "ROOT.war" : name;
            ModelNode mn = ctx.buildRequest("/deployment=" + name + ":browse-content");
            String value = wildflyClient.call(server, user, mn.toJSONString(false), false);
            ModelNode node = ModelNode.fromJSONString(value);
            ModelNode result = node.get("result");
            return buildResponse(result.toJSONString(false));
        } catch (Exception ex) {
            return handleException(ex, server, "browsing the deployment");
        }
    }

    @Tool(description = "Get the content of a file located inside a deployment.")
    @RolesAllowed("admin")
    ToolResponse getDeploymentFileContent(
            @ToolArg(name = "host", required = false) String host,
            @ToolArg(name = "port", required = false) String port,
            @ToolArg(name = "name", required = false) String name,
            @ToolArg(name = "path", required = true) String path) {
        Server server = new Server(host, port);
        try {
            User user = new User();
            name = (name == null || name.isEmpty()) ? "ROOT.war" : name;
            CommandContext ctx = CommandContextFactory.getInstance().newCommandContext();
            // This call, if done with the Monitor role, will be filtered. No sensitive information present.
            ModelNode mn = ctx.buildRequest("/deployment=" + name + ":read-content(path=" + path + ")");
            String value = wildflyClient.call(server, user, mn.toJSONString(false), true);
            return buildResponse(value);
        } catch (Exception ex) {
            return handleException(ex, server, "retrieving the logging categories");
        }
    }

    private static void cleanupUndefined(ModelNode mn) {
        Set<String> toRemove = new HashSet<>();
        for (String key : mn.keys()) {
            ModelNode field = mn.get(key);
            if (!field.isDefined()) {
                toRemove.add(key);
            } else {
                if (field.getType() == ModelType.OBJECT) {
                    cleanupUndefined(field);
                }
            }
        }
        for (String k : toRemove) {
            mn.remove(k);
        }
    }

    @Tool(description = "Invoke a WildFly CLI operation.")
    @RolesAllowed("admin")
    ToolResponse invokeWildFlyCLIOperation(
            @ToolArg(name = "host", required = false) String host,
            @ToolArg(name = "port", required = false) String port,
            String operation) {
        Server server = new Server(host, port);
        try {
            User user = new User();
            CommandContext ctx = CommandContextFactory.getInstance().newCommandContext();
            ModelNode mn = ctx.buildRequest(operation);
            // TODO, implement possible rules if needed to disallow some operations.
            String value = wildflyClient.call(server, user, mn.toJSONString(false), false);
            return buildResponse(value);
        } catch (Exception ex) {
            return handleException(ex, server, "invoking operations ");
        }
    }

    @Tool(description = "Enable a logging category.")
    @RolesAllowed("admin")
    ToolResponse enableWildFlyLoggingCategory(
            @ToolArg(name = "host", required = false) String host,
            @ToolArg(name = "port", required = false) String port,
            String loggingCategory) {
        Server server = new Server(host, port);
        try {
            User user = new User();
            String category = findCategory(loggingCategory);
            GetLoggersResponse response = wildflyClient.call(new GetLoggersRequest(server, user));
            if (response.result != null && response.result.contains(category)) {
                return buildErrorResponse("The logging category " + loggingCategory + " is already enabled. You can get the enabled logging categories from the getLoggingCategories operation.");
            }
            wildflyClient.call(new AddLoggerRequest(server, user, category));
            return buildResponse("The logging category " + loggingCategory + " has been enabled by using the " + category + " logger");
        } catch (Exception ex) {
            return handleException(ex, server, "enabling the logger " + loggingCategory);
        }
    }

    @Tool(description = "Disable a logging category.")
    @RolesAllowed("admin")
    ToolResponse disableWildFlyLoggingCategory(
            @ToolArg(name = "host", required = false) String host,
            @ToolArg(name = "port", required = false) String port,
            String loggingCategory) {
        Server server = new Server(host, port);
        try {
            User user = new User();
            String category = findCategory(loggingCategory);
            GetLoggersResponse response = wildflyClient.call(new GetLoggersRequest(server, user));
            if (response.result != null && !response.result.contains(category)) {
                return buildErrorResponse("The logging category " + loggingCategory + " is not already enabled, you should first enabled it.");
            }
            wildflyClient.call(new RemoveLoggerRequest(server, user, category));
            return buildResponse("The logging category " + loggingCategory + " has been removed by using the " + category + " logger.");
        } catch (Exception ex) {
            return handleException(ex, server, "disabling the logger " + loggingCategory);
        }
    }

    @Tool(description = "Get the log file content.")
    @RolesAllowed("admin")
    ToolResponse getWildFlyLogFileContent(
            @ToolArg(name = "host", required = false) String host,
            @ToolArg(name = "port", required = false) String port,
            @ToolArg(name = "numberOfLines", description = "200 by default, use `-1` for all lines.", required = false) String numLines) {
        Server server = new Server(host, port);
        try {
            User user = new User();
            GetLoggingFileResponse response = wildflyClient.call(new GetLoggingFileRequest(server, numLines, user));
            StringBuilder builder = new StringBuilder();
            for (String line : response.result) {
                builder.append(line).append("\n");
            }
            return buildResponse("WildFly server log file Content: `" + builder.toString() + "`");
        } catch (Exception ex) {
            return handleException(ex, server, "retrieving the log file ");
        }
    }

    @Tool(description = "Get the Server version and JVM configuration.")
    @RolesAllowed("admin")
    ToolResponse getWildFlyServerInfo(
            @ToolArg(name = "host", required = false) String host,
            @ToolArg(name = "port", required = false) String port) {
        Server server = new Server(host, port);
        User user = new User();
        try {
            VMInfo info = new VMInfo();
            try (JMXSession session = new JMXSession(server, user)) {
                RuntimeMXBean proxy
                        = ManagementFactory.newPlatformMXBeanProxy(session.connection,
                                ManagementFactory.RUNTIME_MXBEAN_NAME,
                                RuntimeMXBean.class);
                info.name = proxy.getName();
                info.inputArguments = proxy.getInputArguments();
                info.specName = proxy.getSpecName();
                info.specVendor = proxy.getSpecVendor();
                info.specVersion = proxy.getSpecVersion();
                info.startTime = "" + new Date(proxy.getStartTime());
                info.upTime = (proxy.getUptime() / 1000) + "seconds";
                info.vmName = proxy.getVmName();
                info.vmVendor = proxy.getVmVendor();
                info.vmVersion = proxy.getVmVersion();

                OperatingSystemMXBean proxy2
                        = ManagementFactory.newPlatformMXBeanProxy(session.connection,
                                ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME,
                                OperatingSystemMXBean.class);
                double val = proxy2.getProcessCpuLoad();
                info.consumedCPU = "" + (int) val + "%";

                MemoryMXBean proxy3
                        = ManagementFactory.newPlatformMXBeanProxy(session.connection,
                                ManagementFactory.MEMORY_MXBEAN_NAME,
                                MemoryMXBean.class);
                double max = proxy3.getHeapMemoryUsage().getMax();
                double used = proxy3.getHeapMemoryUsage().getUsed();
                double result = (used * 100) / max;
                info.consumedMemory = "" + (int) result + "%";
            }
            ServerInfo serverInfo = new ServerInfo();
            serverInfo.vmInfo = info;
            CommandContext ctx = CommandContextFactory.getInstance().newCommandContext();
            ModelNode mn = ctx.buildRequest(":read-resource(recursive=false)");
            String value = wildflyClient.call(server, user, mn.toJSONString(false), false);
            ModelNode node = ModelNode.fromJSONString(value);
            ModelNode result = node.get("result");
            serverInfo.nodeName = result.get("name").asString();
            serverInfo.productName = result.get("product-name").asString();
            serverInfo.productVersion = result.get("product-version").asString();
            serverInfo.coreVersion = result.get("release-version").asString();
            return buildResponse(toJson(serverInfo));

        } catch (Exception ex) {
            return handleException(ex, server, "retrieving the consumed memory");
        }
    }

    @Tool(description = "Get the prometheus metrics.")
    ToolResponse getWildFlyPrometheusMetrics(
            @ToolArg(name = "host", required = false) String host,
            @ToolArg(name = "port", required = false) String port) {
        Server server = new Server(host, port);
        try {
            String url = "http://" + server.host + ":" + server.port + "/metrics";
            try {
                return buildResponse(wildflyMetricsClient.getMetrics(url));
            } catch (ClientWebApplicationException ex) {
                if (ex.getResponse().getStatus() == 404) {
                    return buildResponse("The WildFly metrics are not available in the WildFly server running on " + server.host + ":" + server.port);
                } else {
                    throw ex;
                }
            }
        } catch (Exception ex) {
            return handleException(ex, server, "retrieving the metrics");
        }
    }

    String toJson(Object value) throws JsonProcessingException {
        ObjectWriter ow = new ObjectMapper().writer();
        return ow.writeValueAsString(value);
    }

    @Tool(description = "Get the status of the WildFly server and deployments.")
    ToolResponse getWildFlyHealth(
            @ToolArg(name = "host", required = false) String host,
            @ToolArg(name = "port", required = false) String port) {
        Server server = new Server(host, port);
        try {
            String url = "http://" + server.host + ":" + server.port + "/health";
            try {
                return buildResponse(wildflyHealthClient.getHealth(url));
            } catch (ClientWebApplicationException ex) {
                if (ex.getResponse().getStatus() == 404) {
                    User user = new User();
                    WildFlyStatus status = wildflyClient.getStatus(server, user);
                    List<String> ret = new ArrayList<>();
                    ret.addAll(status.getStatus());
                    return buildResponse(ret.toArray(String[]::new));
                } else {
                    throw ex;
                }
            }
        } catch (Exception ex) {
            return handleException(ex, server, "retrieving the health");
        }
    }

    @Prompt(name = "wildFly-prometheus-metrics-chart", description = "WildFly, prometheus metrics chart")
    PromptMessage prometheusMetricsChart(@PromptArg(name = "host",
            description = "Optional WildFly server host name. By default localhost is used.",
            required = false) String host,
            @PromptArg(name = "port",
                    description = "Optional WildFly server port. By default 9990 is used.",
                    required = false) String port) {
        Server server = new Server(host, port);
        return PromptMessage.withUserRole(new TextContent("Using available tools, get Prometheus metrics from wildfly server Wildfly server running on host " + server.host + ", port " + server.port
                + ". You will repeat the invocation 3 times, being sure to wait 5 seconds between each invocation. "
                + "After all the 3 invocation has been completed you will organize the data in a table. "
                + "Then you will use this table to create a bar chart to visually compare the data. "
                + "Be sure to use at least 5 different data column and be sure to represent all data as bar in the chart"));
    }

    @Prompt(name = "wildFly-security-audit", description = "WildFly, security audit. Analyze the server log file for potential attacks")
    PromptMessage securityAudit(@PromptArg(name = "loggingCategories",
            description = "Comma separated list of logging categories to enable. By default the security category is enabled.",
            required = false) String arg,
            @PromptArg(name = "host",
                    description = "Optional WildFly server host name. By default localhost is used.",
                    required = false) String host,
            @PromptArg(name = "port",
                    description = "Optional WildFly server port. By default 9990 is used.",
                    required = false) String port) {
        Server server = new Server(host, port);
        String additionalCategories = (arg == null || arg.isEmpty()) ? "" : " " + arg;
        return PromptMessage.withUserRole(new TextContent("Using available tools, enable the org.wildfly.security" + additionalCategories + " logging categories of the Wildfly server running on host " + server.host + ", port " + server.port
                + ". Then wait 10 seconds. Finally get the server log file, analyze it and report issues related to authentication failures."));
    }

    @Prompt(name = "wildFly-resources-consumption", description = "WildFly and JVM resource consumption status. Analyze the consumed resources.")
    PromptMessage consumedResources(@PromptArg(name = "host",
            description = "Optional WildFly server host name. By default localhost is used.",
            required = false) String host,
            @PromptArg(name = "port",
                    description = "Optional WildFly server port. By default 9990 is used.",
                    required = false) String port) {
        Server server = new Server(host, port);
        return PromptMessage.withUserRole(new TextContent("Using available tools, Could you check the consumed resources of the JVM and the Wildfly server running on host " + server.host + ", port " + server.port + ": consumed memory, max memory and cpu utilization, prometheus metrics. Mainly anything that could impact running my application. In addition could you verify that the JVM input arguments are well suited to run my server? "
                + "Your reply should be short with a strong focus on what is wrong, how the JVM arguments are set and the recommendations."));
    }
    
    @Prompt(name = "wildFly-deployment-errors", description = "WildFly deployed applications, identify potential for errors.")
    PromptMessage deploymentError(@PromptArg(name = "host",
            description = "Optional WildFly server host name. By default localhost is used.",
            required = false) String host,
            @PromptArg(name = "port",
                    description = "Optional WildFly server port. By default 9990 is used.",
                    required = false) String port) {
        Server server = new Server(host, port);
        return PromptMessage.withUserRole(new TextContent("Using available tools, Could you check that the deployed applications in the Wildfly server running on host " + server.host + ", port " + server.port + " are correct? Correct means that there are no error related to the deployment in the server log files. "
                + "Incorrect means that there are errors and possibly exceptions related to the deployment. If you found an incorrect state, please access the web.xml and jboss-web.xml file and check for faulty content that could explain the error seen in the log file."));
    }

    @RegisterRestClient(baseUri = "http://foo:9990/metrics/")
    public interface WildFlyMetricsClient {

        @GET
        String getMetrics(@Url String url);
    }

    @RegisterRestClient(baseUri = "http://foo:9990/health")
    public interface WildFlyHealthClient {

        @GET
        String getHealth(@Url String url);
    }

    private String findCategory(String category) {
        category = category.toLowerCase();
        if (category.contains("security")) {
            return "org.wildfly.security";
        } else {
            if (category.contains("web") || category.contains("http")) {
                return "io.undertow";
            }
            return category.trim();
        }
    }

    Set<String> getHighLevelCategory(String logger) {
        Set<String> hc = new TreeSet<>();
        if (logger.equals("org.wildfly.security")) {
            hc.add("security");
        } else {
            if (logger.equals("io.undertow")) {
                hc.add("web");
                hc.add("http");
            } else {
                hc.add(logger);
            }
        }
        return hc;
    }

    private ToolResponse handleException(Exception ex, Server server, String action) {
        LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
        if (ex instanceof ClientWebApplicationException clientWebApplicationException) {
            Response resp = clientWebApplicationException.getResponse();
            resp.getStatus();
            return buildErrorResponse(" Error when " + action + " for the server running on host " + server.host + " and port " + server.port);
        } else {
            if (ex instanceof AuthenticationException || ex instanceof ForbiddenException) {
                return buildErrorResponse(ex.getMessage());
            } else {
                if (ex instanceof HttpHostConnectException) {
                    return buildErrorResponse(" Error when connecting to the server " + server.host + ":" + server.port + ". Could you check the host and port.");
                } else {
                    if (ex instanceof UnknownHostException) {
                        return buildErrorResponse("The server host " + server.host + " is not a known server name");
                    } else {
                        return buildErrorResponse(ex.getMessage());
                    }
                }
            }
        }
    }

    private ToolResponse buildResponse(String... content) {
        return buildResponse(false, content);
    }

    private ToolResponse buildErrorResponse(String... content) {
        return buildResponse(true, content);
    }

    private ToolResponse buildResponse(boolean isError, String... content) {
        List<TextContent> lst = new ArrayList<>();
        for (String str : content) {
            TextContent text = new TextContent(str);
            lst.add(text);
        }
        return new ToolResponse(isError, lst);
    }
}
