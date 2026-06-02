package com.example.assistant.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bot")
public class PolicyController {

    @GetMapping("/status")
    public String status() {
        return "Service is running...";
    }
}
