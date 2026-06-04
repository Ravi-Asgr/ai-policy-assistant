package com.example.assistant.model;

import java.util.List;

public record ChatResponse (
        String answer, List<String> sources, boolean usedPolicyContext
) {}
