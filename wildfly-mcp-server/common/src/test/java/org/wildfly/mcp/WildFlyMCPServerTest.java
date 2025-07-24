/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import java.lang.reflect.Field;
import org.jboss.dmr.ModelNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import io.quarkiverse.mcp.server.TextContent;
import java.util.ArrayList;
import java.util.List;
import org.jboss.as.controller.client.OperationResponse;
import org.wildfly.mcp.WildFlyControllerClient.AddLoggerRequest;
import org.wildfly.mcp.WildFlyControllerClient.CheckDeploymentRequest;
import org.wildfly.mcp.WildFlyControllerClient.EnableLoggerRequest;
import org.wildfly.mcp.WildFlyControllerClient.GetLoggersRequest;
import org.wildfly.mcp.WildFlyControllerClient.RemoveLoggerRequest;
import org.wildfly.mcp.WildFlyControllerClient.GetLoggingFileRequest;
import org.wildfly.mcp.WildFlyControllerClient.GetRuntimeMXBean;
import org.wildfly.mcp.WildFlyControllerClient.GetOperatingSystemMXBean;
import org.wildfly.mcp.WildFlyControllerClient.GetMemoryMXBean;
import org.jboss.resteasy.reactive.ClientWebApplicationException;
import org.wildfly.mcp.WildFlyControllerClient.UndeployRequest;


@QuarkusTest
public class WildFlyMCPServerTest {

    WildFlyMCPServer server;

    WildFlyControllerClient controllerClientMock;

    @InjectMock
    @RestClient
    WildFlyMCPServer.WildFlyMetricsClient wildflyMetricsClient;

    @InjectMock
    @RestClient
    WildFlyMCPServer.WildFlyHealthClient wildflyHealthClient;

    @BeforeEach
    public void setup() throws Exception {
        server = new WildFlyMCPServer();
        server = spy(server);

        // The controller client is not injectable, so we have to mock it and set it via reflection.
        controllerClientMock = mock(WildFlyControllerClient.class);
        Field wildflyClientField = WildFlyMCPServer.class.getDeclaredField("wildflyClient");
        wildflyClientField.setAccessible(true);
        wildflyClientField.set(server, controllerClientMock);

        // The other clients are injectable, so we can set them directly.
        Field wildflyMetricsClientField = WildFlyMCPServer.class.getDeclaredField("wildflyMetricsClient");
        wildflyMetricsClientField.setAccessible(true);
        wildflyMetricsClientField.set(server, wildflyMetricsClient);

        Field wildflyHealthClientField = WildFlyMCPServer.class.getDeclaredField("wildflyHealthClient");
        wildflyHealthClientField.setAccessible(true);
        wildflyHealthClientField.set(server, wildflyHealthClient);
    }

    @Test
    public void testGetWildFlyServerConfiguration() throws Exception {
        // Prepare mock response
        ModelNode response = new ModelNode();
        ModelNode result = response.get("result");
        result.get("foo").set("bar");
        result.get("undef");


        when(controllerClientMock.call(any(Server.class), any(User.class), any(ModelNode.class)))
                .thenReturn(response);

        // Call the method
        ToolResponse toolResponse = server.getWildFlyServerConfiguration("localhost", "9990");

        // Assertions
        assertFalse(toolResponse.isError());
        String jsonResponse = ((TextContent)toolResponse.content().get(0)).text();
        // The undefined property should have been removed
        assertEquals("{\"foo\":\"bar\"}", jsonResponse.replaceAll("\\s", ""));
    }

    @Test
    public void testGetWildFlyMetaModel() throws Exception {
        // Prepare mock response
        ModelNode response = new ModelNode();
        ModelNode result = response.get("result");
        result.get("foo").set("bar");
        result.get("undef");

        when(controllerClientMock.call(any(Server.class), any(User.class), any(ModelNode.class)))
                .thenReturn(response);

        // Call the method
        ToolResponse toolResponse = server.getWildFlyMetaModel("localhost", "9990", "/subsystem=ee");

        // Assertions
        assertFalse(toolResponse.isError());
        String jsonResponse = ((TextContent)toolResponse.content().get(0)).text();
        // The undefined property should have been removed
        assertEquals("{\"foo\":\"bar\"}", jsonResponse.replaceAll("\\s", ""));
    }

