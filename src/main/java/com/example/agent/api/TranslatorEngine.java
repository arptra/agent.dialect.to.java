package com.example.agent.api;

import com.example.agent.bootstrap.LearnerV2;
import com.example.agent.config.Config;
import com.example.agent.knowledge.RuleStore;
import com.example.agent.providers.GigaChatOpenAIClient;
import com.example.agent.rag.SimpleIndexer;
import com.example.agent.rules.RuleLoaderV2;
import com.example.agent.translate.TranslatorAgent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class TranslatorEngine implements TranslatorApi {
    private final GigaChatOpenAIClient llm;
    private final RuleStore processed;
    private final SimpleIndexer indexer;
    private final RuleLoaderV2 ruleRepo;

    public TranslatorEngine(String apiBase, String apiKey, String model, Path runtimeDir) throws IOException {
        this.llm = new GigaChatOpenAIClient(Objects.requireNonNull(apiBase), Objects.requireNonNull(apiKey), Objects.requireNonNull(model));
        this.processed = new RuleStore(Objects.requireNonNull(runtimeDir));
        this.indexer = new SimpleIndexer();
        this.ruleRepo = new RuleLoaderV2(runtimeDir);
    }

    public static TranslatorEngine fromEnv(Path runtimeDir) throws IOException {
        var cfg = Config.fromEnv();
        return new TranslatorEngine(cfg.apiBase, cfg.apiKey, cfg.model, runtimeDir);
    }

    @Override
    public void learn(Path repoRoot, List<String> includeExts) throws IOException {
        new LearnerV2(llm, processed, ruleRepo, indexer).learnFromRepo(repoRoot, includeExts);
    }

    @Override
    public String translate(String dialectSource) throws IOException {
        var agent = new TranslatorAgent(llm, indexer, processed, ruleRepo);
        String out = agent.translate(dialectSource);
        processed.save();
        ruleRepo.save();
        return out;
    }

    @Override
    public String fix(String dialectSource, String currentJava, String feedback) throws IOException {
        var agent = new TranslatorAgent(llm, indexer, processed, ruleRepo);
        String out = agent.applyUserFix(dialectSource, currentJava, feedback);
        processed.save();
        ruleRepo.save();
        return out;
    }
}
