package com.example.agent.translate;

import com.example.agent.bootstrap.Improver;
import com.example.agent.knowledge.RuleStore;
import com.example.agent.model.ir.IR;
import com.example.agent.providers.GigaChatOpenAIClient;
import com.example.agent.rag.SimpleIndexer;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class TranslatorAgent {

    private final GigaChatOpenAIClient llm;
    private final SimpleIndexer indexer;
    private final RuleStore ruleStore;
    private final DynamicDialectParser parser;
    private final IRToJava generator = new IRToJava();
    private final JavaVerifier verifier = new JavaVerifier();
    private final Improver improver;

    public TranslatorAgent(GigaChatOpenAIClient llm, SimpleIndexer indexer, RuleStore ruleStore) {
        this.llm = llm;
        this.indexer = indexer;
        this.ruleStore = ruleStore;
        this.parser = new DynamicDialectParser(ruleStore);
        this.improver = new Improver(llm, ruleStore);
    }

    public String translate(String source) throws IOException {
        List<String> neighbors = indexer.topKSimilar(source, 5);
        IR ir = parser.parse(source);
        long unknowns = ir.nodes.stream().filter(n -> n instanceof IR.UnknownNode).count();

        if (unknowns > Math.max(2, ir.nodes.size()/3)) {
            String hint = askForIRHints(source, neighbors);
            ir.nodes.add(new IR.UnknownNode("LLM_HINT: " + hint.replaceAll("\n", " ")));
        }

        String className = "TranslatedProgram";
        String java = generator.generate(ir, className);

        var res = verifier.compile(className, java);
        if (!res.ok) {
            String repaired = tryRepair(java, res.diagnostics);
            if (repaired != null) {
                improver.refineRules(source, repaired, res.diagnostics);
                ruleStore.save();
                return repaired;
            }
        } else {
            improver.refineRules(source, java, "OK");
            ruleStore.save();
        }
        return java;
    }

    public String applyUserFix(String source, String currentJava, String userFeedback) throws IOException {
        String prompt = "Исправь Java-код согласно замечаниям пользователя, сохрани функциональность:\n\n" +
                "Замечания:\n" + userFeedback + "\n\nКод:\n" + currentJava +
                "\n\nВерни только исправленный Java.";
        String fixed = llm.chat(List.of(
                Map.of("role","system","content","Ты опытный Java-разработчик. Возвращай только компилируемый код."),
                Map.of("role","user","content", prompt)
        ), 0.2);
        improver.refineRules(source, fixed, userFeedback);
        ruleStore.save();
        return fixed;
    }

    private String askForIRHints(String source, List<String> neighbors) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("По коду неизвестного диалекта дай краткие подсказки для построения IR (Assign/Call/If/Loop/Decl). ");
        sb.append("Опирайся на похожие фрагменты. Кратко.\n\n");
        sb.append("Примеры:\n");
        for (String n : neighbors) sb.append("----\n").append(n).append("\n");
        sb.append("\nАнализируемый фрагмент:\n").append(source).append("\n");
        return llm.chat(List.of(
                Map.of("role","system","content","Отвечай коротко и структурно."),
                Map.of("role","user","content", sb.toString())
        ), 0.2);
    }

    private String tryRepair(String java, String diagnostics) throws IOException {
        String prompt = "Исправь компиляционные ошибки в Java-коде, не меняя логику. Верни только код.\n\n" +
                "Диагностика:\n" + diagnostics + "\n\nКод:\n" + java;
        String fixed = llm.chat(List.of(
                Map.of("role","system","content","Ты опытный Java-разработчик. Возвращай только готовый код."),
                Map.of("role","user","content", prompt)
        ), 0.2);
        if (fixed.contains("class")) return fixed;
        return null;
    }
}
