package com.example.assistant.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bot")
public class PolicyController {


    @Value("${GOOGLE_API_KEY:NOT_SET}")
    private String key;

    @GetMapping("/status")
    public String status() {
        return "Service is running..."+ key;
    }
}
