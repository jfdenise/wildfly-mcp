/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

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
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.conn.HttpHostConnectException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.resteasy.reactive.ClientWebApplicationException;
import org.wildfly.mcp.WildFlyControllerClient.AddLoggerRequest;
import org.wildfly.mcp.WildFlyControllerClient.EnableLoggerRequest;
import org.wildfly.mcp.WildFlyControllerClient.GetLoggersRequest;
import org.wildfly.mcp.WildFlyControllerClient.GetLoggingFileRequest;
import org.wildfly.mcp.WildFlyControllerClient.GetMemoryMXBean;
import org.wildfly.mcp.WildFlyControllerClient.GetOperatingSystemMXBean;
import org.wildfly.mcp.WildFlyControllerClient.GetRuntimeMXBean;
import org.wildfly.mcp.WildFlyControllerClient.RemoveLoggerRequest;
import org.wildfly.mcp.WildFlyControllerClient.CheckDeploymentRequest;
import org.jboss.as.cli.CommandLineException;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.wildfly.channel.Channel;
import org.wildfly.glow.AddOn;
import org.wildfly.glow.Arguments;
import org.wildfly.glow.GlowMessageWriter;
import org.wildfly.glow.GlowSession;
import org.wildfly.glow.OutputContent;
import org.wildfly.glow.OutputFormat;
import org.wildfly.glow.ScanArguments;
import org.wildfly.glow.ScanResults;
import org.wildfly.glow.maven.MavenResolver;
import org.wildfly.mcp.WildFlyControllerClient.GetLoggingMXBean;
import org.wildfly.mcp.WildFlyControllerClient.ShutdownRequest;
import org.wildfly.mcp.WildFlyControllerClient.UndeployRequest;
import org.wildfly.prospero.actions.ProvisioningAction;
import org.wildfly.prospero.actions.UpdateAction;
import org.wildfly.prospero.api.ArtifactChange;
import org.wildfly.prospero.api.Console;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.api.ProvisioningDefinition;
import org.wildfly.prospero.api.ProvisioningProgressEvent;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.ChannelUtils;
import org.wildfly.prospero.cli.RepositoryDefinition;
import org.wildfly.prospero.cli.commands.options.LocalRepoOptions;
import org.wildfly.prospero.updates.UpdateSet;

public class WildFlyMCPServer {
    private static final String LAYER_TEMPLATE = "<include name=\"###LAYER###\"/>";
    private static final String FEATURE_PACK_TEMPLATE = """
                                                        <feature-pack location="###LOCATION###">
                                                         <default-configs inherit="false"/>
                                                         <packages inherit="false"/>
                                                       </feature-pack>
                                                        """;
    private static final String PROVISIONING_XML_TEMPLATE = """
                                                     <?xml version="1.0" ?>
                                                     <installation xmlns="urn:jboss:galleon:provisioning:3.0">
                                                         ###FEATURE_PACKS###
                                                         <config model="standalone" name="standalone.xml">
                                                             <layers>
                                                                 ###LAYERS###
                                                             </layers>
                                                         </config>
                                                         <options>
                                                             <option name="jboss-fork-embedded"/>
                                                             <option name="optional-packages" value="passive+"/>
                                                         </options>
                                                     </installation>
                                                     """;
    private static final class MCPConsole implements Console {

        @Override
        public void progressUpdate(ProvisioningProgressEvent ppe) {
        }

        @Override
        public void println(String string) {
        }
    
}
    static final Logger LOGGER = Logger.getLogger("org.wildfly.mcp.WildFlyMCPServer");

    public record Status(
            String name,
            String outcome,
            List<HealthValue> data) {

    }

    public record HealthValue(
            String value) {

    }

    WildFlyControllerClient wildflyClient = new WildFlyControllerClient();
    @RestClient
    WildFlyMetricsClient wildflyMetricsClient;
    @RestClient
    WildFlyHealthClient wildflyHealthClient;