    @Test
    public void testGetDeploymentFilePaths() throws Exception {
        // Prepare mock response
        ModelNode response = new ModelNode();
        ModelNode result = response.get("result");
        result.add("/a/b.txt");
        result.add("/c/d.txt");

        when(controllerClientMock.call(any(Server.class), any(User.class), any(ModelNode.class)))
                .thenReturn(response);

        // Call the method
        ToolResponse toolResponse = server.getDeploymentFilePaths("localhost", "9990", "test.war");

        // Assertions
        assertFalse(toolResponse.isError());
        String jsonResponse = ((TextContent)toolResponse.content().get(0)).text();
        assertEquals("[\"/a/b.txt\",\"/c/d.txt\"]", jsonResponse.replaceAll("\\s", ""));
    }

    @Test
    public void testGetDeploymentFileContent() throws Exception {
        // Prepare mock response
        OperationResponse opResponse = mock(OperationResponse.class);
        when(controllerClientMock.callOperation(any(), any(), any())).thenReturn(opResponse);
        doReturn("file content").when(server).getAttachment(opResponse);


        // Call the method
        ToolResponse toolResponse = server.getDeploymentFileContent("localhost", "9990", "test.war", "/WEB-INF/web.xml");

        // Assertions
        assertFalse(toolResponse.isError());
        assertEquals("file content", ((TextContent)toolResponse.content().get(0)).text());
    }

    @Test
    public void testInvokeWildFlyCLIOperation() throws Exception {
        // Prepare mock response
        ModelNode response = new ModelNode();
        response.get("result").set("success");

        when(controllerClientMock.call(any(Server.class), any(User.class), any(ModelNode.class)))
                .thenReturn(response);

        // Call the method
        ToolResponse toolResponse = server.invokeWildFlyCLIOperation("localhost", "9990", ":whoami");

        // Assertions
        assertFalse(toolResponse.isError());
        String jsonResponse = ((TextContent)toolResponse.content().get(0)).text();
        assertEquals(response.toJSONString(false).replaceAll("\\s", ""), jsonResponse.replaceAll("\\s", ""));
    }

    @Test
    public void testEnableWildFlyLoggingCategoryExists() throws Exception {
        // Prepare mock response for GetLoggersRequest
        ModelNode response = new ModelNode();
        ModelNode result = response.get("result");
        result.add("org.wildfly.security");


        when(controllerClientMock.call(any(GetLoggersRequest.class)))
                .thenReturn(response);
        when(controllerClientMock.call(any(EnableLoggerRequest.class)))
                .thenReturn(new ModelNode().set("success"));

        // Call the method
        ToolResponse toolResponse = server.enableWildFlyLoggingCategory("localhost", "9990", "org.wildfly.security");

        // Assertions
        assertFalse(toolResponse.isError());
        String textResponse = ((TextContent)toolResponse.content().get(0)).text();
        assertEquals("The logging category org.wildfly.security has been enabled by using the org.wildfly.security logger", textResponse);
    }

    @Test
    public void testEnableWildFlyLoggingCategoryNotExists() throws Exception {
        // Prepare mock response for GetLoggersRequest
        ModelNode response = new ModelNode();
        response.get("result").setEmptyList();


        when(controllerClientMock.call(any(GetLoggersRequest.class)))
                .thenReturn(response);
        when(controllerClientMock.call(any(AddLoggerRequest.class)))
                .thenReturn(new ModelNode().set("success"));

        // Call the method
        ToolResponse toolResponse = server.enableWildFlyLoggingCategory("localhost", "9990", "security");

        // Assertions
        assertFalse(toolResponse.isError());
        String textResponse = ((TextContent)toolResponse.content().get(0)).text();
        assertEquals("The logging category security has been enabled by using the org.wildfly.security logger", textResponse);
    }

    @Test
    public void testRemoveWildFlyLoggingCategory() throws Exception {
        // Prepare mock response for GetLoggersRequest
        ModelNode response = new ModelNode();
        response.get("result").add("org.wildfly.security");


        when(controllerClientMock.call(any(GetLoggersRequest.class)))
                .thenReturn(response);
        when(controllerClientMock.call(any(RemoveLoggerRequest.class)))
                .thenReturn(new ModelNode().set("success"));

        // Call the method
        ToolResponse toolResponse = server.removeWildFlyLoggingCategory("localhost", "9990", "security");

        // Assertions
        assertFalse(toolResponse.isError());
        String textResponse = ((TextContent)toolResponse.content().get(0)).text();
        assertEquals("The logging category security has been removed by using the org.wildfly.security logger.", textResponse);
    }

