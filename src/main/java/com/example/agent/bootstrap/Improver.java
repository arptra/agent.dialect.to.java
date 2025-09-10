package com.example.agent.bootstrap;

import com.example.agent.knowledge.Rule;
import com.example.agent.knowledge.RuleStore;
import com.example.agent.providers.LlmProvider;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Generates improvement suggestions for parsing rules based on
 * a dialect snippet and the resulting Java code.
 */
public class Improver {

    private final LlmProvider llm;
    private final RuleStore store;

    public Improver(LlmProvider llm, RuleStore store) {
        this.llm = llm;
        this.store = store;
    }

    public void refineRules(String dialectSnippet, String javaResult, String diagnosticsOrFeedback) throws IOException {
        String prompt = "На основе пары (диалект -> Java) предложи улучшения правил распознавания в формате JSONL. " +
                "Строгие поля: id, irType, regex, fields:[...], listFields:[...] (опц.), javaTemplate (опц.). " +
                "Используй IR-ноды: Assign(name,expr), Call(callee,args), Decl(name,type), If(cond), Loop(header). " +
                "Не дублируй существующие правила, обобщи, стабилизируй regex.\n\n" +
                "Диалект:\n" + dialectSnippet + "\n\nJava:\n" + javaResult + "\n\nЗамечания/диагностика:\n" + diagnosticsOrFeedback +
                "\n\nВерни только JSONL.";
        String jsonl = llm.chat(List.of(
                Map.of("role","system","content","Возвращай только JSONL, по одному объекту в строке."),
                Map.of("role","user","content", prompt)
        ), 0.2);
        for (String line : jsonl.split("\r?\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            try {
                String id = extract(line, "\"id\"\\s*:\\s*\"(.*?)\"");
                String irType = extract(line, "\"irType\"\\s*:\\s*\"(.*?)\"");
                String regex = extract(line, "\"regex\"\\s*:\\s*\"(.*?)\"");
                String fields = extract(line, "\"fields\"\\s*:\\s*\\[(.*?)\\]");
                String listFields = extract(line, "\"listFields\"\\s*:\\s*\\[(.*?)\\]");
                String tpl = extract(line, "\"javaTemplate\"\\s*:\\s*\"(.*?)\"");
                if (id == null || irType == null || regex == null || fields == null) continue;
                var fs = new java.util.ArrayList<String>();
                var m1 = java.util.regex.Pattern.compile("\"(.*?)\"").matcher(fields);
                while (m1.find()) fs.add(m1.group(1));
                var lfs = new java.util.ArrayList<String>();
                if (listFields != null) {
                    var m2 = java.util.regex.Pattern.compile("\"(.*?)\"").matcher(listFields);
                    while (m2.find()) lfs.add(m2.group(1));
                }
                store.addOrUpdateRule(new Rule(id, irType, regex, fs.toArray(new String[0]), lfs.toArray(new String[0]), tpl));
            } catch (Exception ignored) {}
        }
    }

    private String extract(String s, String pattern) {
        var m = java.util.regex.Pattern.compile(pattern).matcher(s);
        return m.find() ? m.group(1) : null;
    }
}
