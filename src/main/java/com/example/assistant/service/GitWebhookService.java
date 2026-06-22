package com.example.assistant.service;

import com.example.assistant.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GitWebhookService {

    private static final Logger logger = LoggerFactory.getLogger(GitWebhookService.class);

    @Value("${github.webhook.secret:encryption}")
    private String secret;

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${github.token}")
    private String githubToken;

    public GitWebhookService(RestTemplateBuilder restTemplateBuilder, ObjectMapper objectMapper,
                             VectorStore vectorStore, ChatModel chatModel) {
        this.restTemplate = restTemplateBuilder.build();
        this.objectMapper = objectMapper;
        this.vectorStore = vectorStore;
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem("""
                        You are a concise assistant that formats Github Pull Request search results.
                        Answer general questions directly from your knowledge.
                        Do NOT include headings like Task, Constraint, Idea, or Draft.
                        When asked for a table, return only a markdown table with columns: PR, Author, Files, Merged At, PR URL.
                        For list queries, return a bulleted list of PR numbers and titles.
                        Keep responses factual and concise.
                        If you don't know, say so clearly.
                        """)
                .build();
    }

    public boolean isValidSignature(String payload, String signHeader) {

        if (signHeader == null || !signHeader.startsWith("sha256=")) {
            logger.error("Problem with signed value (from header) {}", signHeader);
            return false;
        }

        try {
            String expectedSignature = "sha256=" + computeHmacSha(payload);
            boolean isSignMatch = constantTimeEquals(expectedSignature, signHeader);
            if (isSignMatch) {
                logger.info("Signature validation is success");
                return true;
            }
        } catch (Exception e) {
            logger.error("Error calculating secret from payload. Exception: {}", e.getMessage());
            return false;
        }

        logger.error("Signature validation Error!!");
        return false;
    }

    private String computeHmacSha(String payload) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length())
            return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }


    public PrRecord parseRawPayload(String payload) {
        try {
            logger.info("Parsing the string payload");
            JsonNode node = objectMapper.readTree(payload);
            //logger.info("Json node as string {}", node.toPrettyString());

            // PR parent node
            JsonNode pullReqNode = node.hasNonNull("pull_request") ? node.get("pull_request") : null;
            if (payload == null) {
                logger.error("Webhook payload does not have pull_request field");
                return null;
            }
            // Check pull request state: open, closed
            String prState = pullReqNode.hasNonNull("state") ? pullReqNode.get("state").asText(null) : null;
            if (null == prState || !"closed".equalsIgnoreCase(prState)) {
                logger.error("Invalid PR status: {}", prState);
                return null;
            }
            // PR title, default is empty string
            String prtitle = pullReqNode.hasNonNull("title") ? pullReqNode.get("title").asText("") : "";
            // PR URL
            String prURL = pullReqNode.hasNonNull("url") ? pullReqNode.get("url").asText(null) : null;
            if (!StringUtils.hasText(prURL)) {
                logger.error("Invalid PR URL: {}", prURL);
                return null;
            }
            // PR number
            String prNumber = pullReqNode.hasNonNull("number") ? pullReqNode.get("number").asText(null) : null;
            if (!StringUtils.hasText(prNumber)) {
                logger.error("Invalid PR number: {}", prNumber);
                return null;
            }
            // PR open time
            String prMerged = pullReqNode.hasNonNull("merged_at") ? pullReqNode.get("merged_at").asText(null) : null;
            if (!StringUtils.hasText(prMerged)) {
                logger.error("PR is not yet merged,skipping it");
                return null;
            }
            Instant mergedDate = parseToInstant(prMerged);
            Long mergedSec = mergedDate.getEpochSecond();
            // PR body
            String prBody = pullReqNode.hasNonNull("body") ? pullReqNode.get("body").asText(null) : null;
            // User parent node
            JsonNode userNode = pullReqNode.hasNonNull("user") ? pullReqNode.get("user") : null;
            if(null == userNode) {
                logger.error("Invalid PR payload, author is missing");
                return null;
            }
            String author = userNode.hasNonNull("login") ? userNode.get("login").asText(null) : null;
            if (!StringUtils.hasText(author)) {
                logger.error("Invalid PR payload, author is missing");
                return null;
            }
            // Repository parent node
            JsonNode repoNode = node.hasNonNull("repository") ? node.get("repository") : null;
            if(null == repoNode) {
                logger.error("Invalid PR payload, repository is missing");
                return null;
            }
            String repoName = repoNode.hasNonNull("name") ? repoNode.get("name").asText(null) : null;
            if (!StringUtils.hasText(repoName)) {
                logger.error("Invalid PR payload, repo name is missing");
                return null;
            }
            Set<String> fileSet = getListOfFilesUpdated("Ravi-Asgr", repoName, Integer.parseInt(prNumber));
            List<String> files = new ArrayList<>(fileSet);
            PrRecord pr = new PrRecord(repoName, Integer.parseInt(prNumber),
                    prtitle, prBody, prURL, author, "", mergedDate, mergedSec.intValue(), new ArrayList<>(files));

            logger.info("PrRecord as string {}", objectMapper.writeValueAsString(pr));

            return pr;
        } catch (Exception ex) {
            logger.error("Invalid JSON or parse error Exception = {}", ex.getMessage());
            return null;
        }
    }

    /**
     * Synchronous method that calls GitHub Pulls Files API and follows pagination.
     *
     * @param owner    repository owner (e.g., "Ravi-Asgr")
     * @param repo     repository name (e.g., "spring-cloud-service-deploy")
     * @param prNumber pull request number
     * @return deduped Set of filenames changed in the PR (preserves insertion order)
     * @throws RuntimeException on HTTP errors or unexpected response shapes
     */
    public Set<String> getListOfFilesUpdated(String owner, String repo, int prNumber) {
        if (owner == null || owner.isBlank() || repo == null || repo.isBlank()) {
            throw new IllegalArgumentException("owner and repo must be provided");
        }

        String firstPage = UriComponentsBuilder.fromUriString("https://api.github.com")
                .pathSegment("repos", owner, repo, "pulls", String.valueOf(prNumber), "files")
                .queryParam("per_page", "100")
                .build()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, "rest-template-client");
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(githubToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        Set<String> filenames = new LinkedHashSet<>();
        String nextUrl = firstPage;

        while (nextUrl != null) {
            ResponseEntity<String> resp = restTemplate.exchange(nextUrl, HttpMethod.GET, entity, String.class);

            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("GitHub API returned non-2xx: " + resp.getStatusCodeValue()
                        + " for URL: " + nextUrl);
            }

            String body = resp.getBody();
            if (body != null && !body.isBlank()) {
                try {
                    // parse JSON array into List<Map<String,Object>>
                    List<Map<String, Object>> list = objectMapper.readValue(body, new TypeReference<>() {});
                    for (Map<String, Object> fileObj : list) {
                        Object filenameObj = fileObj.get("filename");
                        if (filenameObj != null) filenames.add(filenameObj.toString());
                    }
                } catch (Exception ex) {
                    throw new RuntimeException("Failed to parse GitHub response JSON", ex);
                }
            }

            // parse Link header for next page
            String link = resp.getHeaders().getFirst(HttpHeaders.LINK);
            nextUrl = extractNextLink(link);
        }

        return filenames;
    }

    /**
     * Extracts the URL for rel="next" from a GitHub Link header.
     * Example Link header:
     * <https://api.github.com/...&page=2>; rel="next", <https://api.github.com/...&page=4>; rel="last"
     */
    private String extractNextLink(String linkHeader) {
        if (linkHeader == null || linkHeader.isBlank()) return null;
        // split on comma, find part with rel="next"
        String[] parts = linkHeader.split(",");
        for (String part : parts) {
            int semicolon = part.indexOf(";");
            if (semicolon > 0) {
                String urlPart = part.substring(0, semicolon).trim();
                String relPart = part.substring(semicolon + 1).trim();
                if (relPart.contains("rel=\"next\"")) {
                    // urlPart is like <https://...>
                    int lt = urlPart.indexOf("<");
                    int gt = urlPart.indexOf(">");
                    if (lt >= 0 && gt > lt) {
                        return urlPart.substring(lt + 1, gt);
                    } else {
                        return urlPart.replaceAll("[<>\\s]", "");
                    }
                }
            }
        }
        return null;
    }

    /**
     * Create Documents (chunks) for the PR and add to vector store.
     * We create:
     *  - one pr_summary Document
     *  - one file_chunk Document per changed file
     */
    public void indexPr(PrRecord pr) {
        logger.info("Creating Documents to Index");
        List<Document> docs = createDocumentsForPr(pr);
        logger.info("Creating Documents to Index of size {}", docs.size());
        // vectorStore.add should handle embedding + upsert to Qdrant
        // If your VectorStore API requires a collection name, pass it accordingly.
        //vectorStore.add(collectionName, docs);
        vectorStore.add(docs);
    }

    private List<Document> createDocumentsForPr(PrRecord pr) {
        List<Document> docs = new ArrayList<>();

        // PR summary text (used for PR-level queries)
        String summaryText = buildPrSummaryText(pr);

        Map<String, Object> basePayload = new HashMap<>();
        basePayload.put("pr_number", pr.prNumber());
        basePayload.put("repo", pr.repo());
        basePayload.put("pr_title", pr.prTitle());
        basePayload.put("pr_body", pr.prBody());
        basePayload.put("pr_url", pr.prUrl());
        basePayload.put("author", pr.author());
        basePayload.put("author_email", pr.authorEmail());
        basePayload.put("merged_at", pr.mergedAt() != null ? pr.mergedAt().toString() : null);
        basePayload.put("merged_at_milli", pr.mergedAt() != null ? pr.mergedAtMilli() : null);
        basePayload.put("change_files", pr.changeFiles());
        basePayload.put("chunk_type", "pr_summary");

        // Create Document for PR summary, set deterministic id so re-upsert replaces old
        //but semantic string identifier is causing problems as Qdrant only supports 128-bit UUIDs or 64-bit positive integers as point IDs,
        String id = "pr-" + slug(pr.repo()) + "-" + pr.prNumber() + "-summary";
        // 2. Convert the custom text string into a valid, deterministic UUIDv5.
        // This ALWAYS generates the exact same UUID for the exact same input string.
        String validQdrantUuid = UUID.nameUUIDFromBytes(id.getBytes()).toString();

        Document prDoc = Document.builder()
                .id(validQdrantUuid)
                .text(summaryText)
                .metadata(basePayload)
                .build();
        // set deterministic id so re-upsert replaces old
        //prDoc = prDoc.withId("pr-" + slug(pr.repo()) + "-" + pr.prNumber() + "-summary");
        docs.add(prDoc);

        // Create one Document per file
        if (pr.changeFiles() != null) {
            for (String filename : pr.changeFiles()) {
                Map<String, Object> filePayload = new HashMap<>(basePayload);
                filePayload.put("filename", filename);
                filePayload.put("file_status", "modified"); // or "added" if you know
                filePayload.put("chunk_type", "file_chunk");

                String fileText = "Filename: " + filename + "\nChanged in PR #" + pr.prNumber()
                        + "\nRepo: " + pr.repo()
                        + "\nTitle: " + pr.prTitle();

                String fileId = "pr-" + slug(pr.repo()) + "-" + pr.prNumber() + "-file-" + slug(filename);
                String qdrantUuid = UUID.nameUUIDFromBytes(fileId.getBytes()).toString();

                Document fileDoc = Document.builder()
                        .id(qdrantUuid)
                        .text(fileText)
                        .metadata(filePayload)
                        .build();
                docs.add(fileDoc);
            }
        }

        return docs;
    }

    private String buildPrSummaryText(PrRecord pr) {
        StringBuilder sb = new StringBuilder();
        sb.append(pr.prTitle() == null ? "" : pr.prTitle()).append("\n\n");
        if (StringUtils.hasText(pr.prBody())) {
            sb.append(pr.prBody()).append("\n\n");
        }
        sb.append("Repo: ").append(pr.repo()).append("\n");
        sb.append("Author: ").append(pr.author()).append("\n");
        sb.append("MergedAt: ").append(pr.mergedAt() != null ? pr.mergedAt().toString() : "null").append("\n");
        sb.append("MergedAtMilli: ").append(pr.mergedAt() != null ? pr.mergedAtMilli() : "null").append("\n");
        sb.append("PR No: ").append(pr.prNumber()).append("\n");
        sb.append("PR URL: ").append(pr.prUrl()).append("\n");
        sb.append("Files: ").append(pr.changeFiles() != null ? String.join(", ", pr.changeFiles()) : "");
        return sb.toString();
    }

    private String slug(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("[^a-z0-9]+", "-");
    }

    /**
     * Search method: builds metadata filter and either runs a metadata-only query
     * or a semantic similarity search with a query embedding.
     *
     * This example uses vectorStore.similaritySearch(collection, query, limit, filter)
     * and vectorStore.searchByFilter(collection, filter) for metadata-only queries.
     *
     * Adjust method names to match your VectorStore API.
     */
    public List<SearchResultRow> search(SearchRequest request) {
        // Build metadata filter map
        Map<String, Object> filter = new HashMap<>();
        if (request.repo() != null) filter.put("repo", request.repo());
        if (request.from() != null || request.to() != null) {
            Map<String, String> range = new HashMap<>();
            if (request.from() != null) range.put("gte", request.from().toString());
            if (request.to() != null) range.put("lte", request.to().toString());
            filter.put("merged_at_range", range);
        }
        if (request.filenames() != null && !request.filenames().isEmpty()) {
            filter.put("change_files", request.filenames());
        }

        // If user provided a semantic query, run similarity search with filter
        List<Document> found;
        if (request.query() != null && !request.query().isBlank()) {
            //found = vectorStore.similaritySearch(collectionName, request.getQuery(), request.getLimit(), filter);
            Filter.Expression expression = buildAndFilterFromMap(filter);
            found = vectorStore.similaritySearch(
                    org.springframework.ai.vectorstore.SearchRequest.builder()
                            .query(request.query())
                            .filterExpression(expression)
                            .topK(request.limit())
                            .similarityThreshold(0.65) //only include relevant chunks
                            .build()
            );
        } else {
            // metadata-only search: fetch points matching filter (vectorStore.searchByFilter)
            //found = vectorStore.searchByFilter(collectionName, filter, request.getLimit());
            Filter.Expression expression = buildAndFilterFromMap(filter);
            found = vectorStore.similaritySearch(
                    org.springframework.ai.vectorstore.SearchRequest.builder()
                            .filterExpression(expression)
                            .topK(request.limit())
                            .similarityThreshold(0.65) //only include relevant chunks
                            .build()
            );

        }

        // Aggregate by pr_number to produce table rows
        Map<Integer, SearchResultRow> agg = new LinkedHashMap<>();
        for (Document doc : found) {
            Map<String, Object> meta = doc.getMetadata();
            Integer prNumber = (Integer) meta.get("pr_number");
            if (prNumber == null) continue;
            String mergedAt = (String) meta.get("merged_at");
            Instant instant = mergedAt != null ? Instant.parse(mergedAt) : null;
            SearchResultRow row = agg.computeIfAbsent(prNumber, k -> {
                SearchResultRow r = new SearchResultRow(
                    prNumber,
                    (String) meta.get("pr_title"),
                    (String) meta.get("author"),
                    (String) meta.get("pr_url"),
                    instant,
                    new ArrayList<>(),
                    (Double) meta.get("")
                );
                return r;
            });

            // merge filenames
            Object filesObj = meta.get("change_files");
            if (filesObj instanceof List) {
                List<?> files = (List<?>) filesObj;
                for (Object f : files) {
                    if (f != null && !row.changeFiles().contains(f.toString())) {
                        row.changeFiles().add(f.toString());
                    }
                }
            } else if (meta.get("filename") != null) {
                String f = (String) meta.get("filename");
                if (!row.changeFiles().contains(f)) {
                    row.changeFiles().add(f);
                }
            }

            // optional: set score if Document contains it
            if (doc.getScore() != null) {
               // row.score(doc.getScore());
            }
        }

        return new ArrayList<>(agg.values());
    }

    private Filter.Expression buildAndFilterFromMap(Map<String, Object> filterMap) {
        if (filterMap == null || filterMap.isEmpty()) {
            return null;
        }

        Filter.Expression combinedExpression = null;

        for (Map.Entry<String, Object> entry : filterMap.entrySet()) {
            // 1. Construct the individual equality segment: key == value
            Filter.Expression currentEquals = new Filter.Expression(
                    Filter.ExpressionType.EQ,
                    new Filter.Key(entry.getKey()),
                    new Filter.Value(entry.getValue())
            );

            // 2. Chain it to the growing filter using an AND operator
            if (combinedExpression == null) {
                combinedExpression = currentEquals;
            } else {
                combinedExpression = new Filter.Expression(
                        Filter.ExpressionType.AND,
                        combinedExpression,
                        currentEquals
                );
            }
        }

        return combinedExpression;
    }

    public PrResponse semanticSearch(String userQuery) {
        // 1. Ask Gemini to extract JSON filters
        String extractionJson = callGeminiForExtraction(userQuery);

        // 2. Parse and validate JSON into ParsedQuery
        ParsedQuery pq = parseAndValidateExtraction(extractionJson);
        pq.setSemanticQuery(userQuery);
        if (null == pq) {
            // fallback: treat whole query as semantic query with no metadata
            pq = new ParsedQuery();
            pq.setSemanticQuery(userQuery);
            pq.setLimit(50);
        }

        // 3. Build metadata filter for Qdrant
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        List<FilterExpressionBuilder.Op> metadataFilter = buildMetadataFilter(b, pq);
        org.springframework.ai.vectorstore.SearchRequest.Builder searchRequest =
                org.springframework.ai.vectorstore.SearchRequest.builder()
                        //.query(pq.getSemanticQuery())
                        .topK(pq.getLimit())
                        .similarityThreshold(0.65); //only include relevant chunks
        if (!metadataFilter.isEmpty()) {
            FilterExpressionBuilder.Op combined = metadataFilter.get(0);
            for (int i = 0; i < metadataFilter.size(); i++) {
                combined = b.and(combined, metadataFilter.get(i));
            }
            searchRequest.filterExpression(combined.build());
        }

        // 4. Run vector search (semantic if semanticQuery present, else metadata-only)
        List<Document> found;
        if (StringUtils.hasText(pq.getSemanticQuery())) {
            //found = vectorStore.similaritySearch(collectionName, pq.semanticQuery, pq.limit, metadataFilter);
            found = vectorStore.similaritySearch(searchRequest.query(pq.getSemanticQuery()).build());
        } else {
            //found = vectorStore.searchByFilter(collectionName, metadataFilter, pq.limit);
            found = vectorStore.similaritySearch(searchRequest.build());
        }

        // 5. Default response when semantic search does not yield results
        if (found.isEmpty()) {
            String message = """
                    I'm sorry, but the requested information is not available in my current database. 
                    I am configured to only answer questions using verified source documents, and no matching records were found.
                    """;
            PrResponse prResponse = new PrResponse();
            prResponse.setMessage(message);
            return prResponse;
        }

        // 6. Aggregate to PR-level rows
        List<SearchResultRow> rows = aggregateToRows(found);

        // 7. Call Gemini to format results into a strict markdown table or list
        // this method was added for testing, now directly response to UI which does the formatting
        //String formatted = callGeminiForFormatting(userQuery, rows);

        // 7. Return formatted string (markdown)
        PrResponse prResponse = new PrResponse();
        prResponse.setSearchResultRow(rows);
        return prResponse;

    }

    // Extraction call
    public String callGeminiForExtraction(String question) {

        // Inject current date so the model can resolve relate terms.
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String system = """
                You are a JSON extractor. Today's date is %s.
                Return ONLY a single JSON object with keys: repo, from, to, filenames, author, semanticQuery.
                Use ISO dates YYYY-MM-DD or full ISO for from and to.
                If a field is absent in the query, set it to null or an empty array.
                Do not include any explanation, markdown, commentary, or code block. Return raw JSON only.
                """.formatted(today);

        String user = "Extract filters from this query and return JSON only.\n\nInput: \"" + escapeForPrompt(question) + "\"";
        logger.info("-------------------------------------");
        logger.info("Prompt to LLM {}", user);
        String llmResponse = chatClient.prompt().system(system).user(user).call().content();
        logger.info("LLM response {}", llmResponse);

        logger.info("-------------------------------------");
        return  llmResponse;
    }

    // Parse and validate extraction
    private ParsedQuery parseAndValidateExtraction(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            ParsedQuery pq = new ParsedQuery();
            pq.setRepo(node.hasNonNull("repo") ? node.get("repo").asText(null) : null);
            pq.setAuthor(node.hasNonNull("author") ? node.get("author").asText(null) : null);
            pq.setSemanticQuery(node.hasNonNull("semanticQuery") ? node.get("semanticQuery").asText(null) : null);

            if (node.has("filenames") && node.get("filenames").isArray()) {
                List<String> files = new ArrayList<>();
                for (JsonNode f : node.get("filenames")) files.add(f.asText());
                pq.setFilenames(files);
            }

            if (node.hasNonNull("from")) {
                pq.setFrom(parseToInstant(node.get("from").asText()));
            }
            if (node.hasNonNull("to")) {
                pq.setTo(parseToInstant(node.get("to").asText()));
            }

            pq.setLimit(50);
            // Basic validation: if dates provided ensure from <= to
            if (pq.getFrom() != null && pq.getTo() != null && pq.getTo().isAfter(pq.getTo())) {
                // swap or reject; here we swap
                Instant tmp = pq.getFrom();
                pq.setFrom(pq.getTo());
                pq.setTo(tmp);
            }
            return pq;
        } catch (Exception ex) {
            logger.error("Exception constructing JSON search query. Exception = {}", ex.getMessage());
            // invalid JSON or parse error -> return null to trigger fallback
            return null;
        }
    }

    // Build metadata filter for Qdrant via VectorStore
    private List<FilterExpressionBuilder.Op> buildMetadataFilter(FilterExpressionBuilder b, ParsedQuery pq) {
        List<FilterExpressionBuilder.Op> conditions = new ArrayList<>();
        if (StringUtils.hasText(pq.getRepo())) {
            conditions.add(b.eq("repo", pq.getRepo()));
        }
        if (StringUtils.hasText(pq.getAuthor())) {
            conditions.add(b.eq("author", pq.getAuthor()));
        }
        if (pq.getFrom() != null || pq.getTo() != null) {
            if (pq.getFrom() != null) {

                conditions.add(b.gte("merged_at_milli", Long.valueOf(pq.getFrom().getEpochSecond()).intValue()));
            }
            if (pq.getTo() != null) {
                conditions.add(b.lte("merged_at_milli", Long.valueOf(pq.getTo().getEpochSecond()).intValue()));
            }
        }
        if (pq.getFilenames() != null && !pq.getFilenames().isEmpty()) {
            // match any filename by adding a must clause per filename
            for (String f : pq.getFilenames()) {
                conditions.add(b.eq("filename", f));
            }
        }
        return conditions;

    }

    // Aggregate Document results into PR rows
    private List<SearchResultRow> aggregateToRows(List<Document> docs) {
        Map<Integer, SearchResultRow> agg = new LinkedHashMap<>();
        for (Document doc : docs) {
            Map<String, Object> meta = doc.getMetadata();
            if (meta == null) {
                continue;
            }
            Integer prNumber = extractPrNumber(meta.get("pr_number"));
            if (prNumber == null) {
                continue;
            }

            String mergedAt = (String) meta.get("merged_at");
            Instant instant = mergedAt != null ? Instant.parse(mergedAt) : null;
            SearchResultRow row = agg.computeIfAbsent(prNumber, k -> {
                SearchResultRow r = new SearchResultRow(
                        prNumber,
                        (String) meta.get("pr_title"),
                        (String) meta.get("author"),
                        (String) meta.get("pr_url"),
                        instant,
                        new ArrayList<>(),
                        (Double) meta.get("")
                );
                return r;
            });

            Object filesObj = meta.get("change_files");
            if (filesObj instanceof List) {
                for (Object f : (List<?>) filesObj) {
                    if (f != null && !row.changeFiles().contains(f.toString())) row.changeFiles().add(f.toString());
                }
            } else if (meta.get("filename") != null) {
                String f = (String) meta.get("filename");
                if (!row.changeFiles().contains(f)) row.changeFiles().add(f);
            }

            // optional: set score if Document contains it
            if (doc.getScore() != null) {
                //row.score(doc.getScore());
            }
        }
        return new ArrayList<>(agg.values());
    }

    // Formatting call to Gemini
    private String callGeminiForFormatting(String userQuery, List<SearchResultRow> rows) {
        // Build compact context for LLM
        StringBuilder context = new StringBuilder();
        for (SearchResultRow r : rows) {
            context.append("PR: ").append(r.prNumber()).append("\n")
                    .append("Title: ").append(safe(r.prTitle())).append("\n")
                    .append("Author: ").append(safe(r.author())).append("\n")
                    .append("MergedAt: ").append(r.mergedAt() != null ? r.mergedAt().toString() : "").append("\n")
                    .append("Files: ").append(String.join(", ", r.changeFiles() != null ? r.changeFiles() : Collections.emptyList())).append("\n")
                    .append("URL: ").append(safe(r.prUrl())).append("\n---\n");
        }

        String system = "You are a concise formatter. Return ONLY a markdown table with columns: PR, Author, Files, Merged At, PR URL. "
                + "Do not include headings, commentary, or extra text. If no rows, return the table header only.";

        String user = "User query: \"" + escapeForPrompt(userQuery) + "\"\n\nPR records:\n" + context.toString()
                + "\nProduce the markdown table now.";

        String llmResponse = chatClient.prompt().system(system).user(user).call().content();
        logger.info("Final LLM response {}", llmResponse);
        return llmResponse;
    }


    // Utilities
    private Instant parseToInstant(String date) {
        if (date == null) {
            return null;
        }
        date = date.trim();
        try {
            return Instant.parse(date);
        } catch (DateTimeParseException e) {
            logger.error("Error parsing input string date={}, exception={}", date, e.getMessage());
        }
        try {
            LocalDate d = LocalDate.parse(date);
            return d.atTime(23, 59, 59).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            logger.error("Error parsing input string date={}, exception={}", date, e.getMessage());
        }
        return null;
    }

    private Integer extractPrNumber(Object o) {
        if (o == null) return null;
        if (o instanceof Integer) return (Integer) o;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return null; }
    }

    private String safe(String s) { return s == null ? "" : s.replaceAll("\\r?\\n", " "); }

    private String fallbackMarkdownTable(List<SearchResultRow> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("| PR | Author | Files | Merged At | PR URL |\n");
        sb.append("|---|---|---|---|---|\n");
        for (SearchResultRow r : rows) {
            sb.append("| ").append(r.prNumber()).append(" | ")
                    .append(escapePipe(safe(r.author()))).append(" | ")
                    .append(escapePipe(String.join(", ", r.changeFiles() != null ? r.changeFiles() : Collections.emptyList()))).append(" | ")
                    .append(r.mergedAt() != null ? r.mergedAt().toString() : "").append(" | ")
                    .append(escapePipe(safe(r.prUrl()))).append(" |\n");
        }
        return sb.toString();
    }

    private String escapePipe(String s) { return s == null ? "" : s.replace("|", "\\|"); }

    private String escapeForPrompt(String s) { return s == null ? "" : s.replace("\"", "\\\""); }

}