    @Test
    public void testRemoveWildFlyLoggingCategoryNotExists() throws Exception {
        // Prepare mock response for GetLoggersRequest
        ModelNode response = new ModelNode();
        response.get("result").setEmptyList();


        when(controllerClientMock.call(any(GetLoggersRequest.class)))
                .thenReturn(response);

        // Call the method
        ToolResponse toolResponse = server.removeWildFlyLoggingCategory("localhost", "9990", "security");

        // Assertions
        assertTrue(toolResponse.isError());
        String textResponse = ((TextContent)toolResponse.content().get(0)).text();
        assertEquals("The logging category security is not already enabled, you should first enabled it.", textResponse);
    }

    @Test
    public void testGetWildFlyLogFileContent() throws Exception {
        // Prepare mock response
        ModelNode response = new ModelNode();
        ModelNode result = response.get("result");
        result.add("line1");
        result.add("line2");


        when(controllerClientMock.call(any(GetLoggingFileRequest.class)))
                .thenReturn(response);

        // Call the method
        ToolResponse toolResponse = server.getWildFlyLogFileContent("localhost", "9990", "100", false);

        // Assertions
        assertFalse(toolResponse.isError());
        String textResponse = ((TextContent)toolResponse.content().get(0)).text();
        assertEquals("WildFly server log file Content: `line1\nline2\n`".replaceAll("\\s", ""), textResponse.replaceAll("\\s", ""));
    }

    @Test
    public void testGetWildFlyLogFileContentLastStart() throws Exception {
        // Prepare mock response
        ModelNode response = new ModelNode();
        ModelNode result = response.get("result");
        result.add("line1");
        result.add("WFLYSRV0049: blah");
        result.add("line3");
        result.add("line4");


        when(controllerClientMock.call(any(GetLoggingFileRequest.class)))
                .thenReturn(response);

        // Call the method, lastStart is true by default
        ToolResponse toolResponse = server.getWildFlyLogFileContent("localhost", "9990", null, true);

        // Assertions
        assertFalse(toolResponse.isError());
        String textResponse = ((TextContent)toolResponse.content().get(0)).text();
        assertEquals("WildFly server log file Content: `WFLYSRV0049: blah\nline3\nline4\n`", textResponse);
    }

    @Test
    public void testGetWildFlyServerAndJVMInfo() throws Exception {
        // Mocks for MXBean calls
        ModelNode runtimeResponse = new ModelNode();
        ModelNode runtimeResult = runtimeResponse.get("result");
        runtimeResult.get("name").set("test-vm");
        runtimeResult.get("input-arguments").add("-Xmx512m");
        runtimeResult.get("spec-name").set("Java Virtual Machine Specification");
        runtimeResult.get("spec-vendor").set("Oracle Corporation");
        runtimeResult.get("spec-version").set("11");
        runtimeResult.get("start-time").set(1615891200000L); // Some date
        runtimeResult.get("uptime").set(3600000L); // 1 hour
        runtimeResult.get("vm-name").set("OpenJDK 64-Bit Server VM");
        runtimeResult.get("vm-vendor").set("Red Hat, Inc.");
        runtimeResult.get("vm-version").set("11.0.11");

        ModelNode osResponse = new ModelNode();
        osResponse.get("result").get("system-load-average").set(0.5);

        ModelNode memoryResponse = new ModelNode();
        ModelNode memoryResult = memoryResponse.get("result").get("heap-memory-usage");
        memoryResult.get("max").set(1024*1024*512); // 512MB
        memoryResult.get("used").set(1024*1024*256); // 256MB

        ServerInfo serverInfo = new ServerInfo();
        serverInfo.nodeName = "test-node";
        serverInfo.productName = "WildFly";
        serverInfo.productVersion = "30.0.0.Final";
        serverInfo.coreVersion = "22.0.0.Final";

        when(controllerClientMock.call(any(GetRuntimeMXBean.class))).thenReturn(runtimeResponse);
        when(controllerClientMock.call(any(GetOperatingSystemMXBean.class))).thenReturn(osResponse);
        when(controllerClientMock.call(any(GetMemoryMXBean.class))).thenReturn(memoryResponse);
        doReturn(serverInfo).when(server).getServerInfo(any(Server.class), any(User.class), any(ServerInfo.class));

        // Call the method
        ToolResponse toolResponse = server.getWildFlyServerAndJVMInfo("localhost", "9990");

        // Assertions
        assertFalse(toolResponse.isError());
        String jsonResponse = ((TextContent)toolResponse.content().get(0)).text();

        // The vmInfo is not fully populated, so we can't do a full object comparison.
        // Instead, we'll construct the expected JSON manually.
        String expectedJson = server.toJson(serverInfo);
        assertEquals(expectedJson, jsonResponse);
    }

