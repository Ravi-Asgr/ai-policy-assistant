package com.example.assistant.service;

import com.example.assistant.model.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PolicyChatService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private static final Logger logger = LoggerFactory.getLogger(PolicyChatService.class);

    public PolicyChatService(ChatModel chatModel, VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        this.chatClient = ChatClient.create(chatModel);
        /*this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem("""
                You are a helpful HR Policy Assistant for company employees.
                Answer general questions directly from your knowledge.
                Only use the provided policy content when the question is about
                specific company policies (leave, salary, bonus, recruitment, exit).
                If context is provided, cite the source document and page number.
                If you don't know, say so clearly.
                """)
                .build();
         */
    }

    public ChatResponse chat(String query, String department) {

        //step 1: Decide if semantic search is needed
        boolean needsPolicyContext = isPolicyQuestion(query);

        String context = "";
        List<String> sources = new ArrayList<>();

        if(needsPolicyContext) {
            logger.info("Policy question detected - performing semantic search");

            String filter = department != null && !department.isBlank()
                    ? String.format("doc_type == 'policy' && department == '%s'", department)
                    : "doc_type == 'policy'";

            List<Document> results = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(4)
                            .filterExpression(filter)
                            .similarityThreshold(0.65) //only include relevant chunks
                            .build()
            );

            if (!results.isEmpty()) {
                context = results.stream()
                        .map(doc -> {
                            String src = doc.getMetadata().get("file_name") + " (Page "
                                    + doc.getMetadata().get("page_number") + ")";
                            sources.add(src);
                            return "[Source: " + src + "]\n" + doc.getText();
                })
                        .collect(Collectors.joining("\n\n---\n\n"));
            }
        }

        //Step 2: Build prompt - with or without context
        String finalContext = context;
        String answer = chatClient.prompt()
                .user(u -> {
                    if (!finalContext.isBlank()) {
                        u.text("Policy Context:\n{context}\n\nEmployee Question: {question}")
                                .param("context", finalContext)
                                .param("question", query);
                    } else {
                        u.text("{question}").param("question", query);
                    }
                })
                .call()
                .content();
        return new com.example.assistant.model.ChatResponse(answer, sources, needsPolicyContext);
    }

    /* Keyword check, avoids unnecessary vector DB calls for general questions */
    private boolean isPolicyQuestion(String query) {
        String lower = query.toLowerCase();
        List<String> policyKeyWords = List.of(
                "leave", "vacation", "sick", "maternity", "paternity",
                "salary", "bonus", "increment", "appraisal", "hike",
                "recruitment", "hiring", "onboarding", "joining", "exit",
                "resignation", "notice period", "relieving",
                "advance", "loan", "reimbursement", "allowance",
                "policy", "rule", "procedure", "eligible", "entitlement"
        );
        return policyKeyWords.stream().anyMatch(lower::contains);
    }

    public Flux<String> chatStream(String query, String department) {

        //step 1: Decide if semantic search is needed
        boolean needsPolicyContext = isPolicyQuestion(query);

        String context = "";
        List<String> sources = new ArrayList<>();

        if(needsPolicyContext) {
            logger.info("Policy question detected - performing semantic search");

            String filter = department != null && !department.isBlank()
                    ? String.format("doc_type == 'policy' && department == '%s'", department)
                    : "doc_type == 'policy'";

            List<Document> results = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(4)
                            .filterExpression(filter)
                            .similarityThreshold(0.65) //only include relevant chunks
                            .build()
            );

            if (!results.isEmpty()) {
                context = results.stream()
                        .map(doc -> {
                            String src = doc.getMetadata().get("file_name") + " (Page "
                                    + doc.getMetadata().get("page_number") + ")";
                            sources.add(src);
                            return "[Source: " + src + "]\n" + doc.getText();
                        })
                        .collect(Collectors.joining("\n\n---\n\n"));
            }
        }

        //Step 2: Build prompt - with or without context
        String finalContext = context;
        return chatClient.prompt()
                .user(u -> {
                    if (!finalContext.isBlank()) {
                        u.text("Policy Context:\n{context}\n\nEmployee Question: {question}")
                                .param("context", finalContext)
                                .param("question", query);
                    } else {
                        u.text("{question}").param("question", query);
                    }
                })
                .stream()
                .content();
        //return new com.example.assistant.model.ChatResponse(answer, sources, needsPolicyContext);
    }
}
