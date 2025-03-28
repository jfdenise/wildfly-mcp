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
        for (String line : lines) {
            if (line.startsWith("//")) {
                continue;
            }
            if (line.startsWith("#")) {
                if (current != null) {
                    Metadata md = new Metadata();
                    String content = current.toString().trim();
                    if (!levelOne) {
                        md.put("parent", parentName);
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
                        TextSegment segment = TextSegment.from(content, md);
                        segments.add(segment);
                    }
                }
                current = new StringBuilder();
                int index = line.lastIndexOf("#");
                if (line.startsWith("## ")) {
                    levelOne = false;
                    // Always add the parent content to sub sections
                    line = parentContent + "\n" + line.substring(index + 2);
                } else {
                    parentName = line.substring(index + 2);
                    levelOne = true;
                    line = parentName;
                }

            }
            current.append(line.trim()).append("\n");
        }
        if (current != null) {
            Metadata md = new Metadata();
            if (!levelOne) {
                md.put("parent", parentName);
            }
            TextSegment segment = TextSegment.from(current.toString(), md);
            segments.add(segment);
        }
        return segments;
    }

}
