package com.example.agent.grammar;

import com.example.agent.rules.RuleV2;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Сидер правил RuleV2, который читает синтаксис из JSON-манифеста (data-driven).
 * Никаких ключевых слов в коде — всё берётся из spec/plplus_syntax_manifest.json.
 */
public class ManifestDrivenGrammarSeeder {

    private final Path manifestPath;
    private final Path runtimeDir;
    private final ObjectMapper M = new ObjectMapper();

    public ManifestDrivenGrammarSeeder(Path manifestPath, Path runtimeDir) {
        this.manifestPath = manifestPath;
        this.runtimeDir = runtimeDir;
    }

    public void seed() throws IOException {
        Files.createDirectories(runtimeDir);
        Path rules = runtimeDir.resolve("rules.jsonl");
        List<String> existing = Files.exists(rules) ? Files.readAllLines(rules, StandardCharsets.UTF_8) : List.of();
        Set<String> dedup = new LinkedHashSet<>(existing);

        // Загружаем манифест
        ObjectNode spec = (ObjectNode) M.readTree(Files.readString(manifestPath, StandardCharsets.UTF_8));

        // 1) segments
        for (var seg : spec.withArray("segments")) {
            RuleV2 r = new RuleV2();
            r.id = seg.path("id").asText();
            r.type = "segment";
            r.strategy = seg.path("strategy").asText();
            r.regex = seg.path("regex").asText();
            r.priority = seg.path("priority").asInt(10);
            add(r, dedup, rules);
        }

        // 2) blocks
        for (var blk : spec.withArray("blocks")) {
            RuleV2 r = new RuleV2();
            r.id = blk.path("id").asText();
            r.type = "block";
            r.irType = blk.path("irType").asText("Block");
            r.open = blk.path("open").asText();
            if (blk.has("middle")) {
                List<String> mids = new ArrayList<>();
                for (var m : blk.withArray("middle")) mids.add(m.asText());
                r.middle = mids.isEmpty() ? null : mids.toArray(new String[0]);
            }
            r.close = blk.path("close").asText();
            r.priority = blk.path("priority").asInt(50);
            add(r, dedup, rules);
        }

        // 3) statements
        for (var st : spec.withArray("statements")) {
            RuleV2 r = new RuleV2();
            r.id = st.path("id").asText();
            r.type = "stmt";
            r.irType = st.path("irType").asText();
            r.regex = st.path("regex").asText();
            if (st.has("fields")) {
                List<String> f = new ArrayList<>();
                for (var x : st.withArray("fields")) f.add(x.asText());
                r.fields = f.toArray(new String[0]);
            }
            if (st.has("listFields")) {
                List<String> lf = new ArrayList<>();
                for (var x : st.withArray("listFields")) lf.add(x.asText());
                r.listFields = lf.toArray(new String[0]);
            }
            r.priority = st.path("priority").asInt(20);
            add(r, dedup, rules);
        }

        // 4) rewrites
        for (var rw : spec.withArray("rewrites")) {
            RuleV2 r = new RuleV2();
            r.id = rw.path("id").asText();
            r.type = "rewrite";
            r.pattern = rw.path("pattern").asText();
            r.replace = rw.path("replace").asText();
            r.priority = rw.path("priority").asInt(5);
            add(r, dedup, rules);
        }

        System.out.println("[SEED-MANIFEST] total rules now = " + dedup.size() + " → " + rules.toAbsolutePath());
    }

    private void add(RuleV2 r, Set<String> dedup, Path rules) throws IOException {
        String json = M.writeValueAsString(r);
        if (dedup.add(json)) {
            try (BufferedWriter bw = Files.newBufferedWriter(rules, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                bw.write(json); bw.newLine();
            }
            System.out.println("[+rule] " + r.id);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: ManifestDrivenGrammarSeeder <spec.json> <runtimeDir>");
            System.exit(2);
        }
        new ManifestDrivenGrammarSeeder(Paths.get(args[0]), Paths.get(args[1])).seed();
    }
}
