/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp.dev;

import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.TextContent;
import java.util.List;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jboss.galleon.api.config.GalleonConfigurationWithLayers;
import org.jboss.galleon.api.config.GalleonFeaturePackConfig;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.wildfly.glow.AddOn;
import org.wildfly.glow.Arguments;
import org.wildfly.glow.GlowMessageWriter;
import org.wildfly.glow.GlowSession;
import org.wildfly.glow.Layer;
import org.wildfly.glow.OutputFormat;
import org.wildfly.glow.ScanArguments;
import org.wildfly.glow.ScanResults;
import org.wildfly.glow.maven.MavenResolver;

public class WildFlyDevMCPServer {

    static final Logger LOGGER = Logger.getLogger("org.wildfly.mcp.dev.WildFlyDevMCPServer");

    // TODO: Can we retrieve the latest Maven plugin version? Not sure...For now use prompting.
    @Tool(description = "Get the WildFly Maven Plugin initial configuration used to provision a WildFly server and deploy the application.")
    ToolResponse getWildFlyMavenPluginInitialConfiguration() {
        String plugin = """
                             <plugin>
                                <groupId>org.wildfly.plugins</groupId>
                                <artifactId>wildfly-maven-plugin</artifactId>
                                <!-- use the latest version-->
                                <version>USE_LATEST_VERSION</version>
                                <configuration>
                                    <!-- Always re-create the server -->
                                    <overwrite-provisioned-server>true</overwrite-provisioned-server>
                                    <!-- This element makes the plugin to automatically discover the Galleon information required to provision the server -->
                                    <discover-provisioning-info>
                                    </discover-provisioning-info>
                                </configuration>
                                <executions>
                                    <execution>
                                        <goals>
                                            <!-- the package goal provision the server and deploy the application into it -->
                                            <goal>package</goal>
                                        </goals>
                                    </execution>
                                </executions>
                            </plugin>
                            """;
        return buildResponse(plugin);
    }

//    @Tool(description = "Get the WildFly feature-packs and Galleon layers required to provision a server to run this built application")
//    ToolResponse getProvisioningInfoForFile(
//            @ToolArg(name = "built-application-deployment-absolute-file-path", required = true) String filePath) {
//        try {
//            ScanArguments.Builder builder = Arguments.scanBuilder();
//            List<Path> binaries = new ArrayList<>();
//            Path fp = Paths.get(filePath);
//            if (!Files.exists(fp)) {
//                throw new Exception("The file doesn't exist. It must be an absolute path to a deployment file.");
//            }
//            String fileName = fp.getFileName().toString().toLowerCase();
//            System.err.println(fileName);
//            if (!fileName.endsWith(".war") && !fileName.endsWith(".jar") && !fileName.endsWith(".ear")) {
//                throw new Exception("The file must be a binary jar, war or ear file.");
//            }
//            binaries.add(Paths.get(filePath));
//            builder.setBinaries(binaries);
//            builder.setOutput(OutputFormat.PROVISIONING_XML);
//            MavenRepoManager repoManager = MavenResolver.newMavenResolver();
//            ScanResults scanResults = GlowSession.scan(repoManager, builder.build(), GlowMessageWriter.DEFAULT);
//            Path glowOutputFolder = Files.createTempDirectory("wildfly-dev-mcp");
//            scanResults.outputConfig(glowOutputFolder, null);
//            GalleonProvisioningConfig config = scanResults.getProvisioningConfig();
//            GalleonProvisioningConfig.Builder configBuilder = GalleonProvisioningConfig.builder(config);
//            StringBuilder strBuilder = new StringBuilder();
//            strBuilder.append("{ \"feature-packs\":[\n");
//            for (GalleonFeaturePackConfig fpc : config.getFeaturePackDeps()) {
//                strBuilder.append("{ \"location\": \"" + fpc.getLocation() + "\" },");
//            }
//            strBuilder.append("],\n");
//            strBuilder.append("{ \"layers\":[\n");
//            GalleonConfigurationWithLayers c = config.getDefinedConfigs().iterator().next();
//            for (String l : c.getIncludedLayers()) {
//                strBuilder.append("\"" + l + "\",");
//            }
//            strBuilder.append("],\n}\n");
//            //config = configBuilder.build();
//            //Path prov = glowOutputFolder.resolve("provisioning.xml");
//            //scanResults.getProvisioning().storeProvisioningConfig(config, prov);
//            return buildResponse(strBuilder.toString());
//        } catch (Exception ex) {
//            return handleException(ex, "");
//        }
//    }
    @Tool(description = "The maven command to start the server. Note that you must first have packaged the server prior to start it.")
    ToolResponse getMavenCommandToStartTheServer() {
        return buildResponse("mvn wildfly:start");
    }
    @Tool(description = "The configuration to add to the WildFly Maven plugin to execute WildFLy CLI operations when provisioning the server.")
    ToolResponse getWildFlyMavenPluginCLIOperationsConfiguration() {
        String config = """
                        <packaging-scripts>
                            <packaging-script>
                                <commands>
                                <!-- add your CLI operations here -->
                                </commands>
                                <!-- Expressions resolved during server execution -->
                                <resolve-expressions>false</resolve-expressions>
                            </packaging-script>
                        </packaging-scripts>
                        """;
        return buildResponse(config);
    }
    @Tool(description = "The maven command to stop the server. Note that you must first have started the server prior to stop it.")
    ToolResponse getMavenCommandToStopTheServer() {
        return buildResponse("mvn wildfly:shutdown");
    }
    @Tool(description = "The specific configuration that could be added to the WildFly maven plugin <discover-provisioning-info> configuration when the application is to be deployed to the cloud (openshift, kubernetes, ...)")
    ToolResponse getCloudSpecificConfiguration() {
        return buildResponse("<context>cloud</context>");
    }
    @Tool(description = "The specific configuration that could be added to the WildFly maven plugin <discover-provisioning-info> configuration when the application is to benefit from WildFly High Availability features.")
    ToolResponse getHASpecificConfiguration() {
        return buildResponse("<profile>ha</profile>");
    }
    @Tool(description = "The specific configuration that could be added to the WildFly maven plugin <discover-provisioning-info> configuration when the application is to benefit from WildFly High Availability features.")
    ToolResponse getWildFlyVersionSpecificConfiguration(@ToolArg(name = "wildfly-version", required = true) String wildflyVersion) {
        return buildResponse("<server>"+wildflyVersion+"</version>");
    }
    @Tool(description = "Provide the WildFly add-ons (extra server features) that could be added to the WildFly maven plugin <discover-provisioning-info> configuration. NB: The application must have been first built.")
    ToolResponse getAddOns(
            @ToolArg(name = "application-deployment-absolute-file-path", required = true) String filePath) {
        try {
            ScanArguments.Builder builder = Arguments.scanBuilder();
            List<Path> binaries = new ArrayList<>();
            Path fp = Paths.get(filePath);
            if (!Files.exists(fp)) {
                throw new Exception("The file doesn't exist. It must be an absolute path to a deployment file.");
            }
            binaries.add(Paths.get(filePath));
            builder.setBinaries(binaries);
            builder.setOutput(OutputFormat.PROVISIONING_XML);
            builder.setSuggest(true);
            MavenRepoManager repoManager = MavenResolver.newMavenResolver();
            ScanResults scanResults = GlowSession.scan(repoManager, builder.build(), GlowMessageWriter.DEFAULT);
            Path glowOutputFolder = Files.createTempDirectory("wildfly-dev-mcp");
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
            return handleException(ex, "");
        }
    }
    
//     @Tool(description = "Provide suggestions on what are the extra features that could be enabled in the WildFly server for this deployment")
//    ToolResponse getSuggestion(
//            @ToolArg(name = "application-deployment-absolute-file-path", required = true) String filePath) {
//        try {
//            ScanArguments.Builder builder = Arguments.scanBuilder();
//            List<Path> binaries = new ArrayList<>();
//            Path fp = Paths.get(filePath);
//            if (!Files.exists(fp)) {
//                throw new Exception("The file doesn't exist. It must be an absolute path to a deployment file.");
//            }
//            binaries.add(Paths.get(filePath));
//            builder.setBinaries(binaries);
//            builder.setOutput(OutputFormat.PROVISIONING_XML);
//            builder.setSuggest(true);
//            MavenRepoManager repoManager = MavenResolver.newMavenResolver();
//            ScanResults scanResults = GlowSession.scan(repoManager, builder.build(), GlowMessageWriter.DEFAULT);
//            Path glowOutputFolder = Files.createTempDirectory("wildfly-dev-mcp");
//            StringBuilder strBuilder = new StringBuilder();
//            strBuilder.append("{ \"additional-layers\":[\n");
//            Iterator<AddOn> it = scanResults.getSuggestions().getPossibleAddOns().iterator();
//            Map<String, String> layers = new TreeMap<>();
//            while (it.hasNext()) {
//                AddOn addOn = it.next();
//                System.err.println("ADD-on:" + addOn.getName() + " layers " + addOn.getLayers());
//                for (Layer l : addOn.getLayers()) {
//                    layers.put(l.getName(), addOn.getDescription());
//                }
//            }
//            Iterator<String> layerNames = layers.keySet().iterator();
//            while (layerNames.hasNext()) {
//                String layer = layerNames.next();
//                strBuilder.append("{ \"layer\": \"" + layer + "\", \n \"description\": \"" + layers.get(layer) + "\" }\n");
//                if (layerNames.hasNext()) {
//                    strBuilder.append(",\n");
//                }
//            }
//            strBuilder.append("]\n}");
//            return buildResponse(strBuilder.toString());
//        } catch (Exception ex) {
//            return handleException(ex, "");
//        }
//    }

    @Prompt(name = "wildfly-server-memory-consumption-over-time", description = "WildFly, memory consumption over time")
    PromptMessage memOverTimeChart(@PromptArg(name = "host",
            description = "Optional WildFly server host name. By default localhost is used.",
            required = false) String host,
            @PromptArg(name = "port",
                    description = "Optional WildFly server port. By default 9990 is used.",
                    required = false) String port) {
        return PromptMessage.withUserRole(new TextContent(""));
    }

    private ToolResponse handleException(Exception ex, String action) {
        LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
        return buildErrorResponse(ex.getMessage());

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
