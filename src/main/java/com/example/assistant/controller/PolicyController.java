package com.example.assistant.controller;

import com.example.assistant.model.ChatRequest;
import com.example.assistant.model.ChatResponse;
import com.example.assistant.service.PolicyChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;

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
    private final PolicyChatService policyChatService;

    private static final Logger logger = LoggerFactory.getLogger(PolicyController.class);

    public PolicyController(ChatModel chatModel, PolicyChatService policyChatService) {
        this.chatClient = ChatClient.create(chatModel);
        this.policyChatService = policyChatService;
    }

    @GetMapping("/status")
    public String status() {
        return "Service is running... Gemini Key="+ geminiKey + " Qdrant Host="+ qdrantHost + " Qdrant Key="+ qdrantApikey;
    }

    @GetMapping("/testmodel")
    public String testModel(@RequestParam(name="q") String question) {
        logger.info("Calling Gemini, question: {}", question);
        String response = this.chatClient.prompt().user(question).call().content();
        logger.info("Gemini response: {}", response);
        return response;
    }

    /*@GetMapping(value = "/teststreammodel", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> testStreamModel(@RequestParam(name="q") String question) {
        logger.info("Calling Streaming Gemini, question: {}", question);
        return this.chatClient.prompt().user(question).stream().content();
    }*/


    @GetMapping(value = "/teststreammodel", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> testStreamModel(@RequestParam(name="q") String question) {
        logger.info("Calling Streaming Gemma, question: {}", question);

        return this.chatClient.prompt()
                .user(question)
                .stream()
                .content()
                .doOnSubscribe(s -> logger.info("Stream subscribed"))
                .doOnNext(chunk -> logger.info("STREAM CHUNK [{} chars]: [{}]", chunk.length(), chunk))
                .doOnComplete(() -> logger.info("Stream completed"))
                .doOnError(error -> logger.error("Stream error", error));
    }


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
        return Flux.just("Hello ", "from ", "streaming ", "endpoint!")
                .delayElements(Duration.ofMillis(900));
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
