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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class CliOperationsIngestor {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        List<String> englishDictionary = Files.readAllLines(Paths.get("../wildfly-chat-bot/extra-content/standalone/configuration/words_alpha.txt"));
        Path file = Paths.get("../wildfly-chat-bot/extra-content/standalone/configuration/embeddings.json");
        Path lexiqueFile = Paths.get("../wildfly-chat-bot/extra-content/standalone/configuration/cli_lexique.txt");
        Path cliDoc = Paths.get("wildfly-docs/cli/cli.md");
        List<String> doc = Files.readAllLines(cliDoc);
        Set<String> lexique = buildLexique(doc, englishDictionary);

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

            Files.deleteIfExists(lexiqueFile);
            Files.createFile(lexiqueFile);
            for (String l : lexique) {
                Files.writeString(lexiqueFile, l + "\n", StandardOpenOption.APPEND);
            }
            System.out.println("Lexique written to " + lexiqueFile.toAbsolutePath());
        } else {
            InMemoryEmbeddingStore embeddingStore = InMemoryEmbeddingStore.fromFile(file);

            if ("cli-questions.md".equals(question)) {
                List<String> questions = Files.readAllLines(Paths.get("cli-questions.md"));

                // For now reuse the input doc as lexique
                List<String> lexiqueDoc = Files.readAllLines(Paths.get("wildfly-docs/cli/cli.md"));
                Set<String> questionHeaders = new HashSet<>();
                Set<String> docHeaders = new HashSet<>();

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
                            lexique.add(word.toLowerCase());
                        }
                    }

                }
                for (String header : docHeaders) {
                    if (!questionHeaders.contains(header)) {
                        throw new RuntimeException(header + " is not overed by questions");
                    }
                }
                int numQuestions = 0;
                for (String line : questions) {
                    line = line.trim();
                    if (line.startsWith("#") || line.isEmpty() || line.startsWith("//")) {
                        continue;
                    }
                    numQuestions += 1;
                    String filteredQuestion = generalizeQuestion(line, lexique, englishDictionary);
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
                    }
                }
                System.out.println("NUM OF QUESTIONS " + numQuestions);
            } else {
                String generalizedQuestion = generalizeQuestion(question, lexique, englishDictionary);
                Embedding queryEmbedding = embeddingModel.embed(generalizedQuestion).content();
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
                System.out.println("GENERALIZED " + generalizedQuestion);
                System.out.append(messageBuilder);
            }
        }

    }

    private static Set<String> buildLexique(List<String> lines, List<String> englishDictionary) {
        Set<String> lexique = new TreeSet<>();
        for (String l : lines) {
            if (l.startsWith("//") || l.isEmpty()) {
                continue;
            }
            l = l.toLowerCase();
            char[] chars = l.toCharArray();
            boolean blockEntered = false;
            StringBuilder currentWord = new StringBuilder();
            for (char c : chars) {
                if (c == '`') {
                    if (blockEntered) {
                        if (!englishDictionary.contains(currentWord.toString()) && !currentWord.toString().isEmpty()) {
                            lexique.add(currentWord.toString());
                        }
                        currentWord = new StringBuilder();
                        blockEntered = false;
                    } else {
                        blockEntered = true;
                    }
                } else {
                    if (c == ' ' || c == '\n') {
                        if (!blockEntered) {
                            if (!englishDictionary.contains(currentWord.toString()) && !currentWord.toString().isEmpty()) {
                                lexique.add(currentWord.toString());
                            }
                            currentWord = new StringBuilder();
                        } else {
                            currentWord.append(c);
                        }
                    } else {
                        // special characters other than a-z, 0-9
                        if ( (c < 48 || (c > 57 && c < 97)) || ((c < 97 && c > 57) || c > 122)) {
                            if (blockEntered) {
                                currentWord.append(c);
                            }
                        } else {
                            currentWord.append(c);
                        }
                    }
                }
            }
            if (!englishDictionary.contains(currentWord.toString()) && !currentWord.toString().isEmpty()) {
                lexique.add(currentWord.toString());
            }
        }
        return lexique;
    }

    // TODO must apply logic from lexique generation
    private static String generalizeQuestion(String question, Set<String> lexique, List<String> dictionary) {
        String[] words = question.split("\\s+");
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
            word = word.trim();
            char c = word.charAt(word.length()-1);
            // remove 
            boolean lastCharRemoved = false;
            if ( (c < 48 || (c > 57 && c < 97)) || ((c < 97 && c > 57) || c > 122)) {
                word = word.substring(0, word.length() -1);
                lastCharRemoved = true;
            }
            if(word.isEmpty()) {
                continue;
            }
            System.out.println("WORD " + word);
            if (!lexique.contains(word) && !dictionary.contains(word)) {
                // to be generailzed
                System.out.println("NOT CONTAINED! " + word);
                if (question.contains("deployment")) {
                    word = "deployment";
                } else {
                    continue;
                }
            }
            // Actually do not keep punctiation
            // it can reduce matches.
            //if(lastCharRemoved) {
            //    word = word + c;
            //}
            filteredQuestion.append(word + " ");
        }
        String generalized = filteredQuestion.toString();
        if(!question.equals(generalized)) {
            System.out.println("QUESTION: " + question);
            System.out.println("GENERALIZED: " + generalized);
        }
        return generalized;
    }
}
