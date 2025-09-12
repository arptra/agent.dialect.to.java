package com.example.agent;

import com.example.agent.model.ir.IR;
import com.example.agent.translate.IRToJava;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class IRToJavaTest {

    @Test
    void reassigningExistingVariableUsesSimpleAssignment() {
        IR ir = new IR();
        ir.nodes.add(new IR.Assign("P_ROLLBACK", "0"));
        ir.nodes.add(new IR.Assign("P_ROLLBACK", "NVL(P_ROLLBACK, 1)"));

        String java = new IRToJava().generate(ir, "Example");

        assertTrue(java.contains("var P_ROLLBACK = 0;"));
        assertTrue(java.contains("P_ROLLBACK = NVL(P_ROLLBACK, 1);"));
        assertFalse(java.contains("var P_ROLLBACK = NVL"));
        // ensure main method isn't empty
        assertFalse(java.contains("public static void main(String[] args) {\n  }\n"));
    }

    @Test
    void generatesBlocksConditionalsAndLoops() {
        IR ir = new IR();

        IR.Block root = new IR.Block();
        root.body.add(new IR.Assign("i", "0"));

        IR.If cond = new IR.If("i == 0");
        IR.Block thenBlock = new IR.Block();
        thenBlock.body.add(new IR.Call("msg", null, java.util.List.of("\"zero\"")));
        cond.thenBody.add(thenBlock);
        IR.Block elseBlock = new IR.Block();
        elseBlock.body.add(new IR.Call("msg", null, java.util.List.of("\"non-zero\"")));
        cond.elseBody.add(elseBlock);
        root.body.add(cond);

        IR.Loop loop = new IR.Loop("i < 2");
        IR.Block loopBody = new IR.Block();
        loopBody.body.add(new IR.Assign("i", "i + 1"));
        loopBody.body.add(new IR.Call("msg", null, java.util.List.of("\"iter\"")));
        loop.body.add(loopBody);
        root.body.add(loop);
        root.body.add(new IR.Call("createLog", "logger", java.util.List.of("true")));

        ir.nodes.add(root);

        String java = new IRToJava().generate(ir, "Example");

        assertFalse(java.contains("UNKNOWN"));
        assertFalse(java.contains("TODO"));
        assertTrue(java.contains("if (i == 0)"));
        assertTrue(java.contains("while (i < 2)"));
        assertTrue(java.contains("msg("));
        assertTrue(java.contains("logger.createLog(true);"));
    }
}

