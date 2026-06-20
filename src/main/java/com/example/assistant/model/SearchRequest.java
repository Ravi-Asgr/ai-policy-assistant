package com.example.assistant.model;

import java.time.Instant;
import java.util.List;

public record SearchRequest (
        String repo,                 // optional
        Instant from,                // optional
        Instant to,                  // optional
        List<String>filenames,       // optional - match any
        String query,                // optional - semantic query
        int limit
) {}