    @Test
    public void testGetWildFlyPrometheusMetrics() {
        // Mock metrics client
        when(wildflyMetricsClient.getMetrics(any(String.class))).thenReturn("my_metric 123");

        // Call the method
        ToolResponse toolResponse = server.getWildFlyPrometheusMetrics("localhost", "9990");

        // Assertions
        assertFalse(toolResponse.isError());
        String response = ((TextContent)toolResponse.content().get(0)).text();
        assertEquals("my_metric 123", response);
    }

    @Test
    public void testGetWildFlyPrometheusMetricsNotFound() {
        // Mock metrics client to throw 404
        ClientWebApplicationException exception = new ClientWebApplicationException(404);

        when(wildflyMetricsClient.getMetrics(any(String.class))).thenThrow(exception);

        // Call the method
        ToolResponse toolResponse = server.getWildFlyPrometheusMetrics("localhost", "9990");

        // Assertions
        assertFalse(toolResponse.isError());
        String response = ((TextContent)toolResponse.content().get(0)).text();
        assertEquals("The WildFly metrics are not available in the WildFly server running on localhost:9990", response);
    }

    @Test
    public void testGetWildFlyServerAndDeploymentsStatusHealthOk() {
        // Mock health client
        when(wildflyHealthClient.getHealth(any(String.class))).thenReturn("{\"status\":\"UP\"}");

        // Call the method
        ToolResponse toolResponse = server.getWildFlyServerAndDeploymentsStatus("localhost", "9990");

        // Assertions
        assertFalse(toolResponse.isError());
        String response = ((TextContent)toolResponse.content().get(0)).text();
        assertEquals("{\"status\":\"UP\"}", response);
    }

    @Test
    public void testGetWildFlyServerAndDeploymentsStatusHealthNotFound() throws Exception {
        // Mock health client to throw 404
        ClientWebApplicationException exception = new ClientWebApplicationException(404);
        when(wildflyHealthClient.getHealth(any(String.class))).thenThrow(exception);

        // Mock DMR status
        WildFlyDMRStatus dmrStatus = mock(WildFlyDMRStatus.class);
        when(dmrStatus.getStatus()).thenReturn(java.util.Collections.singletonList("server-state: running"));
        when(controllerClientMock.getStatus(any(Server.class), any(User.class))).thenReturn(dmrStatus);

        // Call the method
        ToolResponse toolResponse = server.getWildFlyServerAndDeploymentsStatus("localhost", "9990");

        // Assertions
        assertFalse(toolResponse.isError());
        String response = ((TextContent)toolResponse.content().get(0)).text();
        assertEquals("server-state: running", response);
    }

    @Test
    public void testDeployWildFlyApplicationNewDeployment() throws Exception {
        // Prepare mock response
        ModelNode checkDeploymentResponse = new ModelNode();
        checkDeploymentResponse.get("outcome").set("failed");
        ModelNode responseNode = new ModelNode();
        responseNode.get("outcome").set("success");
        OperationResponse opResponse = mock(OperationResponse.class);

        when(opResponse.getResponseNode())
                .thenReturn(responseNode);
        when(controllerClientMock.call(any(CheckDeploymentRequest.class)))
                .thenReturn(checkDeploymentResponse);
        when(controllerClientMock.callOperation(any(), any(), any(), any()))
                .thenReturn(opResponse);

        // Call the method
        ToolResponse toolResponse = server.deployWildFlyApplication("localhost", "9990", "/tmp/test.war", null, null);

        // Assertions
        assertFalse(toolResponse.isError());
        String textResponse = ((TextContent)toolResponse.content().get(0)).text();
        assertEquals("Successfully deployed new application: test.war from path: /tmp/test.war", textResponse);
    }

    @Test
    public void testDeployWildFlyApplicationNewDeploymentFail() throws Exception {
        // Prepare mock response
        ModelNode checkDeploymentResponse = new ModelNode();
        checkDeploymentResponse.get("outcome").set("failed");
        ModelNode responseNode = new ModelNode();
        responseNode.get("outcome").set("failed");
        OperationResponse opResponse = mock(OperationResponse.class);

        when(opResponse.getResponseNode())
                .thenReturn(responseNode);
        when(controllerClientMock.call(any(CheckDeploymentRequest.class)))
                .thenReturn(checkDeploymentResponse);
        when(controllerClientMock.callOperation(any(), any(), any(), any()))
                .thenReturn(opResponse);

        // Call the method
        ToolResponse toolResponse = server.deployWildFlyApplication("localhost", "9990", "/tmp/test.war", null, null);

        // Assertions
        assertTrue(toolResponse.isError());
        String textResponse = ((TextContent)toolResponse.content().get(0)).text();
        assertEquals("Failed to deploy new application: Unknown error", textResponse);
    }

