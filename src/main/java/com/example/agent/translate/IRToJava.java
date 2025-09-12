package com.example.agent.translate;

import com.example.agent.model.ir.IR;

public class IRToJava {
    private boolean needMsg;

    public String generate(IR ir, String className) {
        needMsg = false;
        StringBuilder sb = new StringBuilder();
        sb.append("public class ").append(className).append(" {\n");
        sb.append("  public static void main(String[] args) {\n");
        for (IR.Node n : ir.nodes) {
            sb.append(genStmt(n, 1)).append("\n");
        }
        sb.append("  }\n");
        if (needMsg) {
            sb.append("  private static void msg(String text) {\n");
            sb.append("    System.out.println(text);\n");
            sb.append("  }\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    private String genStmt(IR.Node n, int indent) {
        String ind = "    ".repeat(indent);
        if (n instanceof IR.Assign a) {
            return ind + "var " + a.name + " = " + sanitize(a.expr) + ";";
        }
        if (n instanceof IR.Call c) {
            if ("msg".equals(c.callee)) needMsg = true;
            String args = String.join(", ", c.args);
            return ind + c.callee + "(" + args + ");";
        }
        if (n instanceof IR.Decl d) {
            return ind + d.type + " " + d.name + ";";
        }
        if (n instanceof IR.Block b) {
            StringBuilder sb = new StringBuilder();
            sb.append(ind).append("{\n");
            for (IR.Node child : b.body) {
                sb.append(genStmt(child, indent + 1)).append("\n");
            }
            sb.append(ind).append("}");
            return sb.toString();
        }
        if (n instanceof IR.If i) {
            StringBuilder sb = new StringBuilder();
            sb.append(ind).append("if (").append(sanitize(i.cond)).append(") {\n");
            for (IR.Node child : i.thenBody) {
                sb.append(genStmt(child, indent + 1)).append("\n");
            }
            sb.append(ind).append("} else {\n");
            for (IR.Node child : i.elseBody) {
                sb.append(genStmt(child, indent + 1)).append("\n");
            }
            sb.append(ind).append("}");
            return sb.toString();
        }
        if (n instanceof IR.Loop l) {
            String header = sanitize(l.header).trim();
            StringBuilder sb = new StringBuilder();
            String lower = header.toLowerCase();
            if (lower.startsWith("for")) {
                String body = header.substring(3).trim();
                if (!body.startsWith("(")) body = "(" + body + ")";
                sb.append(ind).append("for ").append(body).append(" {\n");
            } else {
                String cond = lower.startsWith("while") ? header.substring(5).trim() : header;
                if (!cond.startsWith("(")) cond = "(" + cond + ")";
                sb.append(ind).append("while ").append(cond).append(" {\n");
            }
            for (IR.Node child : l.body) {
                sb.append(genStmt(child, indent + 1)).append("\n");
            }
            sb.append(ind).append("}");
            return sb.toString();
        }
        if (n instanceof IR.TryCatch tc) {
            StringBuilder sb = new StringBuilder();
            sb.append(ind).append("try {\n");
            for (IR.Node child : tc.tryBody) {
                sb.append(genStmt(child, indent + 1)).append("\n");
            }
            String ex = (tc.exceptionName != null && !tc.exceptionName.isBlank()) ? tc.exceptionName + " e" : "Exception e";
            sb.append(ind).append("} catch (" + ex + ") {\n");
            for (IR.Node child : tc.catchBody) {
                sb.append(genStmt(child, indent + 1)).append("\n");
            }
            sb.append(ind).append("}");
            return sb.toString();
        }
        if (n instanceof IR.Pragma p) {
            if ("error".equalsIgnoreCase(p.name) && !p.args.isEmpty()) {
                String msg = p.args.get(0);
                return ind + "throw new RuntimeException(" + sanitize(msg) + ");";
            }
            String args = String.join(", ", p.args);
            return ind + "/* PRAGMA " + p.name + "(" + args + ") */";
        }
        if (n instanceof IR.UnknownNode u) {
            return ind + "/* UNKNOWN: " + escape(u.raw) + " */";
        }
        return ind + "/* TODO */";
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
