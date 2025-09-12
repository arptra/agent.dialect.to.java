package com.example.agent.rules;

import com.example.agent.model.ir.IR;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StmtEngine {
    private static class Compiled {
        final RuleV2 r;
        final Pattern p;

        Compiled(RuleV2 r) {
            this.r = r;
            this.p = Pattern.compile(r.regex, Pattern.CASE_INSENSITIVE);
        }
    }

    private List<Compiled> compiled = new ArrayList<>();

    public StmtEngine() {}

    public StmtEngine(List<RuleV2> stmtRules) {
        refresh(stmtRules);
    }

    public void refresh(List<RuleV2> stmtRules) {
        List<Compiled> c = new ArrayList<>();
        if (stmtRules != null) {
            for (RuleV2 r : stmtRules) {
                if (r.regex == null) continue;
                try {
                    c.add(new Compiled(r));
                } catch (Exception ignored) {
                }
            }
        }
        this.compiled = c;
    }

    public IR.Node match(String line) {
        for (Compiled cr : compiled) {
            Matcher m = cr.p.matcher(line);
            if (m.matches()) {
                IR.Node n = instantiateIR(cr.r, m);
                if (!(n instanceof IR.UnknownNode)) return n;
            }
        }
        return new IR.UnknownNode(line);
    }

    private IR.Node instantiateIR(RuleV2 r, Matcher m) {
        try {
            String fqcn = "com.example.agent.model.ir.IR$" + r.irType;
            Class<?> clazz = Class.forName(fqcn);
            Constructor<?> ctor = clazz.getDeclaredConstructors()[0];
            Class<?>[] ps = ctor.getParameterTypes();
            Object[] args = new Object[ps.length];
            for (int i = 0; i < ps.length; i++) {
                String val = safe(m, i + 1);
                if (List.class.isAssignableFrom(ps[i])) {
                    List<String> out = new ArrayList<>();
                    if (val != null && !val.isBlank()) for (String a : val.split(",")) out.add(a.trim());
                    args[i] = out;
                } else {
                    args[i] = val;
                }
            }
            if ("Call".equals(r.irType) && args.length == 3) {
                Object first = args[0];
                Object second = args[1];
                if (second == null || String.valueOf(second).isBlank()) {
                    args[0] = first;
                    args[1] = null;
                } else {
                    args[0] = second;
                    args[1] = first;
                }
            }
            return (IR.Node) ctor.newInstance(args);
        } catch (Throwable t) {
            return new IR.UnknownNode(m.group(0));
        }
    }

    private String safe(Matcher m, int i) {
        try {
            return m.group(i);
        } catch (Exception e) {
            return null;
        }
    }
}
