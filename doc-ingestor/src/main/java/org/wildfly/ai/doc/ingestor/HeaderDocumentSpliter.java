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
        String parent = "";
        for (String line : lines) {
            if (line.startsWith("#")) {
                if (current != null) {
                    Metadata md = new Metadata();
                    if (!levelOne) {
                        md.put("parent", parent);
                    }
                    TextSegment segment = TextSegment.from(current.toString(), md);
                    segments.add(segment);
                }
                current = new StringBuilder();
                int index = line.lastIndexOf("#");
                if (line.startsWith("##")) {
                    levelOne = false;
                    // Always add the parent title to sub sections
                    line = parent + ". " + line.substring(index + 1);
                } else {
                    parent = line.substring(index + 1);
                    levelOne = true;
                    line = parent;
                }
                
            }
            current.append(line).append("\n");
        }
        if (current != null) {
            Metadata md = new Metadata();
            if (!levelOne) {
                md.put("parent", parent);
            }
            TextSegment segment = TextSegment.from(current.toString(), md);
            segments.add(segment);
        }
        return segments;
    }

}
