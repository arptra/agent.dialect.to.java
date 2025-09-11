package com.example.agent;

import com.example.agent.grammar.ManifestDrivenGrammarSeeder;
import com.example.agent.model.ir.IR;
import com.example.agent.rules.BlockEngine;
import com.example.agent.rules.RuleLoaderV2;
import com.example.agent.rules.SegmentEngine;
import com.example.agent.rules.StmtEngine;
import com.example.agent.translate.IRToJava;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TryCatchBlockTest {

    @Test
    void ceoCfoLookupCompilesToTryCatch() throws Exception {
        Path runtime = Files.createTempDirectory("runtime");
        new ManifestDrivenGrammarSeeder(Path.of("spec/plplus_syntax_manifest.json"), runtime).seed();
        RuleLoaderV2 loader = new RuleLoaderV2(runtime);
        String src = "BEGIN\nCEO := lookup('CEO');\nEXCEPTION WHEN NoDataFound THEN CFO := lookup('CFO');\nEND;";
        List<String> tokens = new SegmentEngine().segment(src, loader.ofType("segment"));
        assertEquals(List.of(
                "BEGIN",
                "CEO := lookup('CEO');",
                "EXCEPTION WHEN NoDataFound THEN CFO := lookup('CFO');",
                "END;"), tokens);
        StmtEngine stmt = new StmtEngine(loader.ofType("stmt"));
        IR ir = new BlockEngine().parse(tokens, loader.ofType("block"), stmt);
        assertFalse(ir.nodes.isEmpty());
        assertTrue(ir.nodes.get(0) instanceof IR.TryCatch);
        IR.TryCatch tc = (IR.TryCatch) ir.nodes.get(0);
        assertEquals(1, tc.tryBody.size());
        assertEquals(1, tc.catchBody.size());
        assertEquals("NoDataFound", tc.exceptionName);
        String java = new IRToJava().generate(ir, "Demo");
        String norm = java.replace("\r", "");
        assertTrue(norm.contains("try {"));
        assertTrue(norm.contains("catch (NoDataFound e)"));
        assertFalse(norm.contains("UNKNOWN"));
    }
}
