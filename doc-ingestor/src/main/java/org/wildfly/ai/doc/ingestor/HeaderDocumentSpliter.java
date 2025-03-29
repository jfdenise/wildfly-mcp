/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.doc.ingestor;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple 2 level: # or ##
 *
 * @author jdenise
 */
public class HeaderDocumentSpliter implements DocumentSplitter {

    @Override
    public List<TextSegment> split(Document dcmnt) {
        String[] lines = dcmnt.text().split("\\n");
        List<TextSegment> segments = new ArrayList<>();
        StringBuilder current = null;
        boolean levelOne = true;
        String parentName = "";
        // Parent content is a one liner, replicated in each sub section.
        String parentContent = "";
        Metadata currentMetadata = new Metadata();
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("//")) {
                continue;
            }
            if (line.startsWith("#")) {
                int index = line.lastIndexOf("#");
                String title = line.substring(index + 2);
                if (current != null) {
                    String content = current.toString().trim();
                    if (!levelOne) {
                        currentMetadata.put("parent", parentName);
                    } else {
                        parentContent = content;
                    }
                    if (!levelOne) {
                        int words = content.split("\\s+").length;
                        if (words >= 256) {
                            System.err.println(content);
                            throw new RuntimeException("Content length is " + words + ". Max allowed is 256.\n");
                        } else {
                            System.out.println("segment lenth " + words);
                        }
                        TextSegment segment = TextSegment.from(content, currentMetadata);
                        segments.add(segment);
                        currentMetadata = new Metadata();
                    }
                }
                current = new StringBuilder();

                if (line.startsWith("##")) {
                    levelOne = false;
                    // Always add the parent content to sub sections if not a single line
                    line = (parentContent.lines().count() > 1 ? parentContent + "\n" : "") + title;
                } else {
                    parentName = line.substring(index + 2);
                    levelOne = true;
                    line = parentName;
                }
                currentMetadata.put("title", title);
            }
            current.append(line.trim()).append("\n");
        }
        if (current != null) {
            if (!levelOne) {
                currentMetadata.put("parent", parentName);
                TextSegment segment = TextSegment.from(current.toString(), currentMetadata);
                segments.add(segment);
            }
        }
        return segments;
    }

}
