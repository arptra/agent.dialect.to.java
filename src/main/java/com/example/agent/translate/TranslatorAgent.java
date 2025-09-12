package com.example.agent.translate;

import com.example.agent.bootstrap.ImproverV2;
import com.example.agent.knowledge.RuleStore;
import com.example.agent.model.ir.IR;
import com.example.agent.providers.LlmProvider;
import com.example.agent.rag.SimpleIndexer;
import com.example.agent.rules.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class TranslatorAgent {
  private final LlmProvider llm;
  private final SimpleIndexer indexer;
  private final RuleStore processed; // only for processed_files.jsonl
  private final RuleLoaderV2 rules;
  private final IRToJava generator = new IRToJava();
  private final JavaVerifier verifier = new JavaVerifier();
  private final ImproverV2 improver;
  private final StmtEngine stmtEngine;

  public TranslatorAgent(LlmProvider llm, SimpleIndexer indexer, RuleStore processed, RuleLoaderV2 rules){
    this.llm=llm; this.indexer=indexer; this.processed=processed; this.rules=rules; this.improver=new ImproverV2(llm, rules);
    this.stmtEngine = new StmtEngine(rules.ofType("stmt"));
  }

  public String translate(String source) throws IOException {
    indexer.addDocument(source);

    // 1) Segment
    var seg = new SegmentEngine().segment(source, rules.ofType("segment"), rules.keywordBoundaries());

    // 2) Blocks + statements
    stmtEngine.refresh(rules.ofType("stmt"));
    IR ir = new BlockEngine().parse(seg, rules.ofType("block"), stmtEngine);

    // 3) Generate Java
    String className = "TranslatedProgram";
    String java = generator.generate(ir, className);

    // 4) Verify & improve rules
    JavaVerifier.Result res = verifier.compile(className, java);
    if (!res.ok) {
      String repaired = tryRepair(java, res.diagnostics);
      if (repaired != null) {
        improver.refine(source, repaired, res.diagnostics);
        return repaired;
      }
    } else {
      improver.refine(source, java, "OK");
    }
    return java;
  }

  public String applyUserFix(String source, String currentJava, String userFeedback) throws IOException {
    String prompt = "Исправь Java-код согласно замечаниям. Верни только код.\nЗамечания:\n"+userFeedback+"\nКод:\n"+currentJava;
    String fixed = llm.chat(List.of(
        Map.of("role","system","content","Только компилируемый код"),
        Map.of("role","user","content", prompt)
    ), 0.2);
    improver.refine(source, fixed, userFeedback);
    return fixed;
  }

  private String tryRepair(String java, String diagnostics) throws IOException {
    String prompt = "Исправь компиляционные ошибки в Java-коде. Верни только код.\nДиагностика:\n"+diagnostics+"\nКод:\n"+java;
    String fixed = llm.chat(List.of(
        Map.of("role","system","content","Только компилируемый код"),
        Map.of("role","user","content", prompt)
    ), 0.2);
    if (fixed.contains("class")) return fixed;
    return null;
  }
}
