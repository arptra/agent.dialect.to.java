package com.example.agent.docs;

import com.example.agent.grammar.GrammarStore;
import com.example.agent.grammar.GrammarStore.Block;
import com.example.agent.grammar.GrammarStore.FunctionSig;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts grammar hints (keywords, blocks, macros, functions) from DOC/DOCX. */
public class DocGrammarExtractor {
    private static final Pattern KEYWORD = Pattern.compile("\\b[A-Z][A-Z0-9_]{1,}\\b");
    private static final Pattern MACRO = Pattern.compile("&([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");
    private static final Pattern FUNC = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*\\(([^)]*)\\)");
    private static final Pattern END_BLOCK = Pattern.compile("\\bEND\\s+([A-Z][A-Z0-9_]*)\\b");

    public void extract(Path docPath, GrammarStore store) throws IOException {
        List<String> lines = readLines(docPath);
        Map<String, Set<String>> mids = new HashMap<>();
        Set<String> opens = new LinkedHashSet<>();

        for (String line : lines) {
            Matcher kw = KEYWORD.matcher(line);
            while (kw.find()) {
                store.keywords.add(kw.group());
            }
            Matcher mc = MACRO.matcher(line);
            if (mc.find()) {
                store.macros.add("&");
            }
            Matcher fn = FUNC.matcher(line);
            while (fn.find()) {
                String name = fn.group(1);
                String args = fn.group(2);
                if (name.startsWith("&")) continue;
                FunctionSig fs = new FunctionSig();
                fs.name = name;
                fs.args = Arrays.stream(args.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
                store.functions.add(fs);
            }
            Matcher end = END_BLOCK.matcher(line);
            while (end.find()) {
                String open = end.group(1);
                opens.add(open);
            }
            for (String o : opens) {
                if (line.contains(o)) {
                    Matcher t = KEYWORD.matcher(line);
                    while (t.find()) {
                        String tok = t.group();
                        if (!tok.equals(o) && !tok.equals("END")) {
                            mids.computeIfAbsent(o, k -> new LinkedHashSet<>()).add(tok);
                        }
                    }
                }
            }
        }

        for (String o : opens) {
            Block b = new Block();
            b.open = o;
            b.close = "END " + o;
            Set<String> ms = mids.get(o);
            if (ms != null && !ms.isEmpty()) b.middle = new ArrayList<>(ms);
            store.blocks.add(b);
        }
    }

    private List<String> readLines(Path doc) throws IOException {
        String fn = doc.getFileName().toString().toLowerCase();
        List<String> lines = new ArrayList<>();
        try (InputStream is = Files.newInputStream(doc)) {
            if (fn.endsWith(".docx")) {
                XWPFDocument x = new XWPFDocument(is);
                for (XWPFParagraph p : x.getParagraphs()) {
                    lines.add(p.getText());
                }
            } else if (fn.endsWith(".doc")) {
                HWPFDocument h = new HWPFDocument(is);
                WordExtractor e = new WordExtractor(h);
                lines.addAll(Arrays.asList(e.getParagraphText()));
            } else {
                throw new IOException("Unsupported document: " + doc);
            }
        }
        return lines;
    }
}
