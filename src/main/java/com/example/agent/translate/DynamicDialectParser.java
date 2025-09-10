package com.example.agent.translate;

import com.example.agent.knowledge.Rule;
import com.example.agent.knowledge.RuleStore;
import com.example.agent.model.ir.IR;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DynamicDialectParser {

    private final List<CompiledRule> compiled = new ArrayList<>();

    private static class CompiledRule {
        final Rule r;
        final Pattern p;
        CompiledRule(Rule r) {
            this.r = r;
            this.p = Pattern.compile(r.regex, Pattern.CASE_INSENSITIVE);
        }
    }

    public DynamicDialectParser(RuleStore store) {
        for (Rule r : store.allRules()) {
            try {
                compiled.add(new CompiledRule(r));
            } catch (Exception ignored) {}
        }
    }

    public IR parse(String source) {
        IR ir = new IR();
        String[] lines = source.split("\r?\n");
        for (String line : lines) {
            if (line.isBlank()) continue;
            boolean matched = false;
            for (CompiledRule cr : compiled) {
                Matcher m = cr.p.matcher(line);
                if (m.matches()) {
                    IR.Node node = buildNodeByRule(cr.r, m, line);
                    ir.nodes.add(node);
                    matched = true;
                    break;
                }
            }
            if (!matched) ir.nodes.add(new IR.UnknownNode(line));
        }
        return ir;
    }

    private IR.Node buildNodeByRule(Rule r, Matcher m, String fallbackRaw) {
        try {
            String fqcn = "com.example.agent.model.ir.IR$" + r.irType;
            Class<?> clazz = Class.forName(fqcn);
            Constructor<?>[] ctors = clazz.getDeclaredConstructors();
            if (ctors.length == 0) return new IR.UnknownNode(fallbackRaw);
            Constructor<?> ctor = ctors[0];
            Class<?>[] params = ctor.getParameterTypes();
            Object[] args = new Object[params.length];

            // Map fields -> constructor args by order
            for (int i = 0; i < params.length; i++) {
                String fieldName = (r.fields != null && i < r.fields.length) ? r.fields[i] : null;
                String value = null;
                if (fieldName != null) {
                    // group index is i+1 by convention; but try by name position-independent
                    int groupIdx = i + 1;
                    // safer: try to find position of fieldName in r.fields
                    for (int gi = 0; gi < r.fields.length; gi++) {
                        if (r.fields[gi].equalsIgnoreCase(fieldName)) { groupIdx = gi + 1; break; }
                    }
                    value = safeGroup(m, groupIdx);
                } else {
                    value = safeGroup(m, i + 1);
                }

                if (params[i] == String.class) {
                    args[i] = value;
                } else if (List.class.isAssignableFrom(params[i])) {
                    // convert CSV to List<String>
                    if (value == null || value.isBlank()) {
                        args[i] = java.util.List.of();
                    } else {
                        java.util.List<String> out = new java.util.ArrayList<>();
                        for (String a : value.split(",")) out.add(a.trim());
                        args[i] = out;
                    }
                } else {
                    // fallback: pass as string
                    args[i] = value;
                }
            }
            Object node = ctor.newInstance(args);
            return (IR.Node) node;
        } catch (Throwable t) {
            return new IR.UnknownNode(fallbackRaw);
        }
    }

    private String safeGroup(Matcher m, int idx) {
        try { return m.group(idx); } catch (Exception e) { return null; }
    }
}
