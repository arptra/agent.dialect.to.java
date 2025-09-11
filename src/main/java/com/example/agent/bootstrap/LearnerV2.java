package com.example.agent.bootstrap;

import com.example.agent.knowledge.RuleStore;
import com.example.agent.providers.LlmProvider;
import com.example.agent.rag.SimpleIndexer;
import com.example.agent.rules.RuleJsonlCoercer;
import com.example.agent.rules.RuleLoaderV2;
import com.example.agent.rules.RuleV2;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class LearnerV2 {

  private final LlmProvider llm;
  private final RuleStore processedCache; // tracks processed files
  private final RuleLoaderV2 repo;
  private final SimpleIndexer indexer;

  public LearnerV2(LlmProvider llm, RuleStore processedCache, RuleLoaderV2 repo, SimpleIndexer indexer) {
    this.llm = llm; this.processedCache = processedCache; this.repo = repo; this.indexer = indexer;
  }

  public void learnFromRepo(Path root, List<String> includeExts) throws IOException {
    int scanned = 0, matched = 0, added = 0;

    try (var walk = Files.walk(root)) {
      for (Path p : (Iterable<Path>) walk::iterator) {
        if (!Files.isRegularFile(p)) continue;
        scanned++;
        if (includeExts.stream().noneMatch(ext -> p.toString().endsWith(ext))) continue;
        matched++;

        if (processedCache.isProcessed(root, p)) continue;

        String text = Files.readString(p);
        indexer.addDocument(text);
        String sample = sample(text, 1500);

        // ----- First attempt: strict schema + examples
        String sys = strictSystemPrompt();
        String user = strictUserPrompt(sample);
        String raw = llm.chat(List.of(
            Map.of("role","system","content", sys),
            Map.of("role","user","content", user)
        ), 0.0);

        List<RuleV2> rules = RuleJsonlCoercer.parse(raw);

        // ----- If nothing parsed, retry with correction and echo of invalid output
        if (rules.isEmpty()) {
          String retryUser = correctionPrompt(sample, raw);
          String raw2 = llm.chat(List.of(
              Map.of("role","system","content", sys),
              Map.of("role","user","content", retryUser)
          ), 0.0);
          rules = RuleJsonlCoercer.parse(raw2);

          // dump if still empty
          if (rules.isEmpty()) {
            Path dbg = Paths.get("runtime").resolve("llm_dbg");
            Files.createDirectories(dbg);
            String ts = java.time.LocalDateTime.now().toString().replace(":","-");
            Files.writeString(dbg.resolve("empty_retry_"+ts+".txt"), raw + "\n----\n" + raw2);
            System.err.println("[LEARN] 0 rules parsed, dump saved to runtime/llm_dbg");
          }
        }

        for (RuleV2 r : rules) repo.addOrMerge(r);
        added += rules.size();
        processedCache.markProcessed(root, p);
      }
    }

    repo.save();
    processedCache.save();
    System.out.printf("[LEARN] scanned=%d matchedByExt=%d rules_added=%d%n", scanned, matched, added);
  }

  private static String sample(String text, int maxChars) {
    return text.length() <= maxChars ? text : text.substring(0, maxChars);
  }

  private static String strictSystemPrompt() {
    return String.join("\n",
      "Ты — генератор правил RuleV2.",
      "Верни ТОЛЬКО JSONL. Одна строка = один объект JSON.",
      "Строго плоский формат (без обёрток):",
      "{\"id\":\"<string>\",\"type\":\"segment|block|stmt|rewrite\",\"regex\":\"^...$\",\"strategy\":\"...\",\"irType\":\"Assign|Call|Decl|If|Loop|Try\",\"fields\":[\"...\"],\"listFields\":[\"...\"],\"open\":\"^...$\",\"middle\":[\"^...$\"],\"close\":\"^...$\",\"pattern\":\"...\",\"replace\":\"...\",\"priority\":<int>}",
      "Требования:",
      "- Никаких Markdown/текста/комментариев.",
      "- Для type=stmt ОБЯЗАТЕЛЬНЫ irType и regex с ^ и $.",
      "- Для type=block ОБЯЗАТЕЛЬНЫ open/close (с ^ и $) и irType. middle — опционально.",
      "- Для type=segment ОБЯЗАТЕЛЬНЫ strategy и regex.",
      "- Для type=rewrite ОБЯЗАТЕЛЬНЫ pattern и replace.",
      "- id укажи уникальный, человекочитаемый.",
      "- 6–12 правил на один фрагмент, не дублируй."
    );
  }

  private static String strictUserPrompt(String sample) {
    return String.join("\n",
      "Проанализируй фрагмент неизвестного диалекта и сгенерируй набор правил RuleV2 (segment|block|stmt|rewrite).",
      "Примеры корректных строк JSONL:",
      "{\"id\":\"stmt_assign\",\"type\":\"stmt\",\"irType\":\"Assign\",\"regex\":\"^\\\\s*([A-Za-z_][A-Za-z0-9_]*)\\\\s*:=\\\\s*(.+);\\\\s*$\",\"fields\":[\"name\",\"expr\"]}",
      "{\"id\":\"stmt_call\",\"type\":\"stmt\",\"irType\":\"Call\",\"regex\":\"^\\\\s*([A-Za-z_][A-Za-z0-9_]*)\\\\s*\\\\((.*)\\\\)\\\\s*;?\\\\s*$\",\"fields\":[\"callee\",\"args\"],\"listFields\":[\"args\"]}",
      "{\"id\":\"stmt_decl\",\"type\":\"stmt\",\"irType\":\"Decl\",\"regex\":\"^\\\\s*DECLARE\\\\s+([A-Za-z_][A-Za-z0-9_]*)\\\\s*:\\\\s*([A-Za-z0-9_<>\\\\[\\\\]]+)\\\\s*;\\\\s*$\",\"fields\":[\"name\",\"type\"]}",
      "{\"id\":\"block_if\",\"type\":\"block\",\"irType\":\"If\",\"open\":\"^\\\\s*IF\\\\s+(.+?)\\\\s+THEN\\\\s*$\",\"middle\":[\"^\\\\s*ELSE\\\\s*$\"],\"close\":\"^\\\\s*END\\\\s*IF\\\\s*;?\\\\s*$\",\"fields\":[\"cond\"]}",
      "{\"id\":\"segment_semicolon\",\"type\":\"segment\",\"strategy\":\"regex_outside_quotes_parens\",\"regex\":\";\"}",
      "{\"id\":\"rw_is_null\",\"type\":\"rewrite\",\"pattern\":\"(?i)\\\\bis\\\\s+null\\\\b\",\"replace\":\"== null\"}",
      "",
      "Фрагмент:",
      sample
    );
  }

  private static String correctionPrompt(String sample, String invalid) {
    return String.join("\n",
      "Твой предыдущий ответ был в неверном формате. Нужен плоский JSONL RuleV2 (без {\"stmt\":{...}} обёрток).",
      "Примеры корректных строк JSONL:",
      "{\"id\":\"stmt_assign\",\"type\":\"stmt\",\"irType\":\"Assign\",\"regex\":\"^\\\\s*([A-Za-z_][A-Za-z0-9_]*)\\\\s*:=\\\\s*(.+);\\\\s*$\",\"fields\":[\"name\",\"expr\"]}",
      "{\"id\":\"stmt_call\",\"type\":\"stmt\",\"irType\":\"Call\",\"regex\":\"^\\\\s*([A-Za-z_][A-Za-z0-9_]*)\\\\s*\\\\((.*)\\\\)\\\\s*;?\\\\s*$\",\"fields\":[\"callee\",\"args\"],\"listFields\":[\"args\"]}",
      "{\"id\":\"block_if\",\"type\":\"block\",\"irType\":\"If\",\"open\":\"^\\\\s*IF\\\\s+(.+?)\\\\s+THEN\\\\s*$\",\"middle\":[\"^\\\\s*ELSE\\\\s*$\"],\"close\":\"^\\\\s*END\\\\s*IF\\\\s*;?\\\\s*$\",\"fields\":[\"cond\"]}",
      "",
      "Фрагмент:",
      sample,
      "",
      "Некорректный ответ, который ты вернул ранее (для понимания ошибки, НЕ ПОВТОРЯТЬ ЭТОТ ФОРМАТ):",
      invalid
    );
  }
}
