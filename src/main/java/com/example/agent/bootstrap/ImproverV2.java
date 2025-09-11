package com.example.agent.bootstrap;

import com.example.agent.providers.LlmProvider;
import com.example.agent.rules.RuleLoaderV2;
import com.example.agent.rules.RuleV2;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ImproverV2 {
  private final LlmProvider llm;
  private final RuleLoaderV2 repo;
  private final ObjectMapper mapper = new ObjectMapper();

  public ImproverV2(LlmProvider llm, RuleLoaderV2 repo) {
    this.llm = llm; this.repo = repo;
  }

  public void refine(String dialectSnippet, String javaResult, String diagnosticsOrFeedback) throws IOException {
    String prompt = "На основе пары (диалект → Java) предложи новые или улучшенные RuleV2.\n" +
            "Не повторяй уже существующие правила (меняй id или улучшай regex)." +
      "Диалект:\n" + dialectSnippet + "\n\nJava:\n" + javaResult +
      "\n\nДиагностика/фидбек:\n" + diagnosticsOrFeedback + "\n\nТолько JSONL.";
    String jsonl = llm.chat(List.of(
      Map.of("role","system","content","Верни ТОЛЬКО JSONL RuleV2. Одна строка = один объект JSON.\n" +
              "Разрешённые ключи: id,type,regex,strategy,irType,fields,listFields,open,middle,close,pattern,replace,priority\n" +
              "Запрещены любые другие ключи (name, version и т.п.).\n" +
              "Для type=stmt ОБЯЗАТЕЛЬНЫ irType и regex с ^ и $.\n" +
              "Для type=block ОБЯЗАТЕЛЬНЫ open и close (оба с якорями), irType; middle опционален.\n" +
              "Для type=segment ОБЯЗАТЕЛЬНЫ strategy и regex.\n" +
              "Для type=rewrite ОБЯЗАТЕЛЬНЫ pattern и replace.\n" +
              "6–12 правил, без дубликатов."),
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
