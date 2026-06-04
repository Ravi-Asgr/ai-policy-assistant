package com.example.assistant.controller;

import com.example.assistant.model.ChatRequest;
import com.example.assistant.model.ChatResponse;
import com.example.assistant.service.PolicyChatService;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    /* LLS anawers directly or uses RAG
    * Body: { "query": "How many sick leaves am I entitled to?", "department": "HR" }
    *
    * */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        logger.info("Chat or Policy query : {}", request.query());
        ChatResponse chat = policyChatService.chat(request.query(), request.department());
        return ResponseEntity.ok(chat);
    }
}
