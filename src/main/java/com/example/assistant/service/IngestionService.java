package com.example.assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.stringtemplate.v4.ST;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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


    public String loadPdfsFromResources() {
        List<Object> fileList = new ArrayList<>();

        try {
            PathMatchingResourcePatternResolver resolver =
                    new PathMatchingResourcePatternResolver();

            Resource[] resources =
                    resolver.getResources("classpath*:documents/**/*.pdf");

            for (Resource resource : resources) {
                String relativePath = getRelativeDocumentsPath(resource);

                String[] pathParts = relativePath.split("/");

                if (pathParts.length < 4) {
                    System.out.println("Skipping invalid PDF path: " + relativePath);
                    continue;
                }

                String dept = pathParts[1];       // hr / business
                String category = pathParts[2];   // leave / recruitment / rfp

                fileList.add(ingestPdf(resource, dept, category, "v1"));

                System.gc();
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to load PDFs from resources", e);
        }

        return fileList.toString();
    }


    private String getRelativeDocumentsPath(Resource resource) {
        try {
            String url = resource.getURL().toString();

            url = URLDecoder.decode(url, StandardCharsets.UTF_8);

            int index = url.indexOf("documents/");

            if (index == -1) {
                throw new IllegalStateException(
                        "Could not locate documents path in resource URL: " + url
                );
            }

            return url.substring(index);

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to extract relative path for resource: " + resource.getFilename(),
                    e
            );
        }
    }


    private Object ingestPdf(Resource resource, String dept, String category, String version) {
        try (InputStream inputStream = resource.getInputStream()) {

            String fileName = resource.getFilename();

            System.out.println("Ingesting PDF");
            System.out.println("File     : " + fileName);
            System.out.println("Dept     : " + dept);
            System.out.println("Category : " + category);
            System.out.println("Version  : " + version);

            /*
             * Your existing PDF processing logic goes here.
             *
             * Example:
             * 1. Read PDF using inputStream
             * 2. Extract text
             * 3. Split into chunks
             * 4. Add metadata: dept, category, version, fileName
             * 5. Store chunks into Qdrant
             */

            return fileName;

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to ingest PDF: " + resource.getFilename(),
                    e
            );
        }
    }


}
