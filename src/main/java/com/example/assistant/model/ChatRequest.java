package com.example.assistant.model;

import java.util.List;

public record ChatRequest (
        String query, String department
) {}
