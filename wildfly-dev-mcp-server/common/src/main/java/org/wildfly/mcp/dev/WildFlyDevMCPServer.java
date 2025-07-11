/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp.dev;

import io.quarkiverse.mcp.server.TextContent;
import java.util.List;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.wildfly.glow.AddOn;
import org.wildfly.glow.Arguments;
import org.wildfly.glow.Env;
import org.wildfly.glow.GlowMessageWriter;
import org.wildfly.glow.GlowSession;
import org.wildfly.glow.Layer;
import org.wildfly.glow.OutputFormat;
import org.wildfly.glow.ScanArguments;
import org.wildfly.glow.ScanResults;
import org.wildfly.glow.maven.MavenResolver;

public class WildFlyDevMCPServer {

    static final Logger LOGGER = Logger.getLogger("org.wildfly.mcp.dev.WildFlyDevMCPServer");

    @Tool(description = "Call this tool when a wildfly-maven-plugin is already configured in the plugins of the pom.xml file. "
            + "Follow the steps to replace the feature-packs and layers with automatic dicovery of feature-packs and layers")
    ToolResponse refactorExistingWildFlyMavenPluginConfigurationForDiscoveryofProvisioningInformation() {
        String steps = """
                       1- if the <discover-provisioning-info> is already present in the WildFly Maven plugin configuration, do not follow the next steps. The plugin is already properly configured.
                       2- Remove <feature-packs> element and its children elements.
                       3- Remove <layers> element and its children elements.
                       4- Remove <excluded-layers> element and its children element.
                       5- Add <discover-provisioning-info></discover-provisioning-info> XML element
                       """;
        return buildResponse(steps);
    }

    @Tool(description = "Get the latest version of WildFly.Using the latest version of WildFly is a good development pattern.")
    ToolResponse getWildFlyLatestVersion() {
        try {
            return buildResponse(getLatestWildFlyServerRelease());
        } catch (Exception ex) {
            return handleException(ex, "");
        }
    }

    @Tool(description = "Get the WildFly BOMs (Build of Material). Add them if they don't already exist. "
            + "If some managed dependencies with groupId org.wildfly.bom exist, replace them. "
            + "This BOM must be used to resolve dependencies required to use Jakarta EE 11 API. ")
    ToolResponse getWildFlyMavenBOM(@ToolArg(name = "wildfly-version", required = false) String version) {
        try {
            String bom = """
                         <dependency>
                              <groupId>org.wildfly.bom</groupId>
                              <artifactId>wildfly-ee-with-tools</artifactId>
                              <version>USE_LATEST_VERSION</version>
                              <type>pom</type>
                              <scope>import</scope>
                         </dependency>
                         <dependency>
                             <groupId>org.wildfly.bom</groupId>
                             <artifactId>wildfly-expansion</artifactId>
                             <version>USE_LATEST_VERSION</version>
                             <type>pom</type>
                             <scope>import</scope>
                         </dependency>
                         
                         """;
            bom = bom.replaceAll("USE_LATEST_VERSION", version == null || version.isEmpty() ? getLatestWildFlyServerRelease() : version);
            return buildResponse(bom);
        } catch (Exception ex) {
            return handleException(ex, "");
        }
    }

    @Tool(description = "List all the maven dependencies contained in the WildFly Maven BOM. Only a subset will be needed to use Jakarta EE API.")
    ToolResponse listAllDependenciesContainedInWildFlyBOMs(@ToolArg(name = "wildfly-version", required = false) String version) {
        try {
            String vers = version == null || version.isEmpty() ? getLatestWildFlyServerRelease() : version;
            String eeDeps = getDependencies("https://repo1.maven.org/maven2/org/wildfly/bom/wildfly-ee/" + vers + "/wildfly-ee-" + vers + ".pom");
            String deps = getDependencies("https://repo1.maven.org/maven2/org/wildfly/bom/wildfly-expansion/" + vers + "/wildfly-expansion-" + vers + ".pom");
            return buildResponse(eeDeps + deps);
        } catch (Exception ex) {
            return handleException(ex, "");
        }
    }

    private static String getDependencies(String bomURL) throws Exception {
        URL url = new URL(bomURL);
        URLConnection conn = url.openConnection();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(conn.getInputStream());
        NodeList nl = doc.getElementsByTagName("dependency");
        StringBuilder xmlBuilder = new StringBuilder();
        for (int i = 0; i < nl.getLength(); i++) {
            xmlBuilder.append("<dependency>\n");
            Element n = (Element) nl.item(i);
            xmlBuilder.append("<groupId>" + n.getElementsByTagName("groupId").item(0).getTextContent() + "</groupId>\n");
            xmlBuilder.append("<artifactId>" + n.getElementsByTagName("artifactId").item(0).getTextContent() + "</artifactId>\n");
            xmlBuilder.append("</dependency>\n");
        }
        return xmlBuilder.toString();
    }

