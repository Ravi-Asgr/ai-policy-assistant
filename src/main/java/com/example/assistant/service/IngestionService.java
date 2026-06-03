package com.example.assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
public class IngestionService {

    private static final String FOLDER_PATH = "documents";
    private final VectorStore vectorStore;
    private static final Logger logger = LoggerFactory.getLogger(IngestionService.class);

    public IngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public String ingestProjectDocument() {
        List<String> fileList = new ArrayList<>();
        logger.info("Internal document ingestion folder {}", FOLDER_PATH);
        try {
            Files.walk(Paths.get(FOLDER_PATH))
                    .filter(p -> p.toString().endsWith(".pdf"))
                    .forEach(p -> {
                        String dept = p.getParent().getParent().getFileName().toString();
                        String category = p.getParent().getFileName().toString();
                        fileList.add(ingestPdf(p, dept, category,"v1"));
                        System.gc();
                    });
        } catch (IOException e) {
            logger.info("Error reading project documents at path: {}", FOLDER_PATH);
            throw new RuntimeException(e);
        }
        return fileList.toString();
    }

    private String ingestPdf(Path path, String department, String policyCategory, String version) {
        String fileName = path.getFileName().toString();
        logger.info("Ingesting file: {} of department: {}, of category: {}", fileName, department, policyCategory);
        return fileName;
    }
}
