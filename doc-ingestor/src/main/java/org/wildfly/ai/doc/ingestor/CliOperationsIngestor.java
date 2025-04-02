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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.dmr.ModelNode;

public class CliOperationsIngestor {

    private static final String PROMPT = """
You are a WildFly expert who understands well how to administrate the WildFly server and its components.
You have to answer the user question delimited by  ---
Your reply must only contain WildFly CLI operations. To generate the CLI operations you must only use the directives delimited by %%% 
If you don't have enough information in the directives to generate CLI operations, your reply must be: FAILED TO GENERATE OPERATIONS
                                         """;

    private static String alternative(String name) {
        return name.replaceAll("-", " ");
    }

    private static boolean isPossessive(int i, char[] chars, char c) {
        return (i > 0 && chars[i - 1] != ' ' && c == '\'' && chars.length > i + 1 && chars[i + 1] == 's' && chars.length == i + 1);
    }

    private static String getWordToAdd(Set<String> dictionary, String word) {
        if (word.isEmpty()) {
            return word;
        }
        word = word.trim();
        // search the first alphanumeric char
        boolean foundAlpha = false;
        StringBuilder wordToCheck = new StringBuilder();
        char[] wordChars = word.toCharArray();
        boolean block = false;
        boolean expression = false;
        int indexOfEndExpression = 0;
        for (int j = 0; j < wordChars.length; j++) {
            char wc = wordChars[j];
            if (wc == '`' && j == 0) {
                if (!block) {
                    block = true;
                }
            }
            if (wc == '$' && j == 0) {
                if (wordChars.length > 2) {
                    if (wordChars[1] == '{') {
                        expression = true;
                    }
                }
            }
            if (wc == '}') {
                if (expression) {
                    indexOfEndExpression = j;
                }
            }
            if (foundAlpha) {
                wordToCheck.append(wc);
            } else {
                if ((wc < 48 || (wc > 57 && wc < 97)) || ((wc < 97 && wc > 57) || wc > 122)) {
                    if (isPossessive(j, wordChars, wc)) {
                        // Do not include 's
                        break;
                    }
                } else {
                    foundAlpha = true;
                    wordToCheck.append(wc);
                }
            }
        }
        char[] wordChars2 = wordToCheck.toString().toCharArray();
        int lastAlpha = 0;
        for (int j = wordChars2.length - 1; j > 0; j--) {
            char wc = wordChars2[j];
            if ((wc < 48 || (wc > 57 && wc < 97)) || ((wc < 97 && wc > 57) || wc > 122)) {
                // do nothing, ignore the char
            } else {
                lastAlpha = j;
                break;
            }
        }
        if (!wordToCheck.isEmpty()) {
            String cleanedWord = wordToCheck.substring(0, lastAlpha + 1).toString();
            if (indexOfEndExpression != 0) {
                cleanedWord = "${" + cleanedWord + "}";
            }
            if (!dictionary.contains(cleanedWord)) {
                if (!block) {
                    word = word.replace(cleanedWord, "`" + cleanedWord + "`");
                }
            }
        }
        return word;
    }

    private static String formatDescription(Set<String> dictionary, String text) {
        StringBuilder formated = new StringBuilder();
        text = text.toLowerCase();
        char[] chars = text.toCharArray();
        StringBuilder currentWord = new StringBuilder();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if ((c < 48 || (c > 57 && c < 97)) || ((c < 97 && c > 57) || c > 122)) {
                if (c == ' ' || c == '\n') {
                    String word = currentWord.toString();
                    currentWord = new StringBuilder();
                    if (word.isEmpty()) {
                        formated.append(c);
                        continue;
                    }
                    word = getWordToAdd(dictionary, word);
                    formated.append(word + c);
                }
            }
            currentWord.append(c);
        }
        formated.append(getWordToAdd(dictionary, currentWord.toString()));
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
                String title = "## syntax of the operation to get a " + resourceName + "\n";
                docbuilder.append(title);
                docbuilder.append("* operation: `" + currentPath + ":read-resource()`\n");
                docbuilder.append("* To get the list of all the " + resourceName + " use '*' for `<" + resourceType + " name>`.\n\n");
                questionsbuilder.append(title);
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
                    String formattedDescription = formatDescription(dictionary, description);
                    String title = "## syntax of the operation to get the " + resourceName + " `" + fieldName + "`\n";
                    docbuilder.append(title);
                    questionsbuilder.append(title);
                    questionsbuilder.append("Can you get the " + unblock(fieldName) + " of the example " + unblock(resourceName) + "?\n");
                    questionsLLMbuilder.append(title);
                    questionsLLMbuilder.append("Your reply must only contain a generated question that should capture the idea of retrieving a value for the following text: "
                            + "description of the " + unblock(resourceName) + " " + unblock(fieldName) + " attribute:" + description + "\n");
                    String alternative = alternative(unblock(fieldName));
                    String resourceAlernative = alternative(unblock(resourceName));
                    if (!alternative.equals(fieldName) || !resourceAlernative.equals(resourceName)) {
                        questionsbuilder.append("Can you get the " + alternative + " of the example " + resourceAlernative + "?\n");
                    }
                    docbuilder.append("* Retrieve the " + resourceName + " `" + fieldName + "` attribute value.\n");
                    docbuilder.append("* `" + fieldName + "` attribute description: " + formattedDescription).append("\n");

