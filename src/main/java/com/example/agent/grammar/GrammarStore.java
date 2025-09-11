package com.example.agent.grammar;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Stores grammar artifacts extracted from documentation. */
public class GrammarStore {
    public static class Block {
        public String open;
        public List<String> middle;
        public String close;
    }
    public static class FunctionSig {
        public String name;
        public List<String> args;
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final Path dir;

    public List<Block> blocks = new ArrayList<>();
    public Set<String> keywords = new LinkedHashSet<>();
    public Set<String> macros = new LinkedHashSet<>();
    public List<FunctionSig> functions = new ArrayList<>();

    public GrammarStore(Path runtimeDir) throws IOException {
        this.dir = runtimeDir.resolve("grammar");
        Files.createDirectories(dir);
        load();
    }

    public void load() throws IOException {
        // blocks
        Path g = dir.resolve("grammar.json");
        if (Files.exists(g)) {
            Block[] bs = mapper.readValue(Files.readString(g), Block[].class);
            blocks = new ArrayList<>(Arrays.asList(bs));
        }
        // keywords
        Path k = dir.resolve("keywords.json");
        if (Files.exists(k)) {
            String[] arr = mapper.readValue(Files.readString(k), String[].class);
            keywords.addAll(Arrays.asList(arr));
        }
        // macros
        Path m = dir.resolve("macros.json");
        if (Files.exists(m)) {
            String[] arr = mapper.readValue(Files.readString(m), String[].class);
            macros.addAll(Arrays.asList(arr));
        }
        // functions
        Path f = dir.resolve("functions.jsonl");
        if (Files.exists(f)) {
            functions.clear();
            for (String line : Files.readAllLines(f)) {
                line = line.trim();
                if (line.isEmpty()) continue;
                functions.add(mapper.readValue(line, FunctionSig.class));
            }
        }
    }

    public void save() throws IOException {
        // blocks
        mapper.writeValue(dir.resolve("grammar.json").toFile(), blocks);
        // keywords
        mapper.writeValue(dir.resolve("keywords.json").toFile(), keywords);
        // macros
        mapper.writeValue(dir.resolve("macros.json").toFile(), macros);
        // functions
        Path f = dir.resolve("functions.jsonl");
        List<String> lines = new ArrayList<>();
        for (FunctionSig fn : functions) {
            lines.add(mapper.writeValueAsString(fn));
        }
        Files.write(f, lines);
    }
}
