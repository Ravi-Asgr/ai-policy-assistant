package com.example.assistant.model;

import java.time.Instant;
import java.util.List;

public class ParsedQuery {
    String repo;
    Instant from;
    Instant to;
    List<String> filenames;
    String author;
    String semanticQuery;
    int limit = 50;

    public String getRepo() {
        return repo;
    }

    public void setRepo(String repo) {
        this.repo = repo;
    }

    public Instant getFrom() {
        return from;
    }

    public void setFrom(Instant from) {
        this.from = from;
    }

    public Instant getTo() {
        return to;
    }

    public void setTo(Instant to) {
        this.to = to;
    }

    public List<String> getFilenames() {
        return filenames;
    }

    public void setFilenames(List<String> filenames) {
        this.filenames = filenames;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getSemanticQuery() {
        return semanticQuery;
    }

    public void setSemanticQuery(String semanticQuery) {
        this.semanticQuery = semanticQuery;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }
}