    @Tool(description = "Do not call this tool if wildfly-maven-plugin is already configured in the plugins of the pom.xml file."
            + "Get the WildFly Maven Plugin configured to automatically "
            + "provision a WildFly server and automatically deploy the application when mvn package command is called.")
    ToolResponse getWildFlyMavenPlugin() {
        try {
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
                                            <!-- Maven goal to provision the server and deploy the application into it. -->
                                            <goal>package</goal>
                                        </goals>
                                    </execution>
                                </executions>
                            </plugin>
                            """;
            plugin = plugin.replace("USE_LATEST_VERSION", getLatestWildFlyMavenPluginRelease());
            return buildResponse(plugin);
        } catch (Exception ex) {
            return handleException(ex, "");
        }
    }

    @Tool(description = "The maven command to start the server. Warning: you must first use the WildFly Maven Plugin to package the server and deploy the application before to start it.")
    ToolResponse getMavenCommandToStartTheServer() {
        return buildResponse("mvn wildfly:start");
    }

    @Tool(description = "The maven command to provision the WildFly server and deploy the application into it. "
            + "Make sure that not server is already running when calling it.")
    ToolResponse getMavenCommandToProvisionAndDeployTheServer() {
        return buildResponse("mvn clean package");
    }

    @Tool(description = "The arguments required to generate an initial WildFly Maven "
            + "project using archetype maven plugin. The generated project is complete and ready to be built with the command mvn package.")
    ToolResponse getWildFlyMavenArchetypeGroupIdandArtifactIdGettingStarted() {
        try {
            return buildResponse("archetypeGroupId=org.wildfly.archetype "
                    + "archetypeArtifactId=wildfly-getting-started-archetype -DarchetypeVersion=" + getLatestWildFlyServerRelease());
        } catch (Exception ex) {
            return buildErrorResponse(ex.getMessage());
        }
    }

    @Tool(description = "The configuration to add to the WildFly Maven plugin <configuration> XML element "
            + "to execute WildFLy CLI operations when provisioning the server.")
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

    @Tool(description = "The maven command to stop the server. Warning: you must first have started the server prior to stop it.")
    ToolResponse getMavenCommandToStopTheServer() {
        return buildResponse("mvn wildfly:shutdown");
    }

    @Tool(description = "The exact piece of XML configuration that needs to be added to "
            + "the WildFly maven plugin <discover-provisioning-info> XML configuration element when the application is to be deployed to the cloud (openshift, kubernetes, ...)")
    ToolResponse getWildFlyMavenPluginCloudSpecificConfiguration() {
        return buildResponse("<context>cloud</context>");
    }

    @Tool(description = "The exact piece of XML configuration that needs be added to "
            + "the WildFly maven plugin <discover-provisioning-info> XML onfiguration element when the application is to benefit from WildFly High Availability features.")
    ToolResponse getWildFlyMavenPluginHASpecificConfiguration() {
        return buildResponse("<profile>ha</profile>");
    }

    @Tool(description = "The exact piece of XML configuration that needs be added to "
            + "the WildFly maven plugin <discover-provisioning-info> XML configuration element in order to provision a server of the given version.")
    ToolResponse getWildFlyMavenPluginWildFlyVersionSpecificConfiguration(@ToolArg(name = "wildfly-version", required = true) String wildflyVersion) {
        return buildResponse("<version>" + wildflyVersion + "</version>");
    }

    @Tool(description = "List the WildFly add-ons (extra server features such as add-user.sh, jboss-cli.sh command lines, openAPI and more) that could be added to the WildFly maven plugin <discover-provisioning-info> XML configuration element. Warning: The application must have been first built (as a war, ear, ...).")
    ToolResponse getWildFlyMavenPluginAddOns(
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

    private String getLatestWildFlyMavenPluginRelease() throws Exception {
        String tag = new ObjectMapper().readTree(new URL("https://api.github.com/repos/wildfly/wildfly-maven-plugin/releases/latest")).get("tag_name").asText();
        return tag.substring(1);
    }

    private String getLatestWildFlyServerRelease() throws Exception {
        String tag = new ObjectMapper().readTree(new URL("https://api.github.com/repos/wildfly/wildfly/releases/latest")).get("tag_name").asText();
        return tag;
    }
}
