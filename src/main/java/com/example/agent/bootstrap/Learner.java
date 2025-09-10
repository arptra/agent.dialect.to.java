package com.example.agent.bootstrap;

import com.example.agent.knowledge.Rule;
import com.example.agent.knowledge.RuleStore;
import com.example.agent.providers.LlmProvider;
import com.example.agent.rag.SimpleIndexer;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Learner {

    private final LlmProvider llm;
    private final RuleStore store;
    private final SimpleIndexer indexer;

    public Learner(LlmProvider llm, RuleStore store, SimpleIndexer indexer) {
        this.llm = llm;
        this.store = store;
        this.indexer = indexer;
    }

    public void learnFromRepo(Path root, List<String> includeExts) throws IOException {
        try (var walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> includeExts.stream().anyMatch(ext -> p.toString().endsWith(ext)))
                .forEach(p -> {
                    try {
                        if (store.isProcessed(root, p)) return;
                        String text = Files.readString(p);
                        indexer.addDocument(text);
                        String sample = sampleLines(text, 120);
                        String prompt = buildPrompt(sample);
                        String jsonl = llm.chat(List.of(
                                Map.of("role","system","content","Ты выводишь минимальные правила языка по примерам кода. Формат: JSONL, поля: id, irType, regex, fields, listFields (опц.), javaTemplate (опц.)."),
                                Map.of("role","user","content", prompt)
                        ), 0.2);
                        for (String line : jsonl.split("\r?\n")) {
                            line = line.trim();
                            if (line.isEmpty()) continue;
                            try {
                                var r = parseRule(line);
                                if (r != null) store.addOrUpdateRule(r);
                            } catch (Exception ignored) {}
                        }
                        store.markProcessed(root, p);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        }
    }

    private Rule parseRule(String jsonLine) {
        String id = extract(jsonLine, "\"id\"\\s*:\\s*\"(.*?)\"");
        String irType = extract(jsonLine, "\"irType\"\\s*:\\s*\"(.*?)\"");
        String regex = extract(jsonLine, "\"regex\"\\s*:\\s*\"(.*?)\"");
        String fields = extract(jsonLine, "\"fields\"\\s*:\\s*\\[(.*?)\\]");
        String listFields = extract(jsonLine, "\"listFields\"\\s*:\\s*\\[(.*?)\\]");
        String tpl = extract(jsonLine, "\"javaTemplate\"\\s*:\\s*\"(.*?)\"");
        if (id == null || irType == null || regex == null || fields == null) return null;
        java.util.List<String> fs = new java.util.ArrayList<>();
        var m1 = java.util.regex.Pattern.compile("\"(.*?)\"").matcher(fields);
        while (m1.find()) fs.add(m1.group(1));
        java.util.List<String> lfs = new java.util.ArrayList<>();
        if (listFields != null) {
            var m2 = java.util.regex.Pattern.compile("\"(.*?)\"").matcher(listFields);
            while (m2.find()) lfs.add(m2.group(1));
        }
        return new Rule(id, irType, regex, fs.toArray(new String[0]), lfs.toArray(new String[0]), tpl);
    }

    private String extract(String s, String pattern) {
        var m = java.util.regex.Pattern.compile(pattern).matcher(s);
        return m.find() ? m.group(1) : null;
    }

    private String sampleLines(String text, int maxLines) {
        String[] lines = text.split("\r?\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(maxLines, lines.length); i++) {
            sb.append(lines[i]).append('\n');
        }
        return sb.toString();
    }

    private String buildPrompt(String sample) {
        return "Дан фрагмент неизвестного диалекта. Выведи несколько правил распознавания конструкций (присваивание, вызов, объявление, условие, цикл) " +
                "в формате JSONL. Каждая строка — JSON-объект со строгими полями: " +
                "{id, irType, regex, fields:[...], listFields:[...] (опционально), javaTemplate (опционально)}. " +
                "irType должен совпадать с IR-нодой: Assign(name,expr), Call(callee,args), Decl(name,type), If(cond), Loop(header). " +
                "fields — порядок аргументов конструктора (например Assign → [\"name\",\"expr\"], Call → [\"callee\",\"args\"]). " +
                "Если поле — список аргументов (CSV), добавь его имя в listFields (например для Call: [\"args\"]).\n\n" +
                "Пример (не копируй буквально):\n" +
                "{\"id\":\"assign1\",\"irType\":\"Assign\",\"regex\":\"^\\\\s*([A-Za-z_][A-Za-z0-9_]*)\\\\s*:=\\\\s*(.+);\\\\s*$\",\"fields\":[\"name\",\"expr\"]}\n" +
                "{\"id\":\"call1\",\"irType\":\"Call\",\"regex\":\"^\\\\s*([A-Za-z_][A-Za-z0-9_]*)\\\\s*\\\\((.*)\\\\)\\\\s*;\\\\s*$\",\"fields\":[\"callee\",\"args\"],\"listFields\":[\"args\"]}\n\n" +
                "Фрагмент:\n" + sample + "\n" +
                "Выведи только JSONL без комментариев.";
    }
}