    @Test
    public void testDeployWildFlyApplicationReplaceDeployment() throws Exception {
        // Prepare mock response
        ModelNode checkDeploymentResponse = new ModelNode();
        checkDeploymentResponse.get("outcome").set("success");
        ModelNode responseNode = new ModelNode();
        responseNode.get("outcome").set("success");
        OperationResponse opResponse = mock(OperationResponse.class);

        when(opResponse.getResponseNode())
                .thenReturn(responseNode);
        when(controllerClientMock.call(any(CheckDeploymentRequest.class)))
                .thenReturn(checkDeploymentResponse);
        when(controllerClientMock.callOperation(any(), any(), any(), any()))
                .thenReturn(opResponse);

        // Call the method
        ToolResponse toolResponse = server.deployWildFlyApplication("localhost", "9990", "/tmp/test.war", null, null);

        // Assertions
        assertFalse(toolResponse.isError());
        String textResponse = ((TextContent)toolResponse.content().get(0)).text();
        assertEquals("Successfully replaced existing deployment: test.war from path: /tmp/test.war", textResponse);
    }

    @Test
    public void testDeployWildFlyApplicationReplaceDeploymentFail() throws Exception {
        // Prepare mock response
        ModelNode checkDeploymentResponse = new ModelNode();
        checkDeploymentResponse.get("outcome").set("success");
        ModelNode responseNode = new ModelNode();
        responseNode.get("outcome").set("failed");
        OperationResponse opResponse = mock(OperationResponse.class);

        when(opResponse.getResponseNode())
                .thenReturn(responseNode);
        when(controllerClientMock.call(any(CheckDeploymentRequest.class)))
                .thenReturn(checkDeploymentResponse);
        when(controllerClientMock.callOperation(any(), any(), any(), any()))
                .thenReturn(opResponse);

        // Call the method
        ToolResponse toolResponse = server.deployWildFlyApplication("localhost", "9990", "/tmp/test.war", null, null);

        // Assertions
        assertTrue(toolResponse.isError());
        String textResponse = ((TextContent)toolResponse.content().get(0)).text();
        assertEquals("Failed to replace deployment: Unknown error", textResponse);
    }

    @Test
    public void testUndeployWildFlyApplication() throws Exception {
        // Prepare mock response
        ModelNode response = new ModelNode();
        response.get("outcome").set("success");

        when(controllerClientMock.call(any(UndeployRequest.class)))
                .thenReturn(response);

        // Call the method
        ToolResponse toolResponse = server.undeployWildFlyApplication("localhost", "9990", "test.war");

        // Assertions
        assertFalse(toolResponse.isError());
        String textResponse = ((TextContent)toolResponse.content().get(0)).text();
        assertEquals("Successfully undeployed deployment: test.war", textResponse);
    }
    
    @Test
    public void testListLoggingCategories() throws Exception {
        // Prepare mock response
        ModelNode response = new ModelNode();
        response.get("outcome").set("success");
        ModelNode runtimeResult = response.get("result");
        ModelNode category = new ModelNode();
        category.set("foo");
        List<ModelNode> lst = new ArrayList<>();
        lst.add(category);
        runtimeResult.get("logger-names").set(lst);

        when(controllerClientMock.call(any(WildFlyControllerClient.GetLoggingMXBean.class)))
                .thenReturn(response);

        // Call the method
        ToolResponse toolResponse = server.listAvailableLoggingCategories("localhost", "9990");

        // Assertions
        assertFalse(toolResponse.isError());
        String textResponse = ((TextContent)toolResponse.content().get(0)).text();
        ModelNode reply = ModelNode.fromJSONString(textResponse);
        assertEquals(runtimeResult, reply);
    }

    @Test
    public void testGetWildFlyDoc() throws Exception {
        ToolResponse toolResponse = server.getWildFlyDocumentationURL();
        
        // Assertions
        assertFalse(toolResponse.isError());
        String textResponse = ((TextContent)toolResponse.content().get(0)).text();
        assertEquals("https://docs.wildfly.org/", textResponse);
    }
    
    @Test
    public void testShutdown() throws Exception {
        ModelNode response = new ModelNode();
        response.get("outcome").set("success");
        when(controllerClientMock.call(any(WildFlyControllerClient.ShutdownRequest.class)))
                .thenReturn(response);
        ToolResponse toolResponse = server.shutdownServer(null, null, null);
        assertFalse(toolResponse.isError());
        String textResponse = ((TextContent)toolResponse.content().get(0)).text();
        assertEquals("Successfull shutdown of the server", textResponse);
    }
}