package com.example.assistant.controller;

import com.example.assistant.model.PrRecord;
import com.example.assistant.model.SearchRequest;
import com.example.assistant.model.SearchResultRow;
import com.example.assistant.service.GitWebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

@RestController
@RequestMapping("/webhook/github")
public class GitWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(GitWebhookController.class);

    private final GitWebhookService gitWebhookService;

    public GitWebhookController(GitWebhookService gitWebhookService) {
        this.gitWebhookService = gitWebhookService;
    }

    @PostMapping("/pull-request")
    public ResponseEntity<String> handleWebhook(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-Github-Event", required = false) String event,
            @RequestBody String payload) {
        logger.info("Got data from Github signature={} , event={} , payload={}", signature, event, payload);

        // Step 1: Validate event type, only pull request events
        if (!"pull_request".equalsIgnoreCase(event)) {
            logger.info("Unsupported Github event type {}", event);
            return ResponseEntity.badRequest().body("Unsupported event type : " + event);
        }

        // Step 2: Verify signature
        if (!gitWebhookService.isValidSignature(payload, signature)) {
            logger.error("Webhook signature validaton error!!");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }

        // Step 3: Parse payload
        PrRecord prRecord;
        try {
            prRecord = gitWebhookService.parseRawPayload(payload);
        } catch (Exception e) {
            logger.error("Failed to parse PR payload. Exception = {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid payload format. Exception = " + e.getMessage());
        }

        // Step 4: Validate PR state
        if (null == prRecord) {
            logger.info("PR is not yet in processable state, skipping it.");
            return ResponseEntity.ok("Ignoring non-processable PR");
        }

        // Step 5: Index PR to vector store
        gitWebhookService.indexPr(prRecord);


        return ResponseEntity.ok("PR is processed successfully");
    }

    /*@GetMapping("/pull-test")
    public ResponseEntity<String> handleWebhook() {
        String sign = "sha256=5a63914bf234914198bbfc554ffadcd13734f6f9becdaad824f4daf5643e1c10";
        String payload = "{\"zen\":\"Responsive is better than fast.\",\"hook_id\":642019963,\"hook\":{\"type\":\"Repository\",\"id\":642019963,\"name\":\"web\",\"active\":true,\"events\":[\"pull_request\"],\"config\":{\"content_type\":\"json\",\"insecure_ssl\":\"1\",\"secret\":\"********\",\"url\":\"https://ai-policy-assistant-doe8.onrender.com/webhook/github/pull-request\"},\"updated_at\":\"2026-06-15T12:40:03Z\",\"created_at\":\"2026-06-15T12:40:03Z\",\"url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/hooks/642019963\",\"test_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/hooks/642019963/test\",\"ping_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/hooks/642019963/pings\",\"deliveries_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/hooks/642019963/deliveries\",\"last_response\":{\"code\":null,\"status\":\"unused\",\"message\":null}},\"repository\":{\"id\":1261681974,\"node_id\":\"R_kgDOSzO9Ng\",\"name\":\"spring-cloud-service-deploy\",\"full_name\":\"Ravi-Asgr/spring-cloud-service-deploy\",\"private\":false,\"owner\":{\"login\":\"Ravi-Asgr\",\"id\":89694475,\"node_id\":\"MDQ6VXNlcjg5Njk0NDc1\",\"avatar_url\":\"https://avatars.githubusercontent.com/u/89694475?v=4\",\"gravatar_id\":\"\",\"url\":\"https://api.github.com/users/Ravi-Asgr\",\"html_url\":\"https://github.com/Ravi-Asgr\",\"followers_url\":\"https://api.github.com/users/Ravi-Asgr/followers\",\"following_url\":\"https://api.github.com/users/Ravi-Asgr/following{/other_user}\",\"gists_url\":\"https://api.github.com/users/Ravi-Asgr/gists{/gist_id}\",\"starred_url\":\"https://api.github.com/users/Ravi-Asgr/starred{/owner}{/repo}\",\"subscriptions_url\":\"https://api.github.com/users/Ravi-Asgr/subscriptions\",\"organizations_url\":\"https://api.github.com/users/Ravi-Asgr/orgs\",\"repos_url\":\"https://api.github.com/users/Ravi-Asgr/repos\",\"events_url\":\"https://api.github.com/users/Ravi-Asgr/events{/privacy}\",\"received_events_url\":\"https://api.github.com/users/Ravi-Asgr/received_events\",\"type\":\"User\",\"user_view_type\":\"public\",\"site_admin\":false},\"html_url\":\"https://github.com/Ravi-Asgr/spring-cloud-service-deploy\",\"description\":null,\"fork\":false,\"url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy\",\"forks_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/forks\",\"keys_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/keys{/key_id}\",\"collaborators_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/collaborators{/collaborator}\",\"teams_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/teams\",\"hooks_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/hooks\",\"issue_events_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/issues/events{/number}\",\"events_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/events\",\"assignees_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/assignees{/user}\",\"branches_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/branches{/branch}\",\"tags_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/tags\",\"blobs_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/git/blobs{/sha}\",\"git_tags_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/git/tags{/sha}\",\"git_refs_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/git/refs{/sha}\",\"trees_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/git/trees{/sha}\",\"statuses_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/statuses/{sha}\",\"languages_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/languages\",\"stargazers_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/stargazers\",\"contributors_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/contributors\",\"subscribers_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/subscribers\",\"subscription_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/subscription\",\"commits_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/commits{/sha}\",\"git_commits_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/git/commits{/sha}\",\"comments_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/comments{/number}\",\"issue_comment_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/issues/comments{/number}\",\"contents_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/contents/{+path}\",\"compare_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/compare/{base}...{head}\",\"merges_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/merges\",\"archive_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/{archive_format}{/ref}\",\"downloads_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/downloads\",\"issues_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/issues{/number}\",\"pulls_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/pulls{/number}\",\"milestones_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/milestones{/number}\",\"notifications_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/notifications{?since,all,participating}\",\"labels_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/labels{/name}\",\"releases_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/releases{/id}\",\"deployments_url\":\"https://api.github.com/repos/Ravi-Asgr/spring-cloud-service-deploy/deployments\",\"created_at\":\"2026-06-07T02:40:05Z\",\"updated_at\":\"2026-06-07T05:54:34Z\",\"pushed_at\":\"2026-06-07T05:54:30Z\",\"git_url\":\"git://github.com/Ravi-Asgr/spring-cloud-service-deploy.git\",\"ssh_url\":\"git@github.com:Ravi-Asgr/spring-cloud-service-deploy.git\",\"clone_url\":\"https://github.com/Ravi-Asgr/spring-cloud-service-deploy.git\",\"svn_url\":\"https://github.com/Ravi-Asgr/spring-cloud-service-deploy\",\"homepage\":null,\"size\":11,\"stargazers_count\":0,\"watchers_count\":0,\"language\":\"Dockerfile\",\"has_issues\":true,\"has_projects\":true,\"has_downloads\":true,\"has_wiki\":true,\"has_pages\":false,\"has_discussions\":false,\"forks_count\":0,\"mirror_url\":null,\"archived\":false,\"disabled\":false,\"open_issues_count\":0,\"license\":{\"key\":\"apache-2.0\",\"name\":\"Apache License 2.0\",\"spdx_id\":\"Apache-2.0\",\"url\":\"https://api.github.com/licenses/apache-2.0\",\"node_id\":\"MDc6TGljZW5zZTI=\"},\"allow_forking\":true,\"is_template\":false,\"web_commit_signoff_required\":false,\"has_pull_requests\":true,\"pull_request_creation_policy\":\"all\",\"topics\":[],\"visibility\":\"public\",\"forks\":0,\"open_issues\":0,\"watchers\":0,\"default_branch\":\"main\"},\"sender\":{\"login\":\"Ravi-Asgr\",\"id\":89694475,\"node_id\":\"MDQ6VXNlcjg5Njk0NDc1\",\"avatar_url\":\"https://avatars.githubusercontent.com/u/89694475?v=4\",\"gravatar_id\":\"\",\"url\":\"https://api.github.com/users/Ravi-Asgr\",\"html_url\":\"https://github.com/Ravi-Asgr\",\"followers_url\":\"https://api.github.com/users/Ravi-Asgr/followers\",\"following_url\":\"https://api.github.com/users/Ravi-Asgr/following{/other_user}\",\"gists_url\":\"https://api.github.com/users/Ravi-Asgr/gists{/gist_id}\",\"starred_url\":\"https://api.github.com/users/Ravi-Asgr/starred{/owner}{/repo}\",\"subscriptions_url\":\"https://api.github.com/users/Ravi-Asgr/subscriptions\",\"organizations_url\":\"https://api.github.com/users/Ravi-Asgr/orgs\",\"repos_url\":\"https://api.github.com/users/Ravi-Asgr/repos\",\"events_url\":\"https://api.github.com/users/Ravi-Asgr/events{/privacy}\",\"received_events_url\":\"https://api.github.com/users/Ravi-Asgr/received_events\",\"type\":\"User\",\"user_view_type\":\"public\",\"site_admin\":false}}";
        String shaString =  gitWebhookService.computeSHA(payload);
        return ResponseEntity.ok("Recieved");
    }*/


    /**
     * Search endpoint for user prompts. Accepts metadata filters and optional semantic query.
     */
    @PostMapping("/search")
    public ResponseEntity<String> search(@RequestBody String query) {
        String llmResponse = gitWebhookService.semanticSearch(query);
        return ResponseEntity.ok(llmResponse);
    }

    @GetMapping("/extraction")
    public String testExtraction(@RequestParam(name="q") String question) {
        gitWebhookService.callGeminiForExtraction(question);
        return "Done";
    }
}
