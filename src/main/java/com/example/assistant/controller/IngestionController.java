package com.example.assistant.controller;

import com.example.assistant.service.IngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ingest")
public class IngestionController {

    private final IngestionService ingestionService;
    private static final Logger logger = LoggerFactory.getLogger(IngestionController.class);

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    public String ingestInternalDocuments() {
        logger.info("Internal document ingestion invoked");
        return this.ingestionService.ingestProjectDocument();
    }

}
