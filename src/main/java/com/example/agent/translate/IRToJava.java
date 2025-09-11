package com.example.agent.translate;

import com.example.agent.model.ir.IR;

public class IRToJava {

    public String generate(IR ir, String className) {
        StringBuilder sb = new StringBuilder();
        sb.append("public class ").append(className).append(" {\n");
        sb.append("  public static void main(String[] args) {\n");
        for (IR.Node n : ir.nodes) {
            genStmt(sb, n, 2);
        }
        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
    }

    private void genStmt(StringBuilder sb, IR.Node n, int indent) {
        if (n instanceof IR.Assign a) {
            indent(sb, indent); sb.append("var ").append(a.name).append(" = ").append(sanitize(a.expr)).append(";\n");
            return;
        }
        if (n instanceof IR.Call c) {
            indent(sb, indent);
            String args = c.args.stream().map(this::sanitize).collect(java.util.stream.Collectors.joining(", "));
            sb.append(c.callee).append("(").append(args).append(");\n");
            return;
        }
        if (n instanceof IR.Decl d) {
            indent(sb, indent); sb.append(d.type).append(' ').append(d.name).append(";\n");
            return;
        }
        if (n instanceof IR.If i) {
            indent(sb, indent); sb.append("if (").append(sanitize(i.cond)).append(") {\n");
            for (IR.Node ch : i.thenBody) genStmt(sb, ch, indent + 1);
            indent(sb, indent); sb.append('}');
            if (!i.elseBody.isEmpty()) {
                sb.append(" else {\n");
                for (IR.Node ch : i.elseBody) genStmt(sb, ch, indent + 1);
                indent(sb, indent); sb.append('}');
            }
            sb.append("\n");
            return;
        }
        if (n instanceof IR.Loop l) {
            indent(sb, indent); sb.append("// loop ").append(l.header).append("\n");
            for (IR.Node ch : l.body) genStmt(sb, ch, indent + 1);
            return;
        }
        if (n instanceof IR.UnknownNode u) {
            indent(sb, indent); sb.append("/* UNKNOWN: ").append(escape(u.raw)).append(" */\n");
            return;
        }
        indent(sb, indent); sb.append("/* TODO */\n");
    }

    private void indent(StringBuilder sb, int lvl) { for (int i = 0; i < lvl; i++) sb.append("  "); }

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
