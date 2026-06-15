package com.example.assistant.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static javax.xml.crypto.dsig.SignatureMethod.HMAC_SHA256;

@RestController
@RequestMapping("/webhook/github")
public class GitWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(GitWebhookController.class);

    @PostMapping("/pull-request")
    public ResponseEntity<String> handleWebhook(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-Github-Event", required = false) String event,
            @RequestBody String payload) {
        logger.info("Got data from Github signature={} , event={} , payload={}", signature, event, payload);

        String sign = "sha256=" + computeSHA(payload);
        boolean isSignMatch = constantTimeEquals(sign, signature);

        logger.info("Sign matched is {}", isSignMatch);
        return ResponseEntity.ok("Recieved");
    }

    public String computeSHA(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec("encryption".getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        }
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
}
