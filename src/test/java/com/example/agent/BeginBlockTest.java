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

public class BeginBlockTest {

    @Test
    void beginAssignmentCreatesBlock() throws Exception {
        Path runtime = Files.createTempDirectory("runtime");
        new ManifestDrivenGrammarSeeder(Path.of("spec/plplus_syntax_manifest.json"), runtime).seed();
        RuleLoaderV2 loader = new RuleLoaderV2(runtime);
        String src = "BEGIN\nP_ROLLBACK := 1;\nEND;";
        List<String> tokens = new SegmentEngine().segment(src, loader.ofType("segment"));
        assertEquals(List.of("BEGIN", "P_ROLLBACK := 1;", "END;"), tokens);

        StmtEngine stmt = new StmtEngine(loader.ofType("stmt"));
        var ir = new BlockEngine().parse(tokens, loader.ofType("block"), stmt);
        assertFalse(ir.nodes.isEmpty());
        assertTrue(ir.nodes.get(0) instanceof IR.Block);
        IR.Block blk = (IR.Block) ir.nodes.get(0);
        assertEquals(1, blk.body.size());
        assertTrue(blk.body.get(0) instanceof IR.Assign);
        assertTrue(blk.body.stream().noneMatch(n -> n instanceof IR.UnknownNode));

        String java = new IRToJava().generate(ir, "Demo");
        String norm = java.replace("\r", "");
        assertTrue(norm.contains("{\n        var P_ROLLBACK = 1;\n    }"));
        assertFalse(norm.contains("UNKNOWN"));
    }

    @Test
    void beginAssignmentSameLineSplits() throws Exception {
        Path runtime = Files.createTempDirectory("runtime");
        new ManifestDrivenGrammarSeeder(Path.of("spec/plplus_syntax_manifest.json"), runtime).seed();
        RuleLoaderV2 loader = new RuleLoaderV2(runtime);
        String src = "BEGIN P_ROLLBACK := 1; END;";
        List<String> tokens = new SegmentEngine().segment(src, loader.ofType("segment"));
        assertEquals(List.of("BEGIN", "P_ROLLBACK := 1;", "END;"), tokens);
    }
}