                    String operation = "`" + currentPath + ":read-attribute(name=" + fieldName + ")`\n\n";
                    docbuilder.append("* operation: " + operation);
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
        Path qwen2515bQuestions = Paths.get("questions/cli-questions-llm-qwen2.51.5b-generated.md");
        Path qwen253bQuestions = Paths.get("questions/cli-questions-llm-qwen2.5-3b-generated.md");
        Path questionsDoc = Paths.get("questions/cli-questions.md");
        Path generatedQuestionsDoc = Paths.get("questions/cli-questions-generated.md");
        Path generatedLLMQuestionsTemplateDoc = Paths.get("templates/cli-questions-llm-generated.md");

        Path llmRagReplies = Paths.get("rag-cli-replies.md");

        ChatLanguageModel model = OllamaChatModel.builder()
                .modelName("qwen2.5:3b")
                .baseUrl("http://127.0.0.1:11434")
                .logRequests(true)
                .logResponses(true)
                .build();
        ChatLanguageModel mistralModel = MistralAiChatModel.builder()
                .modelName("mistral-small-latest")
                .apiKey(System.getenv("MISTRAL_API_KEY"))
                .logRequests(true)
                .logResponses(true)
                .build();
        if (Boolean.getBoolean("generate-llm")) {
            Path generatedLLMQuestionsDoc = Paths.get("questions/cli-questions-llm-mistral-small-generated.md");
            Files.deleteIfExists(generatedLLMQuestionsDoc);
            Files.createFile(generatedLLMQuestionsDoc);
            List<String> lines = Files.readAllLines(generatedLLMQuestionsTemplateDoc);
            StringBuilder currentQuestion = new StringBuilder();
            for (String line : lines) {
                if (line.startsWith("#")) {
                    if (!currentQuestion.isEmpty()) {
                        System.out.println(currentQuestion);
                        String reply = invokeLLM(mistralModel, currentQuestion.toString(), 2000);

                        System.out.println("==>" + reply);
                        Files.write(generatedLLMQuestionsDoc, (reply + "\n\n").getBytes(), StandardOpenOption.APPEND);
                    }
                    Files.write(generatedLLMQuestionsDoc, (line + "\n").getBytes(), StandardOpenOption.APPEND);
                    currentQuestion = new StringBuilder();
                } else {
                    if (line.isEmpty()) {
                        Files.write(generatedLLMQuestionsDoc, (line + "\n").getBytes(), StandardOpenOption.APPEND);
                    } else {
                        currentQuestion.append(line);
                    }
                }
            }
            String reply = model.chat(currentQuestion.toString());
            System.out.println("==>" + reply);
            Files.write(generatedLLMQuestionsDoc, (reply + "\n\n").getBytes(), StandardOpenOption.APPEND);
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
        if (Boolean.getBoolean("analyze-llm-replies")) {
            CommandContext ctx = CommandContextFactory.getInstance().newCommandContext();
            Path llmHWRagReplies = Paths.get("rag-test-replies/rag-cli-replies-handwritten-questions.md");
            Path qwen253bRagReplies = Paths.get("rag-test-replies/rag-cli-replies-qwen2.53b-questions.md");
            List<String> allLines = new ArrayList<>();
            allLines.addAll(Files.readAllLines(llmHWRagReplies));
            allLines.addAll(Files.readAllLines(qwen253bRagReplies));
            int questions = 0;
            int noAnswer = 0;
            int containsExactOp = 0;
            int parsedOp = 0;
            int failureParsedOp = 0;
            Iterator<String> linesIt = allLines.iterator();
            while (linesIt.hasNext()) {
                String l = linesIt.next();
                if (l.trim().isEmpty()) {
                    continue;
                }
                if (l.startsWith("## question")) {
                    questions += 1;
                    continue;
                }
                if (l.startsWith("## directives")) {
                    Set<String> ops = new HashSet<>();
                    Set<String> llmOps = new HashSet<>();
                    String directiveLine = linesIt.next();
                    String op = null;
                    while (!directiveLine.startsWith("#")) {
                        directiveLine = directiveLine.trim();
                        if (directiveLine.startsWith("operation: ")) {
                            op = unblock(directiveLine.substring(11, directiveLine.length()));
                            if (op.endsWith("()")) {
                                op = op.substring(0, op.length() - 2).toLowerCase();
                            }
                            ops.add(op);
                        } else {
                            Pattern p = Pattern.compile("^.* use '\\*' for `(.*)`\\.$");
                            Matcher m = p.matcher(directiveLine);
                            if (m.matches()) {
                                String name = m.group(1);
                                String allOp = op.replace(name, "*");
                                ops.add(allOp.toLowerCase());
                            }
                        }
                        directiveLine = linesIt.next();
                    }
                    String llmReply = linesIt.next();
                    while (!llmReply.startsWith("#")) {
                        llmReply = llmReply.trim();
                        if (!llmReply.isEmpty()) {
                            llmReply = unblock(llmReply);
                            if (llmReply.startsWith("/")) {
                                if (llmReply.endsWith("()")) {
                                    llmReply = llmReply.substring(0, llmReply.length() - 2);
                                }
                                llmOps.add(llmReply.toLowerCase());
                            }
                        }
                        if (linesIt.hasNext()) {
                            llmReply = linesIt.next();
                        } else {
                            break;
                        }
                    }
                    if (llmOps.isEmpty()) {
                        noAnswer += 1;
                        continue;
                    }
                    boolean found = false;
                    for (String s : ops) {
                        if (llmOps.contains(s)) {
                            found = true;
                            containsExactOp += 1;
                            break;
                        }
                    }
                    if (!found) {
                        // Attempt to parse the LLM operations

                        boolean containsFailure = false;
                        for (String o : llmOps) {
                            try {
                                ModelNode mn = ctx.buildRequest(o);
                            } catch (Exception ex) {
                                containsFailure = true;
                            }
                        }
                        if (containsFailure) {
                            failureParsedOp += 1;
                        } else {
                            parsedOp += 1;

                        }
                    }
                }
            }
            System.out.println("NUM QUESTIONS            :" + questions);
            System.out.println("NUM EXACT MATCH          :" + containsExactOp + " " + (containsExactOp * 100) / questions + "%");
            System.out.println("NUM NO ANSWER            :" + noAnswer + " " + (noAnswer * 100) / questions + "%");
            System.out.println("NUM REPLY PARSE OK       :" + parsedOp + " " + (parsedOp * 100) / questions + "%");
            System.out.println("NUM REPLY PARSE FAILURES :" + failureParsedOp + " " + (failureParsedOp * 100) / questions + "%");

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
            List<String> questionsNotRanked = new ArrayList<>();
            System.out.println("Loading embeddings...");
            InMemoryEmbeddingStore embeddingStore = InMemoryEmbeddingStore.fromFile(docEmbeddings);
            System.out.println("Loading questions segments...");
            List<String> qSegments = Files.readAllLines(questionsSegments);
            if ("questions".equals(question)) {
                List<String> questions = new ArrayList<>();
                System.out.println("Reading questions...");
                if (Boolean.getBoolean("invoke-llm")) {
                    Files.deleteIfExists(llmRagReplies);
                    Files.createFile(llmRagReplies);
                }
                questions.addAll(Files.readAllLines(generatedQuestionsDoc));
                questions.addAll(Files.readAllLines(qwen2515bQuestions));
                questions.addAll(Files.readAllLines(qwen253bQuestions));
                questions.addAll(Files.readAllLines(questionsDoc));
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
                    line = line.trim();
                    if (line.startsWith("#") || line.isEmpty() || line.startsWith("//")) {
                        continue;
                    }
                    num += 1;
                    if (num == 100) {
                        total += num;
                        System.out.println("Checked " + total);
                        num = 0;
                    }
                    numQuestions += 1;
                    String filteredQuestion = line;//generalizeQuestion(line, lexique, dictionary);
                    Embedding queryEmbedding = embeddingModel.embed(filteredQuestion.toString()).content();
                    List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(queryEmbedding, 4);
                    StringBuilder messageBuilder = new StringBuilder();
                    List<String> augmentedContext = new ArrayList<>();
                    for (EmbeddingMatch<TextSegment> match : relevant) {
                        // Only keep the top 4.
                        if (match.score() >= 0.7 && augmentedContext.size() < 4) {
                            augmentedContext.add(match.embedded().text());
                        }
                        messageBuilder.append("\n" + match.score() + " ------------\n");
                        messageBuilder.append(match.embedded().text());
                    }
                    String questionTitle = getQuestionMetadata("title", qSegments, line);
                    int index = 0;
                    double score = 0.0;
                    boolean topRanked = false;
                    boolean ranked = false;
                    for (int i = 0; i < relevant.size(); i++) {
                        // Check the top 4
                        if (i == 4) {
                            break;
                        }
                        EmbeddingMatch<TextSegment> match = relevant.get(i);
                        if (match.score() >= 0.7) {
                            if (i == 0) {
                                //Check that the best result has the same title than the question.
                                if (match.embedded().metadata().getString("title").equals(questionTitle)) {
                                    questionsTopRanked.add(line);
                                    topRanked = true;
                                    ranked = true;
                                    break;
                                }
                            }
                            if (match.embedded().metadata().getString("title").equals(questionTitle)) {
                                index = i + 1;
                                score = match.score();
                            }
                        }
                    }
                    if (!topRanked) {
                        if (score != 0.0) {
                            String l = line + "[rank " + index + ", score " + score + "]";

                            if (Boolean.getBoolean("debug")) {
                                l = "\n###########\n" + l + "\n" + messageBuilder;
                            }
                            questionsNotTopRanked.add(l);
                            ranked = true;
                        } else {
                            // questions for which the expected doc is not in any score
                            if (Boolean.getBoolean("debug")) {
                                line = "\n###########\n" + line + "\n" + messageBuilder;
                            }
                            questionsNotRanked.add(line);
                        }
                    }
                    if (ranked && Boolean.getBoolean("invoke-llm")) {
                        // call the LLM and see 
                        StringBuilder build = new StringBuilder(PROMPT + "\n");
                        StringBuilder directivesBuilder = new StringBuilder();
                        build.append("---" + line + "---\n");
                        build.append("%%%\n");
                        for (String directive : augmentedContext) {
                            directivesBuilder.append(directive + "\n");
                        }
                        build.append(directivesBuilder.toString());
                        build.append("%%%");
                        System.out.println("Question " + numQuestions);
                        StringBuilder content = new StringBuilder();
                        content.append("# " + questionTitle + "\n\n");
                        content.append("## question\n\n");
                        content.append(line + "\n\n");
                        content.append("## directives\n\n");
                        for (String directive : augmentedContext) {
                            content.append("* " + directive + "\n");
                        }
                        content.append("\n## LLM reply\n\n");
                        String reply = invokeLLM(mistralModel, build.toString(), 2000);
                        content.append(reply + "\n\n");
                        Files.write(llmRagReplies, content.toString().getBytes(), StandardOpenOption.APPEND);
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

    private static final String invokeLLM(ChatLanguageModel model, String question, long sleep) throws Exception {
        while (true) {
            try {
                if (sleep != -1) {
                    Thread.sleep(sleep);
                }
                return model.chat(question);
            } catch (Exception ex) {
                System.out.println("LLM exception " + ex);
                System.out.println("Will retry in 1 minute");
                Thread.sleep(65000);
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
