package com.example.agent.api;

import com.example.agent.bootstrap.LearnerV2;
import com.example.agent.config.Config;
import com.example.agent.knowledge.RuleStore;
import com.example.agent.providers.GigaChatCertificateClient;
import com.example.agent.providers.GigaChatOpenAIClient;
import com.example.agent.providers.LlmProvider;
import com.example.agent.rag.SimpleIndexer;
import com.example.agent.rules.RuleLoaderV2;
import com.example.agent.translate.TranslatorAgent;

import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Objects;

public class TranslatorEngine implements TranslatorApi {
    private final LlmProvider llm;
    private final RuleStore processed;
    private final SimpleIndexer indexer;
    private final RuleLoaderV2 ruleRepo;

    public TranslatorEngine(LlmProvider llm, Path runtimeDir) throws IOException {
        this.llm = Objects.requireNonNull(llm);
        this.processed = new RuleStore(Objects.requireNonNull(runtimeDir));
        this.indexer = new SimpleIndexer();
        this.ruleRepo = new RuleLoaderV2(runtimeDir);
    }

    public static TranslatorEngine fromEnv(Path runtimeDir) throws IOException {
        var cfg = Config.fromEnv();
        LlmProvider llm;
        if (cfg.certPath == null || cfg.certPath.isBlank() ||
            cfg.keyPath == null || cfg.keyPath.isBlank() ||
            cfg.caPath == null || cfg.caPath.isBlank()) {
            if (cfg.caPath != null && !cfg.caPath.isBlank()) {
                System.setProperty("GIGACHAT_CA_FILE", cfg.caPath);
            }
            llm = new GigaChatOpenAIClient(cfg.apiBase, cfg.apiKey, cfg.model);
        } else {
            try {
                llm = new GigaChatCertificateClient(cfg.apiBase, cfg.model,
                        Path.of(cfg.certPath), Path.of(cfg.keyPath), Path.of(cfg.caPath));
            } catch (GeneralSecurityException e) {
                throw new IOException(e);
            }
        }
        return new TranslatorEngine(llm, runtimeDir);
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
        return out;
    }

    @Override
    public String fix(String dialectSource, String currentJava, String feedback) throws IOException {
        var agent = new TranslatorAgent(llm, indexer, processed, ruleRepo);
        String out = agent.applyUserFix(dialectSource, currentJava, feedback);
        processed.save();
        return out;
    }
}
