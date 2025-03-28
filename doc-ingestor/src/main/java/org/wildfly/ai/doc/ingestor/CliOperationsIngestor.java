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
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CliOperationsIngestor {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        Path file = Paths.get("../wildfly-chat-bot/extra-content/standalone/configuration/embeddings.json");
        Path lexiqueFile = Paths.get("../wildfly-chat-bot/extra-content/standalone/configuration/cli_lexique.md");
        Path cliDoc = Paths.get("wildfly-docs/cli/cli.md");
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
            Files.copy(cliDoc, lexiqueFile, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Copied cli doc as lexique to " + file.toAbsolutePath());
        } else {
            InMemoryEmbeddingStore embeddingStore = InMemoryEmbeddingStore.fromFile(file);
            if ("cli-questions.md".equals(question)) {
                List<String> englishDictionary = Files.readAllLines(Paths.get("../wildfly-chat-bot/extra-content/standalone/configuration/words_alpha.txt"));
                List<String> questions = Files.readAllLines(Paths.get("cli-questions.md"));
                List<String> doc = Files.readAllLines(Paths.get("wildfly-docs/cli/cli.md"));
                // For now reuse the input doc as lexique
                List<String> lexiqueDoc = Files.readAllLines(Paths.get("wildfly-docs/cli/cli.md"));
                Set<String> questionHeaders = new HashSet<>();
                Set<String> docHeaders = new HashSet<>();
                Set<String> lexique = new HashSet();
                for (String line : questions) {
                    if (line.startsWith("#")) {
                        questionHeaders.add(line.trim());
                    }
                }
                for (String line : doc) {
                    if (line.startsWith("#")) {
                        docHeaders.add(line.trim());
                    }
                }
                for (String line : lexiqueDoc) {
                    if (!line.startsWith("#")) {
                        String[] words = line.split("\\s+");
                        for (String word : words) {
                            lexique.add(word);
                        }
                    }

                }
                for (String header : docHeaders) {
                    if (!questionHeaders.contains(header)) {
                        throw new RuntimeException(header + " is not overed by questions");
                    }
                }
                for (String line : questions) {
                    line = line.trim();
                    if (line.startsWith("#") || line.isEmpty()) {
                        continue;
                    }
                    String[] words = line.split("\\s+");
                    StringBuilder filteredQuestion = new StringBuilder();
                    for (String word : words) {
                        word = word.toLowerCase();
                        word = word.trim();
                        if (word.contains(".war")) {
                            word = "deployment";
                        }
                        if (word.contains(".xml")) {
                            word = "file";
                        }
                        word = word.replaceAll("[^a-zA-Z]", "");
                        word = word.trim();
                        if (!lexique.contains(word) && !englishDictionary.contains(word)) {
                            // to be generailzed
                            if (line.contains("deployment")) {
                                word = "deployment";
                            } else {
                                continue;
                            }
                        }

                        filteredQuestion.append(word + " ");
                    }
                    Embedding queryEmbedding = embeddingModel.embed(filteredQuestion.toString()).content();
                    List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(queryEmbedding, 4);
                    StringBuilder messageBuilder = new StringBuilder();
                    List<Double> scores = new ArrayList<>();
                    for (EmbeddingMatch<TextSegment> match : relevant) {
                        if (match.score() >= 0.7) {
                            scores.add(match.score());
                        }
                        messageBuilder.append("\n" + match.score() + " ------------\n");
                        messageBuilder.append(match.embedded().text());
                    }
                    if (scores.size() < 1) {
                        System.out.println("QUESTION: " + line + "\n" + filteredQuestion + "\n" + scores);
                        System.err.println(messageBuilder);
                        throw new Exception("Question " + filteredQuestion + " Has low scoring " + scores);
                    } else {
                        System.out.println("QUESTION: " + line + "\n" + filteredQuestion + "\n" + scores);
                    }
                }
            } else {
                Embedding queryEmbedding = embeddingModel.embed(question).content();
                List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(queryEmbedding, 4);
                StringBuilder messageBuilder = new StringBuilder();
                List<Double> scores = new ArrayList<>();
                for (EmbeddingMatch<TextSegment> match : relevant) {
                    if (match.score() >= 0.7) {
                        scores.add(match.score());
                    }
                    messageBuilder.append("\n" + match.score() + " ------------\n");
                    messageBuilder.append(match.embedded().text());
                }
                System.out.append(messageBuilder);
            }
        }

    }
}
