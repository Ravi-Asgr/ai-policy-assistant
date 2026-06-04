package com.example.assistant.controller;

import com.example.assistant.service.IngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/ingest")
public class IngestionController {

    private final IngestionService ingestionService;
    private static final Logger logger = LoggerFactory.getLogger(IngestionController.class);

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /*
    Bulk ingest from server folder
     */
    @GetMapping("/internal")
    public String ingestInternalDocuments() {
        logger.info("Internal document ingestion invoked");
        return ingestionService.loadPdfsFromResources();
    }

    /*
     * Ingest a single file
     */
    @PostMapping("/ingest")
    public ResponseEntity<String> ingest(
            @RequestParam MultipartFile multipartFile,
            @RequestParam String department,
            @RequestParam String category,
            @RequestParam(defaultValue = "v1") String version) throws IOException {

        Path temp = Files.createTempFile("policy-", ".pdf");
        multipartFile.transferTo(temp);
        ingestionService.ingestPdf(new FileSystemResource(temp), department, category, version);
        Files.delete(temp);
        return ResponseEntity.ok("Ingested {}" + multipartFile.getOriginalFilename());
    }
}
