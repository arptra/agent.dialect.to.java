package com.example.agent.providers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class GigaChatOpenAIClient implements LlmProvider {

    private final String baseUrl; // e.g. http://localhost:8000
    private final String apiKey;
    private final String model;
    private final OkHttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private String accessToken;
    private long tokenExpiresAt;

    public GigaChatOpenAIClient(String baseUrl, String apiKey, String model) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length()-1) : baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.http = new OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(60))
                .build();
    }

    private String getToken() throws IOException {
        if (accessToken != null && System.currentTimeMillis() < tokenExpiresAt) {
            return accessToken;
        }
        String url = baseUrl + "/api/v1/tokens";
        RequestBody body = RequestBody.create(
                "scope=GIGACHAT_API_PERS",
                MediaType.parse("application/x-www-form-urlencoded")
        );
        Request req = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Basic " + apiKey)
                .post(body)
                .build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("GigaChat token error: " + resp.code() + " " + resp.message());
            }
            JsonNode json = mapper.readTree(resp.body().bytes());
            accessToken = json.get("access_token").asText();
            if (json.has("expires_at")) {
                tokenExpiresAt = json.get("expires_at").asLong() * 1000L;
            } else if (json.has("expires_in")) {
                tokenExpiresAt = System.currentTimeMillis() + json.get("expires_in").asLong() * 1000L;
            } else {
                tokenExpiresAt = System.currentTimeMillis() + 30 * 60 * 1000L;
            }
            return accessToken;
        }
    }

    @Override
    public String chat(List<Map<String, String>> messages, double temperature) throws IOException {
        String url = baseUrl + "/v1/chat/completions";
        var payload = Map.of(
                "model", model,
                "messages", messages,
                "temperature", temperature
        );
        RequestBody body = RequestBody.create(
                mapper.writeValueAsBytes(payload),
                MediaType.parse("application/json")
        );
        String token = getToken();
        Request req = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .post(body)
                .build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("GigaChat API error: " + resp.code() + " " + resp.message() + " body=" + (resp.body() != null ? resp.body().string() : ""));
            }
            JsonNode json = mapper.readTree(resp.body().bytes());
            return json.get("choices").get(0).get("message").get("content").asText();
        }
    }
}
