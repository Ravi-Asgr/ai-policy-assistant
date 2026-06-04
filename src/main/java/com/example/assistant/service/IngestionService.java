package com.example.assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.stringtemplate.v4.ST;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class IngestionService {

    private static final String FOLDER_PATH = "documents";
    private final VectorStore vectorStore;
    private static final Logger logger = LoggerFactory.getLogger(IngestionService.class);

    public IngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /* Bulk ingest from folder structure: polies/{department}/{category}/filename.pdf */
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

    public Object ingestPdf(Resource resource, String dept, String category, String version) {
        try (InputStream inputStream = resource.getInputStream()) {
            //try (InputStream inputStream = new BufferedInputStream(new FileInputStream())) {

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

            //Step 1: Read PDF - 1 Document per page
            PagePdfDocumentReader reader = new PagePdfDocumentReader(resource,
                    PdfDocumentReaderConfig.builder().withPagesPerDocument(1).build());
            List<Document> pages = reader.get();

            //Step 2: Chunk into 512-token pieces
            List<Document> chunks = TokenTextSplitter.builder().withChunkSize(512).build()
                    .apply(pages);
            //new TokenTextSplitter(512, 50, 5, 10000, true).apply(pages);

            //Step 3: Enrich  metadata
            List<Document> enriched = new ArrayList<>();
            int chunk_index = 0;
            for (Document chunk : chunks) {
                Map<String, Object> meta = new HashMap<>(chunk.getMetadata());
                meta.put("file_name", fileName);
                meta.put("doc_type", "policy");
                meta.put("department", dept);
                meta.put("category", category);
                meta.put("version", version);
                meta.put("chunk_index", chunk_index++);
                enriched.add(new Document(chunk.getText(), meta));
            }

            //Step 4: Save to Qdrant
            vectorStore.add(enriched);

            return fileName;

        } catch (Exception e) {
            throw new RuntimeException("Failed to ingest PDF: " + resource.getFilename(), e);
        }
    }


}
