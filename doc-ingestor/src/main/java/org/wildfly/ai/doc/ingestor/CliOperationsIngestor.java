/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.doc.ingestor;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class CliOperationsIngestor {
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        Path file = Paths.get("wildfly-embedding-cli.json");
        String question = System.getProperty("question");

        if (question == null) {
            InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
            DocumentSplitter splitter = new HeaderDocumentSpliter();
            List<Document> documents = FileSystemDocumentLoader.loadDocumentsRecursively("wildfly-docs/cli");
            for (Document dd : documents) {
                List<TextSegment> segments = splitter.split(dd);
                Path p = Paths.get("segments-cli.txt");
                Files.deleteIfExists(p);
                Files.createFile(p);
                for (TextSegment s : segments) {
                    embeddingStore.add(embeddingModel.embed(s).content(), s);
                    String ss = "\n-------------------\n" + s + "\n-------------------\n";
                    Files.write(p, ss.getBytes(), StandardOpenOption.APPEND);
                }

                System.out.println("SEGMENTS " + segments.size());
            }

            ((InMemoryEmbeddingStore) embeddingStore).serializeToFile(file);
            System.out.println("Embeddings stored into " + file.toAbsolutePath());
        } else {
            InMemoryEmbeddingStore embeddingStore = InMemoryEmbeddingStore.fromFile(file);
            ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(embeddingStore)
                    .embeddingModel(embeddingModel)
                    .maxResults(4)
                    .minScore(0.7)
                    .build();
            List<Content> ragContents = contentRetriever.retrieve(Query.from(question));
            Embedding queryEmbedding = embeddingModel.embed(question).content();
            List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(queryEmbedding, 4);
            StringBuilder messageBuilder = new StringBuilder();
            for (EmbeddingMatch<TextSegment> match : relevant) {
                messageBuilder.append("\n"+ match.score()+ " ------------\n");
                messageBuilder.append(match.embedded().text());
            }
            System.out.println(messageBuilder);
        }

    }
}
