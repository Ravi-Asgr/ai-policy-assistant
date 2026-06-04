package com.example.assistant.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
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

    private static final Logger logger = LoggerFactory.getLogger(PolicyController.class);

    public PolicyController(ChatModel chatModel) {
        this.chatClient = ChatClient.create(chatModel);
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
}
