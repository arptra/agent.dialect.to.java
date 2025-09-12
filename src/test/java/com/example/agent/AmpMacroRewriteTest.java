package com.example.agent;

import com.example.agent.model.ir.IR;
import com.example.agent.rules.RuleV2;
import com.example.agent.rules.RewriteEngine;
import com.example.agent.rules.StmtEngine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for rw_amp_macro_strict rewrite supporting nested parentheses. */
public class AmpMacroRewriteTest {

    @Test
    void rewritesAmpMacroAndStmtCallRecognizes() {
        // setup rewrite rule
        RuleV2 rw = new RuleV2();
        rw.id = "rw_amp_macro_strict";
        rw.type = "rewrite";
        rw.pattern = "&([A-Za-z_][A-Za-z0-9_]*)\\s*\\((.*)\\)";
        rw.replace = "$1($2)";

        RewriteEngine re = new RewriteEngine();
        String normalized = re.applyAll("&msg(func(1,2));", List.of(rw));
        assertEquals("msg(func(1,2));", normalized);

        // stmt_call rule
        RuleV2 sc = new RuleV2();
        sc.id = "stmt_call";
        sc.type = "stmt";
        sc.irType = "Call";
        sc.regex = "^\\s*([A-Za-z_][A-Za-z0-9_]*)(?:\\s*(?:\\.|::)\\s*([A-Za-z_][A-Za-z0-9_]*))?\\s*\\((.*)\\)\\s*;?\\s*$";

        StmtEngine stmt = new StmtEngine(List.of(sc));
        IR.Node n = stmt.match(normalized);
        assertTrue(n instanceof IR.Call, "Should parse as Call");
        IR.Call call = (IR.Call) n;
        assertEquals("msg", call.callee);
        assertNull(call.ns);
    }
}
