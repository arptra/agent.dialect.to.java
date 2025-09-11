package com.example.agent.translate;

import com.example.agent.model.ir.IR;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

public class IRToJava {

    /** Tracks variables declared in each nested block scope. */
    private final Deque<Set<String>> scopes = new ArrayDeque<>();

    public String generate(IR ir, String className) {
        scopes.clear();
        scopes.push(new HashSet<>()); // global scope for main method

        StringBuilder sb = new StringBuilder();
        sb.append("public class ").append(className).append(" {\n");
        sb.append("  public static void main(String[] args) {\n");
        for (IR.Node n : ir.nodes) {
            sb.append("    ").append(genStmt(n)).append("\n");
        }
        sb.append("  }\n");
        sb.append("}\n");

        scopes.pop();
        return sb.toString();
    }

    private String genStmt(IR.Node n) {
        if (n instanceof IR.Assign a) {
            String expr = sanitize(a.expr);
            if (isDeclared(a.name)) {
                return a.name + " = " + expr + ";";
            } else {
                declare(a.name);
                return "var " + a.name + " = " + expr + ";";
            }
        }
        if (n instanceof IR.Call c) {
            String args = String.join(", ", c.args);
            return c.callee + "(" + args + ");";
        }
        if (n instanceof IR.Decl d) {
            declare(d.name);
            return d.type + " " + d.name + ";";
        }
        if (n instanceof IR.If i) {
            return "if (" + sanitize(i.cond) + ") { /* TODO then */ } else { /* TODO else */ }";
        }
        if (n instanceof IR.Loop l) {
            return "// loop " + l.header + "\n    /* TODO convert loop body */";
        }
        if (n instanceof IR.Block b) {
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            for (IR.Node c : b.body) {
                sb.append("        ").append(genStmt(c)).append("\n");
            }
            sb.append("    }");
            return sb.toString();
        }
        if (n instanceof IR.UnknownNode u) {
            return "/* UNKNOWN: " + escape(u.raw) + " */";
        }
        return "/* TODO */";
    }

    private boolean isDeclared(String name) {
        for (Set<String> scope : scopes) {
            if (scope.contains(name)) {
                return true;
            }
        }
        return false;
    }

    private void declare(String name) {
        scopes.peek().add(name);
    }

    private String sanitize(String s) {
        return s
                .replace(":=", "=")
                .replace("<>", "!=")
                .replace(" and ", " && ")
                .replace(" or ", " || ");
    }

    private String escape(String s) {
        return s.replace("*/", "*_/").replace("\n", " ");
    }
}
