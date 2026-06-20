package com.example.assistant.model;

import java.time.Instant;
import java.util.List;

public record PrRecord (
        String repo,
        Integer prNumber,
        String prTitle,
        String prBody,
        String prUrl,
        String author,
        String authorEmail,
        Instant mergedAt,
        Integer mergedAtMilli,

        List<String> changeFiles
) {}
