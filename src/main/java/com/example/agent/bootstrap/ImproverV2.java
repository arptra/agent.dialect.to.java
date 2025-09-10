package com.example.agent.bootstrap;

import com.example.agent.providers.GigaChatOpenAIClient;
import com.example.agent.rules.RuleLoaderV2;
import com.example.agent.rules.RuleV2;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ImproverV2 {
  private final GigaChatOpenAIClient llm;
  private final RuleLoaderV2 repo;
  private final ObjectMapper mapper = new ObjectMapper();

  public ImproverV2(GigaChatOpenAIClient llm, RuleLoaderV2 repo) {
    this.llm = llm; this.repo = repo;
  }

  public void refine(String dialectSnippet, String javaResult, String diagnosticsOrFeedback) throws IOException {
    String prompt = "На основе пары (диалект → Java) предложи улучшения или новые RuleV2 " +
      "('segment'|'block'|'stmt'|'rewrite') в формате JSONL. " +
      "Не дублируй существующие правила, улучшай устойчивость.\n\n" +
      "Диалект:\n" + dialectSnippet + "\n\nJava:\n" + javaResult +
      "\n\nДиагностика/фидбек:\n" + diagnosticsOrFeedback + "\n\nТолько JSONL.";
    String jsonl = llm.chat(List.of(
      Map.of("role","system","content","Возвращай только JSONL, один объект на строку."),
      Map.of("role","user","content", prompt)
    ), 0.2);
    for (String line : jsonl.split("\r?\n")) {
      String s = line.trim(); if (s.isEmpty()) continue;
      try {
        RuleV2 r = mapper.readValue(s, RuleV2.class);
        if (r.type == null || r.type.isBlank()) continue;
        repo.addOrMerge(r);
      } catch (Exception ignored) {}
    }
    repo.save();
  }
}
