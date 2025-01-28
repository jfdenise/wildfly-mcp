/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.wildfly.mcp.chat.bot;

import io.mvnpm.importmap.Aggregator;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 *
 * @author jdenise
 */
@SuppressWarnings("serial")
public class ImportMapServlet extends HttpServlet {

    private String importmap;
    private static final String JAVASCRIPT_CODE = """
            const im = document.createElement('script');
            im.type = 'importmap';
            im.textContent = JSON.stringify(%s);
            document.currentScript.after(im);
            """;

    @Override
    public void init() throws ServletException {
        Aggregator aggregator = new Aggregator();
        // Add our own mappings
        aggregator.addMapping("icons/", "/icons/");
        aggregator.addMapping("components/", "/components/");
        aggregator.addMapping("fonts/", "/fonts/");
        this.importmap = aggregator.aggregateAsJson();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (request.getPathInfo().endsWith("/dynamic-importmap.js")) {
            String script = JAVASCRIPT_CODE.formatted(this.importmap);
            response.setContentType("application/javascript");
            PrintWriter out = response.getWriter();
            out.println(script);
        } else {
            if (request.getPathInfo().endsWith("/dynamic.importmap")) {
                response.setContentType("application/importmap+json");
                PrintWriter out = response.getWriter();
                out.println(importmap);
            }
        }
    }
}
