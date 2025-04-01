/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.doc.ingestor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class CliOperationsIngestor {

    private static String alternative(String name) {
        return name.replaceAll("-", " ");
    }

    private static String reduce(String name) {
        return name.replaceAll("-", "");
    }

    private static String formatDescription(Set<String> dictionary, String text) {
        StringBuilder formated = new StringBuilder();
        text = text.toLowerCase();
        char[] chars = text.toCharArray();
        boolean blockEntered = false;
        StringBuilder currentWord = new StringBuilder();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            //System.out.println("[" + c + "], [" + currentWord + "], [" + formated + "]");
            if (c == '\'' || c == '\"') {
                if (blockEntered) {
                    String w = currentWord.toString();
                    w = w.trim();
                    if (w.isEmpty()) {
                        continue;
                    }
                    boolean lastCharRemoved = false;
                    boolean lastChar2Removed = false;
                    char last = w.charAt(w.length() - 1);
                    char last2 = 0;
                    if (w.length() > 2) {
                        last2 = w.charAt(w.length() - 2);
                        if ((last < 48 || (last > 57 && last < 97)) || ((last < 97 && last > 57) || last > 122)) {
                            w = w.substring(0, w.length() - 1);
                            lastCharRemoved = true;
                        }
                        if ((last2 < 48 || (last2 > 57 && last2 < 97)) || ((last2 < 97 && last2 > 57) || last2 > 122)) {
                            w = w.substring(0, w.length() - 1);
                            lastChar2Removed = true;
                        }
                    }
                    if (!w.toString().isEmpty() && !dictionary.contains(w)) {
                        formated.append("`" + w + "`" + (lastChar2Removed ? last2 : "") + (lastCharRemoved ? last : ""));
                    } else {
                        formated.append(w + (lastChar2Removed ? last2 : "") + (lastCharRemoved ? last : ""));
                    }
                    //formated.append(" ");
                    currentWord = new StringBuilder();
                    blockEntered = false;
                } else {
                    if (i > 0 && chars[i - 1] != ' ' && c == '\'' && chars.length > i + 1 && (chars[i + 1] == 's' || chars[i + 1] == 't')) {
                        currentWord.append(c);
                    } else {
                        blockEntered = true;
                    }
                }
            } else {
                if (c == '(') {
                    if (blockEntered) {
                        currentWord.append(c);
                    } else {
                        formated.append(c);
                    }
                } else {

                    if (c == ' ' || c == '\n') {

                        if (!blockEntered) {
                            String w = currentWord.toString();
                            //w = w.trim();
                            if (w.isEmpty()) {
                                formated.append(c);
                                continue;
                            }
                            boolean lastCharRemoved = false;
                            boolean lastChar2Removed = false;
                            char last = w.charAt(w.length() - 1);
                            if ((last < 48 || (last > 57 && last < 97)) || ((last < 97 && last > 57) || last > 122)) {
                                w = w.substring(0, w.length() - 1);
                                lastCharRemoved = true;
                            }
                            char last2 = 0;
                            if (w.length() > 2) {
                                last2 = w.charAt(w.length() - 1);
                                if ((last2 < 48 || (last2 > 57 && last2 < 97)) || ((last2 < 97 && last2 > 57) || last2 > 122)) {
                                    w = w.substring(0, w.length() - 1);
                                    lastChar2Removed = true;
                                }
                            }
                            if (!w.toString().isEmpty() && !dictionary.contains(w)) {
                                formated.append("`" + w + (lastChar2Removed ? last2 : "") + "`" + (lastCharRemoved ? last : ""));
                            } else {
                                formated.append(w + (lastChar2Removed ? last2 : "") + (lastCharRemoved ? last : ""));
                            }
                            formated.append(" ");
                            currentWord = new StringBuilder();
                        } else {
                            formated.append(c);
                        }
                    } else {
                        currentWord.append(c);
                    }
                }
            }
        }
        String w = currentWord.toString();
        w = w.trim();
        if (!w.isEmpty()) {
            boolean lastCharRemoved = false;
            boolean lastChar2Removed = false;
            if (w.length() == 1) {
                formated.append(w);
            } else {
                char last = w.charAt(w.length() - 1);
                char last2 = w.charAt(w.length() - 2);
                if ((last < 48 || (last > 57 && last < 97)) || ((last < 97 && last > 57) || last > 122)) {
                    w = w.substring(0, w.length() - 1);
                    lastCharRemoved = true;
                }
                if ((last2 < 48 || (last2 > 57 && last2 < 97)) || ((last2 < 97 && last2 > 57) || last2 > 122)) {
                    w = w.substring(0, w.length() - 1);
                    lastChar2Removed = true;
                }

                if (!w.toString().isEmpty() && !dictionary.contains(w)) {
                    formated.append("`" + w + "`" + (lastChar2Removed ? last2 : "") + (lastCharRemoved ? last : ""));
                } else {
                    formated.append(w + (lastChar2Removed ? last2 : "") + (lastCharRemoved ? last : ""));
                }
            }
        }
        return formated.toString();
    }

    private static String unblock(String str) {
        if (str.startsWith("`") && str.endsWith("`")) {
            StringBuilder builder = new StringBuilder();
            for (char c : str.toCharArray()) {
                if (c == '`') {
                    continue;
                }
                builder.append(c);
            }
            str = builder.toString();
        }
        return str;
    }

    private static void generateResourceDoc(ChatLanguageModel model, Set<String> dictionary, StringBuilder docbuilder,
            StringBuilder questionsbuilder,
            StringBuilder questionsLLMbuilder,
            JsonNode resourceNode,
            boolean isCustomNamed,
            String resourceName,
            String resourceType,
            String currentPath) throws Exception {
        if (!isCustomNamed) {
            Iterator<String> knownResources = resourceNode.fieldNames();
            while (knownResources.hasNext()) {
                String knownResource = knownResources.next();
                JsonNode resource = resourceNode.get(knownResource);
                generateResourceDoc(model, dictionary, docbuilder, questionsbuilder, questionsLLMbuilder, resource, true, resourceName + " `" + knownResource + "`", null, currentPath + knownResource);
            }
        } else {
            if (resourceType != null && !resourceType.equals("subsystem")) {
                docbuilder.append("## syntax of the operation to get a " + resourceName).append("\n");
                docbuilder.append("operation: `" + currentPath + ":read-resource()`\n");
                docbuilder.append("To get the list of all the " + resourceName + " use '*' for `<" + resourceType + " name>`.\n\n");
                questionsbuilder.append("## syntax of the operation to get a " + resourceName).append("\n");
                questionsbuilder.append("Can you get all the " + unblock(resourceName) + "?\n");
                questionsbuilder.append("Can you get all the " + unblock(resourceName) + "s?\n");
                questionsbuilder.append("Can you get the " + unblock(resourceName) + " foo?\n");
                questionsbuilder.append("Can you get the foo " + unblock(resourceName) + "?\n\n");
            }
            if (resourceNode.has("attributes")) {
                JsonNode attributes = resourceNode.get("attributes");
                Iterator<String> fieldNames = attributes.fieldNames();
                while (fieldNames.hasNext()) {
                    String fieldName = fieldNames.next();
                    JsonNode field = attributes.get(fieldName);
                    String description = field.get("description").asText();
                    String fornmattedDescription = formatDescription(dictionary, description);
                    String title = "## syntax of the operation to get the " + resourceName + " `" + fieldName + "`\n";
                    docbuilder.append(title);
                    questionsbuilder.append(title);
                    questionsbuilder.append("Can you get the " + unblock(fieldName) + " of the example " + unblock(resourceName) + "?\n");
                    questionsLLMbuilder.append(title);
                    questionsLLMbuilder.append("Your reply must only contain a generated question for the following text. "
                            + "description of the " + unblock(resourceName) + " " + unblock(fieldName) + " attribute:" + description + "\n");
//                    String generatedQuestion = model.chat("Your reply must only contain a generated question for the following text: " + description);
//                    System.out.println("TEXT: " + description);
//                    System.out.println("MISTRAL GENERATED: " + generatedQuestion);
//                    questionsbuilder.append(generatedQuestion + "\n\n");
//                    Thread.sleep(1000);

                    String alternative = alternative(unblock(fieldName));
                    String resourceAlernative = alternative(unblock(resourceName));
                    if (!alternative.equals(fieldName) || !resourceAlernative.equals(resourceName)) {
                        questionsbuilder.append("Can you get the " + alternative + " of the example " + resourceAlernative + "?\n");
                    }
                    docbuilder.append(fornmattedDescription).append("\n");
                    docbuilder.append("get the " + resourceName + " `" + fieldName + "` attribute.\n");
                    String operation = "`" + currentPath + ":read-attribute(name=" + fieldName + ")`\n\n";
                    docbuilder.append(operation);
                }
            }
            if (resourceNode.has("children")) {
                JsonNode children = resourceNode.get("children");
                Iterator<String> childrenNames = children.fieldNames();
                while (childrenNames.hasNext()) {
                    String childrenName = childrenNames.next();
                    boolean isChildCustomeNamed = children.get(childrenName).get("model-description").has("*");
                    JsonNode node = isChildCustomeNamed ? children.get(childrenName).get("model-description").get("*")
                            : children.get(childrenName).get("model-description");
                    generateResourceDoc(model, dictionary, docbuilder, questionsbuilder, questionsLLMbuilder,
                            node,
                            isChildCustomeNamed,
                            resourceName + " `" + childrenName + "`",
                            childrenName,
                            currentPath + "/" + childrenName + "=" + (isChildCustomeNamed ? "<" + childrenName + " name>" : ""));
                }
            }
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        Set<String> dictionary = Files.readAllLines(Paths.get("../wildfly-chat-bot/extra-content/standalone/configuration/words_alpha.txt")).stream().collect(Collectors.toSet());
        Path docEmbeddings = Paths.get("../wildfly-chat-bot/extra-content/standalone/configuration/embeddings.json");
        Path lexiqueFile = Paths.get("../wildfly-chat-bot/extra-content/standalone/configuration/cli_lexique.txt");
        Path cliDoc = Paths.get("wildfly-docs/cli/cli.md");
        Path generatedCliDoc = Paths.get("wildfly-docs/cli/cli-generated.md");
        Path questionsSegments = Paths.get("segments/segments-cli-questions.txt");
        Path quen2515bQuestions = Paths.get("questions/cli-questions-llm-qwen2.51.5b-generated.md");
        Path questionsDoc = Paths.get("questions/cli-questions.md");
        Path generatedQuestionsDoc = Paths.get("questions/cli-questions-generated.md");
        Path generatedLLMQuestionsTemplateDoc = Paths.get("templates/cli-questions-llm-generated.md");

//        ChatLanguageModel model = MistralAiChatModel.builder()
//                .apiKey(System.getenv("MISTRAL_API_KEY")) // Please use your own Mistral AI API key
//                .modelName("mistral-small-latest")
//                .logRequests(true)
//                .logResponses(true)
//                .build();
        ChatLanguageModel model = OllamaChatModel.builder()
                .modelName("qwen2.5:1.5b")
                .baseUrl("http://127.0.0.1:11434")
                .logRequests(true)
                .logResponses(true)
                .build();
        if (Boolean.getBoolean("generate-llm")) {
            Path generatedLLMQuestionsDoc = Paths.get("questions/cli-questions-llm-qwen2.51.5b-generated.md");
            StringBuilder output = new StringBuilder();
            List<String> lines = Files.readAllLines(generatedLLMQuestionsTemplateDoc);
            StringBuilder currentQuestion = new StringBuilder();
            for (String line : lines) {
                if (line.startsWith("#")) {
                    if (!currentQuestion.isEmpty()) {
                        System.out.println(currentQuestion);
                        String reply = model.chat(currentQuestion.toString());

                        System.out.println("==>" + reply);
                        output.append(reply + "\n\n");
                    }
                    output.append(line + "\n");
                    currentQuestion = new StringBuilder();
                } else {
                    if (line.isEmpty()) {
                        output.append(line + "\n");
                    } else {
                        currentQuestion.append(line);
                    }
                }
            }
            String reply = model.chat(currentQuestion.toString());
            System.out.println("==>" + reply);
            output.append(reply + "\n\n");
            Files.deleteIfExists(generatedLLMQuestionsDoc);
            Files.write(generatedLLMQuestionsDoc, output.toString().getBytes(), StandardOpenOption.CREATE);
            return;
        }
        if (Boolean.getBoolean("generate-doc")) {
            // First generate the doc and question templates
            Path descriptionsDir = Paths.get("json-descriptions");
            Path wildflyModel = descriptionsDir.resolve("wildfly-subsystems-model.json");
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode descObj = objectMapper.readTree(Files.readString(wildflyModel));
            ArrayNode children = (ArrayNode) descObj.get("result");
            StringBuilder docbuilder = new StringBuilder();
            StringBuilder questionsbuilder = new StringBuilder();
            StringBuilder questionsLLMbuilder = new StringBuilder();
            for (JsonNode n : children) {
                ArrayNode address = (ArrayNode) n.get("address");
                String subsystemName = address.get(0).get("subsystem").asText();
                String path = "/subsystem=" + subsystemName;
                generateResourceDoc(model, dictionary, docbuilder, questionsbuilder, questionsLLMbuilder, n.get("result"), true, "`" + subsystemName + "`", "subsystem", path);
            }
            Files.deleteIfExists(generatedCliDoc);
            Files.write(generatedCliDoc, docbuilder.toString().getBytes(), StandardOpenOption.CREATE);
            Files.deleteIfExists(generatedQuestionsDoc);
            Files.write(generatedQuestionsDoc, questionsbuilder.toString().getBytes(), StandardOpenOption.CREATE);
            Files.deleteIfExists(generatedLLMQuestionsTemplateDoc);
            Files.write(generatedLLMQuestionsTemplateDoc, questionsLLMbuilder.toString().getBytes(), StandardOpenOption.CREATE);
            Files.deleteIfExists(lexiqueFile);
            Files.createFile(lexiqueFile);
            System.out.println("Build lexique....");
            System.out.println("Reading doc...");
            List<String> doc = new ArrayList<>();
            doc.addAll(Files.readAllLines(cliDoc));
            doc.addAll(Files.readAllLines(generatedCliDoc));
            Set<String> lexique = buildLexique(doc, dictionary);
            for (String l : lexique) {
                Files.writeString(lexiqueFile, l + "\n", StandardOpenOption.APPEND);
            }
            System.out.println("Lexique written to " + lexiqueFile.toAbsolutePath());
            return;
        }
        String question = System.getProperty("question");
        if (question == null) {
            // First vectorize the doc
            InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
            DocumentSplitter splitter = new HeaderDocumentSpliter();

            List<Document> documents = FileSystemDocumentLoader.loadDocumentsRecursively("wildfly-docs/cli");
            Path p = Paths.get("segments/segments-cli.txt");
            Files.deleteIfExists(p);
            Files.createFile(p);
            for (Document dd : documents) {
                List<TextSegment> segments = splitter.split(dd);
                for (TextSegment s : segments) {
                    embeddingStore.add(embeddingModel.embed(s).content(), s);
                    String ss = "\n-------------------\n" + s + "\n-------------------\n";
                    Files.write(p, ss.getBytes(), StandardOpenOption.APPEND);
                }
            }

            ((InMemoryEmbeddingStore) embeddingStore).serializeToFile(docEmbeddings);
            System.out.println("Embeddings stored into " + docEmbeddings.toAbsolutePath());

            //Store the question segments used fo testing
            List<Document> cliQuestionsDocuments = FileSystemDocumentLoader.loadDocumentsRecursively("questions");
            Files.deleteIfExists(questionsSegments);
            Files.createFile(questionsSegments);
            for (Document dd : cliQuestionsDocuments) {
                List<TextSegment> segments = splitter.split(dd);
                for (TextSegment s : segments) {
                    String ss = "\n-------------------\n"
                            + "parent=" + s.metadata().getString("parent") + "\n"
                            + "title=" + s.metadata().getString("title") + "\n"
                            + s.text() + "\n-------------------\n";
                    Files.write(questionsSegments, ss.getBytes(), StandardOpenOption.APPEND);
                }
            }
        } else {
            System.out.println("Reading doc...");
            List<String> doc = new ArrayList<>();
            doc.addAll(Files.readAllLines(cliDoc));
            doc.addAll(Files.readAllLines(generatedCliDoc));
            Set<String> lexique = Files.readAllLines(lexiqueFile).stream().collect(Collectors.toSet());
            List<String> questionsNotTopRanked = new ArrayList<>();
            List<String> questionsTopRanked = new ArrayList<>();
            //List<String> questionsVeryBadlyRanked = new ArrayList<>();
            List<String> questionsNotRanked = new ArrayList<>();
            System.out.println("Loading embeddings...");
            InMemoryEmbeddingStore embeddingStore = InMemoryEmbeddingStore.fromFile(docEmbeddings);
            System.out.println("Loading questions segments...");
            List<String> qSegments = Files.readAllLines(questionsSegments);
            if ("questions".equals(question)) {
                List<String> questions = new ArrayList<>();
                System.out.println("Reading questions...");
                questions.addAll(Files.readAllLines(questionsDoc));
                questions.addAll(Files.readAllLines(generatedQuestionsDoc));
                questions.addAll(Files.readAllLines(quen2515bQuestions));
                // For now reuse the input doc as lexique
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
                for (String header : docHeaders) {
                    if (!questionHeaders.contains(header)) {
                        throw new RuntimeException(header + " is not covered by questions");
                    }
                }
                int numQuestions = 0;
                System.out.println("Num of doc lines " + doc.size());
                System.out.println("Num of question lines " + questions.size());
                System.out.println("Checking segments...");
                int num = 0;
                int total = 0;
                for (String line : questions) {
                    num += 1;
                    if (num == 100) {
                        total += num;
                        System.out.println("Checked " + total);
                        num = 0;
                    }
                    line = line.trim();
                    if (line.startsWith("#") || line.isEmpty() || line.startsWith("//")) {
                        continue;
                    }
                    numQuestions += 1;
                    String filteredQuestion = line;//generalizeQuestion(line, lexique, dictionary);
                    Embedding queryEmbedding = embeddingModel.embed(filteredQuestion.toString()).content();
                    List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(queryEmbedding, 4);
                    StringBuilder messageBuilder = new StringBuilder();
                    for (EmbeddingMatch<TextSegment> match : relevant) {
                        messageBuilder.append("\n" + match.score() + " ------------\n");
                        messageBuilder.append(match.embedded().text());
                    }
                    //Check that the best result on has the same title than the question.
                    EmbeddingMatch<TextSegment> topScore = relevant.get(0);
                    String matchTitle = topScore.embedded().metadata().getString("title");
                    String questionTitle = getQuestionMetadata("title", qSegments, line);
                    int index = 0;
                    double score = 0.0;
                    if (!matchTitle.equals(questionTitle)) {
                        for (int i = 0; i < relevant.size(); i++) {
                            EmbeddingMatch<TextSegment> match = relevant.get(i);
                            if (match.score() >= 0.7) {
                                if (match.embedded().metadata().getString("title").equals(questionTitle)) {
                                    index = i + 1;
                                    score = match.score();
                                }
                            }
                        }
                        if (score != 0.0) {
                            String l = line + "[rank " + index + ", score " + score + "]";

                            if (Boolean.getBoolean("debug")) {
                                l = "\n###########\n" + l + "\n" + messageBuilder;
                            }
                            questionsNotTopRanked.add(l);
                        } else {
                            // questions for which the expected doc is not in any score
                            if (Boolean.getBoolean("debug")) {
                                line = "\n###########\n" + line + "\n" + messageBuilder;
                            }
                            questionsNotRanked.add(line);
                        }
                    } else {
                        questionsTopRanked.add(line);
                    }
                }
                if (!questionsNotTopRanked.isEmpty()) {
                    System.out.println("-------------NOT TOP RANKED QUESTIONS-------------");
                    for (String str : questionsNotTopRanked) {
                        System.out.println(str);
                    }
                }
                if (!questionsNotRanked.isEmpty()) {
                    System.out.println("-------------NOT RANKED QUESTIONS-------------");
                    for (String str : questionsNotRanked) {
                        System.out.println(str);
                    }
                }
                System.out.println("--------------------------");
                System.out.println("TOTAL NUM OF QUESTIONS       :" + numQuestions);
                System.out.println("NUM TOP RANKED QUESTIONS     :" + questionsTopRanked.size() + " " + ((questionsTopRanked.size() * 100 / numQuestions) + "%"));
                System.out.println("NUM NOT TOP RANKED QUESTIONS :" + questionsNotTopRanked.size() + " " + ((questionsNotTopRanked.size() * 100 / numQuestions) + "%"));
                System.out.println("NUM NOT RANKED QUESTIONS     :" + questionsNotRanked.size() + " " + ((questionsNotRanked.size() * 100 / numQuestions) + "%"));
                if (!questionsNotRanked.isEmpty()) {
                    throw new Exception("Some questions are not ranked!");
                }
            } else {
                String generalizedQuestion = question;//generalizeQuestion(question, lexique, dictionary);
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
                System.out.append(messageBuilder);
            }
        }

    }

    private static String getQuestionMetadata(String key, List<String> segments, String question) throws Exception {
        String lastTitle = null;
        for (String s : segments) {
            if (s.startsWith(key + "=")) {
                lastTitle = s.substring(key.length() + 1);
            }
            if (s.equals(question)) {
                return lastTitle;
            }
        }
        throw new Exception("Question " + question + " has no " + key);
    }

    private static Set<String> buildLexique(List<String> lines, Set<String> englishDictionary) {
        Set<String> lexique = new TreeSet<>();
        System.out.println("Documentation lines " + lines.size());
        for (String l : lines) {
            // Remove comments and operations
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
                        if ((c < 48 || (c > 57 && c < 97)) || ((c < 97 && c > 57) || c > 122)) {
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
        Set<String> clean = new TreeSet<>();
        for (String w : lexique) {
            if (w.endsWith("'s")) {
                w = w.substring(0, w.length() - 2);
            }
            if (w.endsWith(")") && (!w.startsWith("/") && !w.startsWith(":"))) {
                w = w.substring(0, w.length() - 1);
            }
            if (!englishDictionary.contains(w)) {
                clean.add(w);
            }
        }
        return clean;
    }

    // TODO must apply logic from lexique generation
    private static String generalizeQuestion(String question, Set<String> lexique, Set<String> dictionary) {
        String[] words = question.split("\\s+");
        StringBuilder filteredQuestion = new StringBuilder();
        for (String word : words) {
            word = word.toLowerCase();
            word = word.trim();
            char c = word.charAt(word.length() - 1);
            // remove 
            boolean lastCharRemoved = false;
            if ((c < 48 || (c > 57 && c < 97)) || ((c < 97 && c > 57) || c > 122)) {
                word = word.substring(0, word.length() - 1);
                lastCharRemoved = true;
            }
            if (word.isEmpty()) {
                continue;
            }
            if (!lexique.contains(word) && !dictionary.contains(word)) {
                // to be generailzed
                if (word.endsWith(".war") || question.contains("deployment")) {
                    word = "myapp.war";
                } else {
                    if (word.endsWith(".xml")) {
                        word = "file";
                    } else {
                        if (question.contains("property")) {
                            word = "foo";
                        }
                        continue;
                    }
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
        if (!question.equals(generalized)) {
            if (Boolean.getBoolean("debug")) {
                System.out.println("QUESTION: " + question);
                System.out.println("GENERALIZED: " + generalized);
            }
        }
        return generalized;
    }
}
