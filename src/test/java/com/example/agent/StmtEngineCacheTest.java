package com.example.agent;

import com.example.agent.model.ir.IR;
import com.example.agent.rules.RuleV2;
import com.example.agent.rules.StmtEngine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class StmtEngineCacheTest {
    @Test
    void cachesAndRefreshes() {
        RuleV2 assign = new RuleV2();
        assign.id = "assign";
        assign.type = "stmt";
        assign.irType = "Assign";
        assign.regex = "^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*:=\\s*(.*);?\\s*$";

        RuleV2 call = new RuleV2();
        call.id = "call";
        call.type = "stmt";
        call.irType = "Call";
        call.regex = "^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\((.*)\\)\\s*;?\\s*$";

        StmtEngine engine = new StmtEngine(List.of(assign, call));

        IR.Node n1 = engine.match("X := 1;");
        assertTrue(n1 instanceof IR.Assign);
        IR.Node n2 = engine.match("foo(bar);");
        assertTrue(n2 instanceof IR.Call);
        assertFalse(n1 instanceof IR.UnknownNode);
        assertFalse(n2 instanceof IR.UnknownNode);

        engine.refresh(List.of(call));
        IR.Node afterRefresh = engine.match("X := 1;");
        assertTrue(afterRefresh instanceof IR.UnknownNode);
        IR.Node callAgain = engine.match("foo(bar);");
        assertTrue(callAgain instanceof IR.Call);
    }
}