    @Tool()
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
            ModelNode node = wildflyClient.call(server, user, mn);
            ModelNode result = node.get("result");
            // enforce some cleanup
            result.remove("extension");
            result.remove("core-service");
            result.remove("path");
            result.remove("system-properties");
            cleanupUndefined(result);
            return buildResponse(result.toJSONString(false));
        } catch (Exception ex) {
            return handleException(ex, server, "retrieving the server configuration");
        }
    }

    @Tool(description = "Retrieve the JSON of the metamodel for the provided resource path.")
    @RolesAllowed("admin")
    ToolResponse getWildFlyMetaModel(
            @ToolArg(name = "host", required = false) String host,
            @ToolArg(name = "port", required = false) String port,
            @ToolArg(name = "resource-path", required = true) String resourcePath) {
        Server server = new Server(host, port);
        try {
            User user = new User();
            CommandContext ctx = CommandContextFactory.getInstance().newCommandContext();
            // This call, if done with the Monitor role, will be filtered. No sensitive information present.
            ModelNode mn = ctx.buildRequest(resourcePath + ":read-resource-description(recursive=false)");
            ModelNode node = wildflyClient.call(server, user, mn);
            ModelNode result = node.get("result");
            cleanupUndefined(result);
            return buildResponse(result.toJSONString(false));
        } catch (Exception ex) {
            return handleException(ex, server, "retrieving the server meta model");
        }
    }

    @Tool()
    @RolesAllowed("admin")
    ToolResponse getDeploymentFilePaths(
            @ToolArg(name = "host", required = false) String host,
            @ToolArg(name = "port", required = false) String port,
            @ToolArg(name = "name", required = false) String name) {
        Server server = new Server(host, port);
        try {
            User user = new User();
            CommandContext ctx = CommandContextFactory.getInstance().newCommandContext();
            name = (name == null || name.isEmpty()) ? "ROOT.war" : name;
            ModelNode mn = ctx.buildRequest("/deployment=" + name + ":browse-content");
            ModelNode node = wildflyClient.call(server, user, mn);
            ModelNode result = node.get("result");
            return buildResponse(result.toJSONString(false));
        } catch (Exception ex) {
            return handleException(ex, server, "browsing the deployment");
        }
    }

    @Tool()
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
            OperationResponse value = wildflyClient.callOperation(server, user, mn);
            String content = getAttachment(value);
            return buildResponse(content);
        } catch (Exception ex) {
            return handleException(ex, server, "retrieving the logging categories");
        }
    }

    String getAttachment(OperationResponse value) throws CommandLineException {
        return WildFlyControllerClient.getAttachment(value);
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

    @Tool()
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
            String value = wildflyClient.call(server, user, mn).toJSONString(false);
            return buildResponse(value);
        } catch (Exception ex) {
            return handleException(ex, server, "invoking operations ");
        }
    }

    @Tool(description = "Deploy an application to a running WildFly server. If the deployment already exists, it will be replaced.")
    @RolesAllowed("admin")
    ToolResponse deployWildFlyApplication(
            @ToolArg(name = "host", required = false) String host,
            @ToolArg(name = "port", required = false) String port,
            @ToolArg(name = "deploymentPath", description = "Path to WAR file or exploded application directory.", required = true) String deploymentPath,
            @ToolArg(name = "name", description = "Defaults to the last portion of the deploymentPath.", required = false) String name,
            @ToolArg(name = "runtime-name", description = "Defaults to the last portion of the deploymentPath.", required = false) String runtimeName) {
        Server server = new Server(host, port);
        try {
            User user = new User();
            Path path = Paths.get(deploymentPath);
            String lastPathSegment = path.getFileName().toString();
            name = (name == null || name.trim().isEmpty()) ? lastPathSegment : name;
            runtimeName = (runtimeName == null || runtimeName.trim().isEmpty()) ? lastPathSegment : runtimeName;
            ModelNode checkDeployment = wildflyClient.call(new CheckDeploymentRequest(server, user, name));
            boolean isAlreadyDeployed = "success".equals(checkDeployment.get("outcome").asString());
            ModelNode response;
            CommandContext ctx = CommandContextFactory.getInstance().newCommandContext();
            String op = ((isAlreadyDeployed
                    ? ":full-replace-deployment(name=" + name + ","
                    : "/deployment=" + name + ":add(")
                    + "content=[{input-stream-index=0}],enabled=true,runtime-name=") + runtimeName + ")";
            ModelNode mn = ctx.buildRequest(op);
            OperationResponse value = wildflyClient.callOperation(server, user, mn, deploymentPath);
            response = value.getResponseNode();
            if ("success".equals(response.get("outcome").asString())) {
                return buildResponse((isAlreadyDeployed
                        ? "Successfully replaced existing deployment: "
                        : "Successfully deployed new application: ") + name + " from path: " + deploymentPath);
            } else {
                String failureDesc = response.has("failure-description")
                        ? response.get("failure-description").asString() : "Unknown error";
                return buildErrorResponse((isAlreadyDeployed
                        ? "Failed to replace deployment: "
                        : "Failed to deploy new application: ") + failureDesc);
            }
        } catch (Exception ex) {
            return handleException(ex, server, "deploying application from " + deploymentPath);
        }
    }

    @Tool(description = "Undeploy an application deployed to a running WildFly server.")
    @RolesAllowed("admin")
    ToolResponse undeployWildFlyApplication(@ToolArg(name = "host", required = false) String host,
            @ToolArg(name = "port", required = false) String port,
            @ToolArg(name = "name", description = "Defaults to the last portion of the deploymentPath.", required = true) String name) {
        Server server = new Server(host, port);
        try {
            User user = new User();
            ModelNode response = wildflyClient.call(new UndeployRequest(server, user, name));
            if ("success".equals(response.get("outcome").asString())) {
                return buildResponse("Successfully undeployed deployment: " + name);
            } else {
                String failureDesc = response.has("failure-description")
                        ? response.get("failure-description").asString() : "Unknown error";
                return buildErrorResponse("Failed to undeploy deployment: " + failureDesc);
            }
        } catch (Exception ex) {
            return handleException(ex, server, "undeploying application " + name);
        }
    }

    @Tool(description = "Provision a WildFly server or WildFly Bootable JAR by inspecting the content of a deployment. The deployment is then deployed to the server ready to be run. The provisioning is operated using WildFly Glow.")
    ToolResponse provisionWildFlyServerForDeployment(@ToolArg(name = "deploymentPath", required = true, description = "Absolute path to an existing deployment") String deploymentPath,
            @ToolArg(name = "targetDirectory", required = true, description = "Absolute path to a non existing directory. Note that the parent directory must exists.") String targetDirectory,
            @ToolArg(name = "addOns", required = false, description = "A list of Glow AddOns to enable") String[] addOns,
            @ToolArg(name = "serverVersion", required = false,
                    description = "The WildFly server version. By default the latest is used") String wildflyVersion,
            @ToolArg(name = "spaces", required = false,
                    description = "The enabled Glow spaces used to discover incubating features.") String[] spaces,
            @ToolArg(name = "bootableJAR", required = false, defaultValue = "false", description = "Produce a WildFly bootable JAR") boolean bootableJAR) {
        Path deployment = Paths.get(deploymentPath);
        if (!Files.exists(deployment)) {
            return buildErrorResponse("The deployment path " + deploymentPath + "doesn't exist");
        }
        Path target = Paths.get(targetDirectory);
        if (Files.exists(target)) {
            return buildErrorResponse("The target directory " + targetDirectory + " already exists. Delete it first");
        }

        ScanArguments.Builder builder = Arguments.scanBuilder();
        List<Path> deployments = new ArrayList<>();
        deployments.add(deployment);
        builder.setBinaries(deployments);
        if (addOns != null) {
            Set<String> set = new HashSet<>(Arrays.asList(addOns));
            builder.setUserEnabledAddOns(set);
        }
        if (wildflyVersion != null) {
            builder.setVersion(wildflyVersion);
        }
        if (spaces != null) {
            Set<String> set = new HashSet<>(Arrays.asList(spaces));
            builder.setSpaces(set);
        }
        if (bootableJAR) {
            builder.setOutput(OutputFormat.BOOTABLE_JAR);
        } else {
            builder.setOutput(OutputFormat.SERVER);
        }
        Arguments args = builder.build();

        try {
            MavenRepoManager repoManager = MavenResolver.newMavenResolver();
            ScanResults scanResults = GlowSession.scan(repoManager, args, GlowMessageWriter.DEFAULT);
            OutputContent content = scanResults.outputConfig(target, null);
        } catch (Exception ex) {
            return buildErrorResponse(ex.getMessage());
        }
        return buildResponse("The deployment " + deploymentPath + " has been deployed in the WildFly server provisioned in " + target);
    }

    @Tool(description = "Provision a complete WildFly server installation using prospero installer. Such installation can then be updated")
    ToolResponse provisionCompleteWildFlyServer(
            @ToolArg(name = "targetDirectory", required = true, description = "Absolute path to a non existing directory. Note that the parent directory must exists.") String targetDirectory,
            @ToolArg(name = "serverVersion", required = false, description = "Server version. If no version is provided, the latest version is used.") String serverVersion) {
        try {
            String manifest = "org.wildfly.channels:wildfly";
            manifest += serverVersion == null ? "" : ":" + serverVersion;
                ProvisioningDefinition definition = ProvisioningDefinition.builder()
                    .setProfile("wildfly").setManifest(manifest).build();
            LocalRepoOptions localRepoOptions = new LocalRepoOptions();
            MavenOptions.Builder mavenOptionsBuilder = localRepoOptions.toOptions();
            MavenOptions mavenOptions = mavenOptionsBuilder.build();
            final GalleonProvisioningConfig provisioningConfig = definition.toProvisioningConfig();
            final List<Channel> channels = ChannelUtils.resolveChannels(definition, mavenOptions);
            ActionFactory factory = new ActionFactory();
            ProvisioningAction action = factory.install(Paths.get(targetDirectory), mavenOptions, new MCPConsole());
            action.provision(provisioningConfig, channels);
            return buildResponse("Server installed in " + targetDirectory);
        } catch (Exception ex) {
            return buildErrorResponse(ex.getMessage());
        }
    }

    // TODO, when we have a published channel manifest with all supported feature-packs, allow to provide feature-packs.
    @Tool(description = "Provision a trimmed WildFly server using Galleon layers. Such installation can then be updated")
    ToolResponse provisionTrimmedWildFlyServer(
            @ToolArg(name = "targetDirectory", required = true, description = "Absolute path to a non existing directory. Note that the parent directory must exists.") String targetDirectory,
            @ToolArg(name = "serverVersion", required = false, description = "Server version. If no version is provided, the latest version is used.") String serverVersion,
            @ToolArg(name = "layers", required = true,
                    description = "List of layer names") String[] layers) {
        Path tmp = null;
        try {
            tmp = Files.createTempFile("provisioning-from-wildfly-mcp-server", null);
            StringBuilder layersBuilder = new StringBuilder();
            for(String name : layers) {
                layersBuilder.append(LAYER_TEMPLATE.replace("###LAYER###", name) + "\n");
            }
            StringBuilder fpsBuilder = new StringBuilder();
            List<String> featurePacks = new ArrayList<>();
            featurePacks.add("org.wildfly:wildfly-galleon-pack:");
            for(String name : featurePacks) {
                fpsBuilder.append(FEATURE_PACK_TEMPLATE.replace("###LOCATION###", name) + "\n");
            }
            String provisioningXml = PROVISIONING_XML_TEMPLATE.replace("###FEATURE_PACKS###", fpsBuilder.toString());
            provisioningXml = provisioningXml.replace("###LAYERS###", layersBuilder.toString());
            Files.write(tmp, provisioningXml.getBytes());
            // Create a channel
            List<String> remoteRepositories = new ArrayList<>();
            remoteRepositories.add("https://repository.jboss.org/nexus/content/groups/public/");
            remoteRepositories.add("https://repo1.maven.org/maven2/");
            String manifest = "org.wildfly.channels:wildfly";
            manifest += serverVersion == null ? "" : ":" + serverVersion;
            ProvisioningDefinition definition = ProvisioningDefinition.builder()
                    .setDefinitionFile(tmp.toUri())
                    .setOverrideRepositories(RepositoryDefinition.from(remoteRepositories))
                    .setManifest(manifest).build();
            LocalRepoOptions localRepoOptions = new LocalRepoOptions();
            MavenOptions.Builder mavenOptionsBuilder = localRepoOptions.toOptions();
            MavenOptions mavenOptions = mavenOptionsBuilder.build();
            final GalleonProvisioningConfig provisioningConfig = definition.toProvisioningConfig();
            final List<Channel> channels = ChannelUtils.resolveChannels(definition, mavenOptions);
            ActionFactory factory = new ActionFactory();
            ProvisioningAction action = factory.install(Paths.get(targetDirectory), mavenOptions, new MCPConsole());
            action.provision(provisioningConfig, channels);
            return buildResponse("Server installed in " + targetDirectory);
        } catch (Exception ex) {
            return buildErrorResponse(ex.getMessage());
        } finally {
            if(tmp != null) {
                try {
                    Files.delete(tmp);
                } catch(Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    @Tool(description = "Update a WildFly server installation that has been provisioned using prospero installer.")
    ToolResponse updateWildFlyServer(
            @ToolArg(name = "targetDirectory", required = true, description = "Absolute path to a non existing directory. Note that the parent directory must exists.") String targetDirectory) {        try {
            ProvisioningDefinition definition = ProvisioningDefinition.builder()
                    .setProfile("wildfly").build();
            LocalRepoOptions localRepoOptions = new LocalRepoOptions();
            MavenOptions.Builder mavenOptionsBuilder = localRepoOptions.toOptions();
            MavenOptions mavenOptions = mavenOptionsBuilder.build();
            ActionFactory factory = new ActionFactory();
            UpdateAction action = factory.update(Paths.get(targetDirectory), mavenOptions, new MCPConsole(), Collections.emptyList());
            UpdateSet set = action.findUpdates();
            if(set.isEmpty()) {
                return buildResponse("Server installion is already up to date.");
            } else {
                action.performUpdate();
                StringBuilder builder = new StringBuilder();
                for(ArtifactChange ac : set.getArtifactUpdates()) {
                    builder.append(ac.getArtifactName() + " updated from " + ac.getOldVersion().get() + " to " + ac.getNewVersion().get() + "\n");
                }
                return buildResponse("Server installion has been updated. Updates \n" + builder);
            }
        } catch (Exception ex) {
            return buildErrorResponse(ex.getMessage());
        }
    }

    @Tool(description = "Check for updates for an existing WildFly server installation. Such server must have been provisioned previously using prospero installer.")
    ToolResponse checkForUpdatesWildFlyServer(
            @ToolArg(name = "targetDirectory", required = true, description = "Absolute path to a non existing directory. Note that the parent directory must exists.") String targetDirectory) {        try {
            ProvisioningDefinition definition = ProvisioningDefinition.builder()
                    .setProfile("wildfly").build();
            LocalRepoOptions localRepoOptions = new LocalRepoOptions();
            MavenOptions.Builder mavenOptionsBuilder = localRepoOptions.toOptions();
            MavenOptions mavenOptions = mavenOptionsBuilder.build();
            ActionFactory factory = new ActionFactory();
            UpdateAction action = factory.update(Paths.get(targetDirectory), mavenOptions, new MCPConsole(), Collections.emptyList());
            UpdateSet set = action.findUpdates();
            if(set.isEmpty()) {
                return buildResponse("Server installion is already up to date.");
            } else {
                StringBuilder builder = new StringBuilder();
                for(ArtifactChange ac : set.getArtifactUpdates()) {
                    builder.append(ac.getArtifactName() + " updated from " + ac.getOldVersion().get() + " to " + ac.getNewVersion().get() + "\n");
                }
                return buildResponse("Server installion has some updates: Updates \n" + builder);
            }
        } catch (Exception ex) {
            return buildErrorResponse(ex.getMessage());
        }
    }

    @Tool(description = "Stop a running server")
    @RolesAllowed("admin")
    ToolResponse shutdownServer(@ToolArg(name = "host", required = false) String host,
            @ToolArg(name = "port", required = false) String port,
            @ToolArg(name = "timeout", required = false, description = "Graceful shutdown timeout") Integer timeout) {
        Server server = new Server(host, port);
        try {
            User user = new User();
            ModelNode response = wildflyClient.call(new ShutdownRequest(server, user, timeout));
            if ("success".equals(response.get("outcome").asString())) {
                return buildResponse("Successfull shutdown of the server");
            } else {
                String failureDesc = response.has("failure-description")
                        ? response.get("failure-description").asString() : "Unknown error";
                return buildErrorResponse("Failed to shutdown the server: " + failureDesc);
            }
        } catch (Exception ex) {
            return handleException(ex, server, "shutdown of the server");
        }
    }

    @Tool(description = "List the WildFly add-ons (extra server features such as add-user.sh, jboss-cli.sh command lines, openAPI and more) "
            + "that could be added when provisioning a WildFly server for the provided deployment.")
    ToolResponse getWildFlyProvisioningAddOns(
            @ToolArg(name = "deploymentPath", required = true, description = "Absolute path to an existing deployment") String deploymentPath) {
        try {
            ScanArguments.Builder builder = Arguments.scanBuilder();
            List<Path> binaries = new ArrayList<>();
            Path dep = Paths.get(deploymentPath);
            if (!Files.exists(dep)) {
                throw new Exception("The file doesn't exist. It must be an absolute path to a deployment file.");
            }
            binaries.add(dep);
            builder.setBinaries(binaries);
            builder.setOutput(OutputFormat.PROVISIONING_XML);
            builder.setSuggest(true);
            MavenRepoManager repoManager = MavenResolver.newMavenResolver();
            ScanResults scanResults = GlowSession.scan(repoManager, builder.build(), GlowMessageWriter.DEFAULT);
            StringBuilder strBuilder = new StringBuilder();
            strBuilder.append("<add-ons>\n");
            Iterator<AddOn> it = scanResults.getSuggestions().getPossibleAddOns().iterator();
            while (it.hasNext()) {
                AddOn addOn = it.next();
                strBuilder.append("<!-- " + addOn.getDescription() + " -->\n");
                strBuilder.append("<add-on>" + addOn.getName() + "</add-on>\n");
            }
            strBuilder.append("</add-ons>");
            return buildResponse(strBuilder.toString());
        } catch (Exception ex) {
            return buildErrorResponse(ex.getMessage());
        }
    }

    @Tool(description = "Get WildFly documentation URL")
    @RolesAllowed("admin")
    ToolResponse getWildFlyDocumentationURL() {
        return buildResponse("https://docs.wildfly.org/");
    }

    @Tool(description = "List all the logging categories one can enable in the WildFly server")
    @RolesAllowed("admin")
    ToolResponse listAvailableLoggingCategories(
            @ToolArg(name = "host", required = false) String host,
            @ToolArg(name = "port", required = false) String port) {
        Server server = new Server(host, port);
        User user = new User();
        try {
            ModelNode response = wildflyClient.call(new GetLoggingMXBean(server, user));
            ModelNode result = response.get("result");
            if (result.isDefined()) {
                return buildResponse(result.toJSONString(false));
            } else {
                return buildResponse("This server version doesn't expose the list of available logging categories. Use a more recent WildFly server");
            }
        } catch (Exception ex) {
            return handleException(ex, server, "retrieving the logging categories");
        }
    }

    @Tool()
    @RolesAllowed("admin")
    ToolResponse enableWildFlyLoggingCategory(
            @ToolArg(name = "host", required = false) String host,
            @ToolArg(name = "port", required = false) String port,
            String loggingCategory) {
        Server server = new Server(host, port);
        try {
            User user = new User();
            String category = findCategory(loggingCategory);
            ModelNode response = wildflyClient.call(new GetLoggersRequest(server, user));
            if (response.get("result") != null) {
                boolean found = false;
                for (ModelNode cat : response.get("result").asList()) {
                    if (cat.asString().equals(category)) {
                        found = true;
                    }
                }
                if (found) {
                    wildflyClient.call(new EnableLoggerRequest(server, user, category));
                } else {
                    wildflyClient.call(new AddLoggerRequest(server, user, category));
                }
            } else {
                wildflyClient.call(new AddLoggerRequest(server, user, category));
            }
            return buildResponse("The logging category " + loggingCategory + " has been enabled by using the " + category + " logger");
        } catch (Exception ex) {
            return handleException(ex, server, "enabling the logger " + loggingCategory);
        }
    }

    @Tool()
    @RolesAllowed("admin")
    ToolResponse removeWildFlyLoggingCategory(
            @ToolArg(name = "host", required = false) String host,
            @ToolArg(name = "port", required = false) String port,
            String loggingCategory) {
        Server server = new Server(host, port);
        try {
            User user = new User();
            String category = findCategory(loggingCategory);
            ModelNode response = wildflyClient.call(new GetLoggersRequest(server, user));
            if (response.get("result") != null) {
                boolean found = false;
                for (ModelNode cat : response.get("result").asList()) {
                    if (cat.asString().equals(category)) {
                        found = true;
                    }
                }
                if (!found) {
                    return buildErrorResponse("The logging category " + loggingCategory + " is not already enabled, you should first enabled it.");
                }
            }
            wildflyClient.call(new RemoveLoggerRequest(server, user, category));
            return buildResponse("The logging category " + loggingCategory + " has been removed by using the " + category + " logger.");
        } catch (Exception ex) {
            return handleException(ex, server, "disabling the logger " + loggingCategory);
        }
    }

    @Tool()
    @RolesAllowed("admin")
    ToolResponse getWildFlyLogFileContent(
            @ToolArg(name = "host", required = false) String host,
            @ToolArg(name = "port", required = false) String port,
            @ToolArg(name = "numberOfLines", description = "200 by default, use `-1` for all lines.", required = false) String numLines,
            @ToolArg(name = "onlyForLastServerStart", description = "True by default.", required = false) Boolean lastStart) {
        Server server = new Server(host, port);
        try {
            User user = new User();
            ModelNode response = wildflyClient.call(new GetLoggingFileRequest(server, numLines, user));
            StringBuilder builder = new StringBuilder();
            List<String> lst = new ArrayList<>();
            lastStart = lastStart == null ? Boolean.TRUE : lastStart;
            if (lastStart && numLines == null) {
                for (ModelNode line : response.get("result").asList()) {
                    if (line.asString().contains("WFLYSRV0049")) {
                        lst = new ArrayList<>();
                    }
                    lst.add(line.asString());
                }
                for (String line : lst) {
                    builder.append(line).append("\n");
                }
            } else {
                for (ModelNode line : response.get("result").asList()) {
                    builder.append(line.asString()).append("\n");
                }
            }
            return buildResponse("WildFly server log file Content: `" + builder.toString() + "`");
        } catch (Exception ex) {
            return handleException(ex, server, "retrieving the log file ");
        }
    }

    @Tool()
    @RolesAllowed("admin")
    ToolResponse getWildFlyServerAndJVMInfo(
            @ToolArg(name = "host", required = false) String host,
            @ToolArg(name = "port", required = false) String port) {
        Server server = new Server(host, port);
        User user = new User();
        try {
            VMInfo info = new VMInfo();
            ModelNode response = wildflyClient.call(new GetRuntimeMXBean(server, user));
            ModelNode result = response.get("result");
            info.name = result.get("name").asString();
            for (ModelNode a : result.get("input-arguments").asList()) {
                info.inputArguments.add(a.asString());
            }
            info.specName = result.get("spec-name").asString();
            info.specVendor = result.get("spec-vendor").asString();
            info.specVersion = result.get("spec-version").asString();
            info.startTime = new Date(result.get("start-time").asLong()).toString();
            info.upTime = (result.get("uptime").asLong() / 1000) + "seconds";
            info.vmName = result.get("vm-name").asString();
            info.vmVendor = result.get("vm-vendor").asString();
            info.vmVersion = result.get("vm-version").asString();

            response = wildflyClient.call(new GetOperatingSystemMXBean(server, user));
            result = response.get("result");
            if (result.has("process-cpu-load")) {
                double val = result.get("process-cpu-load").asLong();
                info.consumedCPU = "" + (int) val + "%";
            } else {
                info.consumedCPU = "not available";
            }
            response = wildflyClient.call(new GetOperatingSystemMXBean(server, user));
            result = response.get("result");
            double val = result.get("system-load-average").asLong();
            info.systemLoadAverage = "" + (int) val + "%";

            response = wildflyClient.call(new GetMemoryMXBean(server, user));
            result = response.get("result");
            double max = result.get("heap-memory-usage").get("max").asLong();
            double used = result.get("heap-memory-usage").get("used").asLong();
            double res = (used * 100) / max;
            info.consumedMemory = "" + (int) res + "%";
            ServerInfo serverInfo = new ServerInfo();
            serverInfo.vmInfo = info;
            serverInfo = getServerInfo(server, user, serverInfo);
            return buildResponse(toJson(serverInfo));

        } catch (Exception ex) {
            return handleException(ex, server, "retrieving the consumed memory");
        }
    }

    ServerInfo getServerInfo(Server server, User user, ServerInfo serverInfo) throws Exception {
        CommandContext ctx = CommandContextFactory.getInstance().newCommandContext();
        ModelNode mn = ctx.buildRequest(":read-resource(recursive=false)");
        ModelNode node = wildflyClient.call(server, user, mn);
        ModelNode res2 = node.get("result");
        serverInfo.nodeName = res2.get("name").asString();
        serverInfo.productName = res2.get("product-name").asString();
        serverInfo.productVersion = res2.get("product-version").asString();
        serverInfo.coreVersion = res2.get("release-version").asString();
        return serverInfo;
    }

    @Tool()
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

    @Tool()
    ToolResponse getWildFlyServerAndDeploymentsStatus(
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
                    WildFlyDMRStatus status = wildflyClient.getStatus(server, user);
                    List<String> ret = new ArrayList<>();
                    ret.addAll(status.getStatus());
                    return buildResponse(ret.toArray(String[]::new));
                } else {
                    if (ex.getResponse().getStatus() == 503) {
                        String e = ex.getResponse().readEntity(String.class);
                        return buildResponse(e);
                    } else {
                        throw ex;
                    }
                }
            }
        } catch (Exception ex) {
            return handleException(ex, server, "retrieving the health");
        }
    }

    @Prompt(name = "wildfly-server-prometheus-metrics-chart", description = "WildFly, prometheus metrics chart")
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

    @Prompt(name = "wildfly-server-security-audit", description = "WildFly, security audit. Analyze the server log file for potential attacks")
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

    @Prompt(name = "wildfly-server-resources-consumption", description = "WildFly and JVM resource consumption status. Analyze the consumed resources.")
    PromptMessage consumedResources(@PromptArg(name = "host",
            description = "Optional WildFly server host name. By default localhost is used.",
            required = false) String host,
            @PromptArg(name = "port",
                    description = "Optional WildFly server port. By default 9990 is used.",
                    required = false) String port) {
        Server server = new Server(host, port);
        return PromptMessage.withUserRole(new TextContent("Check the consumed resources of the JVM and the Wildfly server running on host " + server.host + ", port " + server.port + ": consumed memory, max memory and cpu utilization, prometheus metrics. "
                + "Your reply should be short with a strong focus on what is wrong and your recommendations."));
    }

    @Prompt(name = "wildfly-server-metrics-analyzer", description = "WildFly and JVM metrics. Analyze and summarize the metrics.")
    PromptMessage analyzeMetrics(@PromptArg(name = "host",
            description = "Optional WildFly server host name. By default localhost is used.",
            required = false) String host,
            @PromptArg(name = "port",
                    description = "Optional WildFly server port. By default 9990 is used.",
                    required = false) String port) {
        Server server = new Server(host, port);
        return PromptMessage.withUserRole(new TextContent("Retrieve the metrics of the Wildfly server running on host " + server.host + ", port " + server.port + ". "
                + "Analyze the metrics then provide a summary that should highlights potential reached limits. Make sure to not deep dive into the details and provide a compact summary."));
    }

    @Prompt(name = "wildfly-server-memory-consumption-over-time", description = "WildFly, memory consumption over time")
    PromptMessage memOverTimeChart(@PromptArg(name = "host",
            description = "Optional WildFly server host name. By default localhost is used.",
            required = false) String host,
            @PromptArg(name = "port",
                    description = "Optional WildFly server port. By default 9990 is used.",
                    required = false) String port) {
        Server server = new Server(host, port);
        return PromptMessage.withUserRole(new TextContent("Get the JVM memory consumption from the Wildfly server running on host " + server.host + ", port " + server.port
                + ". You will repeat the invocation 3 times, being sure to wait 5 seconds between each invocation. "
                + "After all the 3 invocation have been completed you will organize the data in a table. "
                + "Then you will use this table to create a graph to visually compare the data. "
                + "Use the time in X axis, and mem consumption in Y axis"));
    }

    @Prompt(name = "wildfly-deployment-errors", description = "WildFly deployed applications, identify potential errors.")
    PromptMessage deploymentError(@PromptArg(name = "host",
            description = "Optional WildFly server host name. By default localhost is used.",
            required = false) String host,
            @PromptArg(name = "port",
                    description = "Optional WildFly server port. By default 9990 is used.",
                    required = false) String port,
            @PromptArg(name = "deploymentName",
                    description = "The deployment name.",
                    required = false) String deploymentName) {
        Server server = new Server(host, port);
        if (deploymentName == null) {
            deploymentName = "deployments";
        } else {
            deploymentName = "the deployed application " + deploymentName;
        }
        return PromptMessage.withUserRole(new TextContent("Check that the status of " + deploymentName + " in the Wildfly server running on host " + server.host + ", port " + server.port + " is OK. "
                + "Retrieve the lines of the server log of the last time the server started. Then check that no errors are found in the traces older than the last time the server was starting."
                + "If you find errors, and if the files exist, access the web.xml and jboss-web.xml files of the " + deploymentName + " and check for faulty content that could explain the error seen in the log file."));
    }

    @Prompt(name = "wildfly-server-log-errors", description = "WildFly server, identify errors.")
    PromptMessage serverLogErrors(@PromptArg(name = "host",
            description = "Optional WildFly server host name. By default localhost is used.",
            required = false) String host,
            @PromptArg(name = "port",
                    description = "Optional WildFly server port. By default 9990 is used.",
                    required = false) String port) {
        Server server = new Server(host, port);
        return PromptMessage.withUserRole(new TextContent("Retrieve the server log of the Wildfly server running on host " + server.host + ", port " + server.port + ". "
                + "If you see lines containing ERROR, analyze the error and report the findings. If you don't see lines with ERROR, reply that the log file doesn't contain any errors."));
    }

    @Prompt(name = "wildfly-server-log-analyzer", description = "WildFly server, analyze the log file.")
    PromptMessage serverAnalyzeLogFile(@PromptArg(name = "host",
            description = "Optional WildFly server host name. By default localhost is used.",
            required = false) String host,
            @PromptArg(name = "port",
                    description = "Optional WildFly server port. By default 9990 is used.",
                    required = false) String port) {
        Server server = new Server(host, port);
        return PromptMessage.withUserRole(new TextContent("Retrieve the server log of the Wildfly server running on host " + server.host + ", port " + server.port + ". "
                + "If you see lines containing ERROR or WARN, analyze the error and report the findings. If you don't see lines with ERROR nor WARN, provide a short summary of what the traces contain."));
    }

    @Prompt(name = "wildfly-server-status", description = "WildFly server, running status.")
    PromptMessage serverBootErrors(@PromptArg(name = "host",
            description = "Optional WildFly server host name. By default localhost is used.",
            required = false) String host,
            @PromptArg(name = "port",
                    description = "Optional WildFly server port. By default 9990 is used.",
                    required = false) String port) {
        Server server = new Server(host, port);
        return PromptMessage.withUserRole(new TextContent("Check that the server and deployments running on host " + server.host + ", port " + server.port
                + " status is ok. Then retrieve the last 100 lines of the server log, then check that the trace WFLYSRV0025 is found in the server log traces of the last time the server started. In addition your reply must contain the WildFly and JVM versions."));
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
