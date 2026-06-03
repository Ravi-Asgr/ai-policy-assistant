package com.example.assistant.controller;

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

    @GetMapping("/status")
    public String status() {
        return "Service is running... Gemini Key="+ geminiKey + " Qdrant Host="+ qdrantHost + " Qdrant Key="+ qdrantApikey;
    }
}
