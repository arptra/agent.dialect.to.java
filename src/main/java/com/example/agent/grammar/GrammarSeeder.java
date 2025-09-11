package com.example.agent.grammar;

import com.example.agent.rules.RuleLoaderV2;
import com.example.agent.rules.RuleV2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Seeds RuleLoaderV2 with generic rules derived from GrammarStore. */
public class GrammarSeeder {
    public void seed(GrammarStore store, RuleLoaderV2 repo) throws IOException {
        // segment: semicolon
        RuleV2 semi = new RuleV2();
        semi.id = "segment_semicolon";
        semi.type = "segment";
        semi.strategy = "regex_outside_quotes_parens";
        semi.regex = ";";
        repo.addOrMerge(semi);

        // segment: keywords
        for (String kw : store.keywords) {
            RuleV2 r = new RuleV2();
            r.id = "seg_kw_" + kw.toLowerCase();
            r.type = "segment";
            r.strategy = "regex_boundary_keep";
            r.regex = "\\b" + Pattern.quote(kw) + "\\b";
            repo.addOrMerge(r);
        }

        // block rules
        for (GrammarStore.Block b : store.blocks) {
            RuleV2 r = new RuleV2();
            r.id = "block_" + b.open.toLowerCase();
            r.type = "block";
            r.irType = "UnknownNode";
            r.open = "^\\s*(" + Pattern.quote(b.open) + ".*)$";
            r.close = "^\\s*" + Pattern.quote(b.close) + ".*$";
            if (b.middle != null && !b.middle.isEmpty()) {
                List<String> mids = new ArrayList<>();
                for (String m : b.middle) {
                    mids.add("^\\s*" + Pattern.quote(m) + ".*$");
                    // also ensure segment rule for middle tokens
                    RuleV2 mr = new RuleV2();
                    mr.id = "seg_kw_" + m.toLowerCase();
                    mr.type = "segment";
                    mr.strategy = "regex_boundary_keep";
                    mr.regex = "\\b" + Pattern.quote(m) + "\\b";
                    repo.addOrMerge(mr);
                }
                r.middle = mids.toArray(new String[0]);
            }
            r.fields = new String[]{"raw"};
            repo.addOrMerge(r);
            // ensure segment rules for open and close tokens
            for (String tok : (b.open + " " + b.close).split("\\s+")) {
                RuleV2 sr = new RuleV2();
                sr.id = "seg_kw_" + tok.toLowerCase();
                sr.type = "segment";
                sr.strategy = "regex_boundary_keep";
                sr.regex = "\\b" + Pattern.quote(tok) + "\\b";
                repo.addOrMerge(sr);
            }
        }

        // stmt rules
        RuleV2 assign = new RuleV2();
        assign.id = "stmt_assign";
        assign.type = "stmt";
        assign.irType = "Assign";
        assign.regex = "^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*:=\\s*(.+);?\\s*$";
        assign.fields = new String[]{"name","expr"};
        repo.addOrMerge(assign);

        RuleV2 decl = new RuleV2();
        decl.id = "stmt_decl";
        decl.type = "stmt";
        decl.irType = "Decl";
        decl.regex = "^\\s*DECLARE\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*(.+);?\\s*$";
        decl.fields = new String[]{"name","type"};
        repo.addOrMerge(decl);

        RuleV2 call = new RuleV2();
        call.id = "stmt_call";
        call.type = "stmt";
        call.irType = "Call";
        call.regex = "^\\s*([&]?[A-Za-z_][A-Za-z0-9_]*)\\s*\\((.*)\\)\\s*;?\\s*$";
        call.fields = new String[]{"callee","args"};
        call.listFields = new String[]{"args"};
        repo.addOrMerge(call);

        // macros rewrite
        if (!store.macros.isEmpty()) {
            RuleV2 rw = new RuleV2();
            rw.id = "rewrite_macro_call";
            rw.type = "rewrite";
            rw.pattern = "&([A-Za-z_][A-Za-z0-9_]*)\\s*\\((.*)\\)";
            rw.replace = "$1($2)";
            repo.addOrMerge(rw);
        }

        repo.save();
    }
}
