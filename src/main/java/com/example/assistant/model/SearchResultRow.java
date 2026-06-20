package com.example.assistant.model;

import java.time.Instant;
import java.util.List;

public record SearchResultRow (
        Integer prNumber,
        String prTitle,
        String author,
        String prUrl,
        Instant mergedAt,
        List<String>changeFiles,
        Double score // optional similarity score
) {}
