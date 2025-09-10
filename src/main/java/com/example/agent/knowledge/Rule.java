package com.example.agent.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Declarative parsing rule:
 *  - regex: Java regex for a single-line construct
 *  - irType: IR node simple name (Assign, Call, Decl, If, Loop, UnknownNode)
 *  - fields: ordered field names to pass into the IR node constructor
 *  - listFields: optional field names that should be split by comma into List<String>
 *  - javaTemplate: optional generator hint (kept for future use)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Rule {
    public String id;
    public String irType;         // e.g., "Assign", "Call", "Decl", "If", "Loop"
    public String regex;
    public String[] fields;       // e.g., ["name","expr"] in constructor order
    public String[] listFields;   // fields interpreted as CSV -> List<String>
    public String javaTemplate;   // optional for direct Java synthesis

    public Rule() {}

    public Rule(String id, String irType, String regex, String[] fields, String[] listFields, String javaTemplate) {
        this.id = id;
        this.irType = irType;
        this.regex = regex;
        this.fields = fields;
        this.listFields = listFields;
        this.javaTemplate = javaTemplate;
    }

    public boolean isListField(String name) {
        if (listFields == null) return false;
        for (String f : listFields) if (name.equalsIgnoreCase(f)) return true;
        return false;
    }
}
