package com.example.agent.bootstrap;

import com.example.agent.knowledge.RuleStore;
import com.example.agent.providers.LlmProvider;
import com.example.agent.rag.SimpleIndexer;
import com.example.agent.rules.RuleLoaderV2;
import com.example.agent.rules.RuleV2;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class LearnerV2 {
    private final LlmProvider llm;
    private final RuleStore processedCache;
    private final RuleLoaderV2 repo;
    private final SimpleIndexer indexer;
    private final ObjectMapper mapper = new ObjectMapper();

    public LearnerV2(LlmProvider llm, RuleStore processedCache, RuleLoaderV2 repo, SimpleIndexer indexer) {
        this.llm = llm;
        this.processedCache = processedCache;
        this.repo = repo;
        this.indexer = indexer;
    }

    public void learnFromRepo(Path root, List<String> includeExts) throws IOException {
        try (var walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> includeExts.stream().anyMatch(ext -> p.toString().endsWith(ext)))
                    .forEach(p -> {
                        try {
                            if (processedCache.isProcessed(root, p)) return;
                            String text = Files.readString(p);
                            indexer.addDocument(text);
                            String sample = sample(text, 2000);
                            String prompt = buildPrompt(sample);
                            String jsonl = llm.chat(List.of(
                                    Map.of("role", "system", "content", "Верни только JSONL RuleV2, по одному объекту на строку."),
                                    Map.of("role", "user", "content", prompt)
                            ), 0.2);
                            for (String line : jsonl.split("\r?\n")) {
                                String s = line.trim();
                                if (s.isEmpty()) continue;
                                try {
                                    RuleV2 r = mapper.readValue(s, RuleV2.class);
                                    if (r.type == null || r.type.isBlank()) r.type = "stmt";
                                    if (r.priority == 0) r.priority = defaultPriority(r.type);
                                    repo.addOrMerge(r);
                                } catch (Exception ignored) {
                                }
                            }
                            processedCache.markProcessed(root, p);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
        repo.save();
        processedCache.save();
    }

    private String sample(String text, int maxChars) {
        return text.substring(0, Math.min(maxChars, text.length()));
    }

    private int defaultPriority(String t) {
        if (t == null) return 1;
        t = t.toLowerCase();
        return switch (t) {
            case "block" -> 50;
            case "stmt" -> 20;
            case "segment" -> 10;
            case "rewrite" -> 5;
            default -> 1;
        };
    }

    private String buildPrompt(String sample) {
        return "Проанализируй фрагмент диалекта и предложи правила RuleV2 (segment|block|stmt|rewrite) в JSONL. " +
                "Для segment укажи strategy и regex. Для block — open/middle[]/close и irType. " +
                "Для stmt — irType и fields/listFields. Для rewrite — pattern/replace. " +
                "Верни только JSONL.\n\nФрагмент:\n" + sample;
    }
}
