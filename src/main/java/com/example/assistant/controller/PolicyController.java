package com.example.assistant.controller;

import com.example.assistant.model.ChatRequest;
import com.example.assistant.model.ChatResponse;
import com.example.assistant.service.PolicyChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/bot")
public class PolicyController {


    @Value("${GOOGLE_API_KEY:NOT_SET}")
    private String geminiKey;
    @Value("${QDRANT_HOST:NOT_SET}")
    private String qdrantHost;
    @Value("${QDRANT_API_KEY:NOT_SET}")
    private String qdrantApikey;

    private final ChatClient chatClient;
    private final ChatModel chatModel;
    private final PolicyChatService policyChatService;

    private static final Logger logger = LoggerFactory.getLogger(PolicyController.class);

    public PolicyController(ChatModel chatModel, PolicyChatService policyChatService) {
        this.chatModel = chatModel;
        this.chatClient = ChatClient.create(chatModel);
        this.policyChatService = policyChatService;
    }

    @GetMapping("/status")
    public String status() {
        return "Service is running... Gemini Key="+ geminiKey + " Qdrant Host="+ qdrantHost + " Qdrant Key="+ qdrantApikey;
    }

    @GetMapping(value = "/testmodel", produces = "application/text")
    public String testModel(@RequestParam(name="q") String question) {
        question = question.concat(". Do not include headings, labels, metadata, Task, Constraint, Idea, Draft in the response.");
        logger.info("Calling Gemini, question: {}", question);
        GoogleGenAiChatOptions options = GoogleGenAiChatOptions
                .builder()
                .stopSequences(List.of("Task", "Constraint", "Draft", "Ideas", "Labels"))
                .build();
        String systemMessage = "You are a concise assistant. Do NOT include headings or labels such as Task, Constraint, Idea, Draft. Return only the answer content with bullets or numbered lines as requested.";
        String response = this.chatClient.prompt().system(systemMessage).user(question).options(options).call().content();
        logger.info("Gemini response: {}", response);
        return response;
    }



    @GetMapping(value = "/teststreammodel", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> testStreamModel(@RequestParam(name="q") String question) {
        Message systemMessage = new SystemMessage("You are a concise assistant. Do NOT include headings or labels such as Task, Constraint, Idea, Draft. Return only the answer content with bullets or numbered lines as requested.");
        Message userMessage = new UserMessage(question);
        GoogleGenAiChatOptions options = GoogleGenAiChatOptions
                .builder()
                .stopSequences(List.of("Task", "Constraint", "Draft", "Ideas", "Labels"))
                .build();
        //Prompt prompt = new Prompt(List.of(systemMessage, userMessage), options);
        Prompt prompt = new Prompt(userMessage);

        return chatModel.stream(prompt)
                .scan(new StringBuilder(), (buffer, response) -> {
                    if (response.getResult() != null && response.getResult().getOutput() != null) {
                            String buff = response.getResult().getOutput().getText();
                        //logger.info("It {} Part string {} {} " , i, buff.length(), buff);
                        buffer.append(buff);
                    }
                    //logger.info("Buffer string {}, {} " + buffer.length(), buffer);
                    return buffer;
                })
                // convert StringBuilder -> cleaned full string
                .map(StringBuilder::toString)
                .map(full -> {
                    // remove unwanted labeled lines (case-insensitive)
                    String cleaned = full.replaceAll("(?im)^[\\s\\-*]*\\b(Task|Constraint\\d*|Constraint|Idea|Draft)\\b\\s*:.*\\n?", "");
                    // normalize whitespace
                    cleaned = cleaned.replaceAll("\\n{3,}", "\n\n").trim();
                    return cleaned;
                })
                // emit only the delta since last emission
                .transform(flux -> {
                    final int[] lastLen = {0};
                    return flux.map(cleaned -> {
                        String delta = "";
                        if (cleaned.length() > lastLen[0]) {
                            delta = cleaned.substring(lastLen[0]);
                        }
                        lastLen[0] = cleaned.length();
                        return delta;
                    });
                })
                .filter(delta -> !delta.isEmpty());
    }

    /*public Flux<String> testStreamModel(@RequestParam(name="q") String question) {
        logger.info("Calling Streaming Gemma, question: {}", question);

        return this.chatClient.prompt()
                .user(question)
                //.options(GoogleGenAiChatOptions.builder().thinkingBudget(0).build())
                .stream()
                .content()
                .doOnSubscribe(s -> logger.info(">>>>Stream subscribed"))
                //.doOnNext(chunk -> logger.info(">>>STREAM CHUNK [{} chars]: [{}]", chunk.length(), chunk))
                .doOnNext(chunk -> logger.info(">>>STREAM CHUNK {} at {} ", chunk, System.currentTimeMillis()))
                //.delayElements(Duration.ofMillis(500))
                .doOnComplete(() -> logger.info(">>>Stream completed"))
                .doOnError(error -> logger.error("Stream error", error));
    }*/


    /* LLM answers directly or uses RAG
    * Body: { "query": "How many sick leaves am I entitled to?", "department": "HR" }
    *
    * */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        logger.info("Chat or Policy query : {}", request.query());
        ChatResponse chat = policyChatService.chat(request.query(), request.department());
        return ResponseEntity.ok(chat);
    }

    @GetMapping(value = "/testdummy", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> testDummy() {
        logger.info("Test stream response");
        return Flux.just("Hello \n", "from \n", "streaming \n", "endpoint! \n",
                        "Hello \n", "from \n", "streaming \n", "endpoint! \n",
                        "Hello \n", "from \n", "streaming \n", "endpoint! \n")
                .delayElements(Duration.ofMillis(1000));
    }

    /*
    Streaming chat endpoint - returns SSE stream of LM response
     */
    @GetMapping(value = "/streamchat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        logger.info("Stream Chat or Policy query : {}", request.query());
        return policyChatService.chatStream(request.query(), request.department());
    }

}
