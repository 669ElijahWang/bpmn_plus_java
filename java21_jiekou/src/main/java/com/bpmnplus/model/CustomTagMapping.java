package com.bpmnplus.model;

/**
 * Custom tag mapping entry: maps a non-standard BPMN tag to a standard type.
 */
public class CustomTagMapping {

    private final String mappedType;
    private final boolean multiInstance;

    public CustomTagMapping(String mappedType, boolean multiInstance) {
        this.mappedType = mappedType;
        this.multiInstance = multiInstance;
    }

    public String getMappedType() {
        return mappedType;
    }

    public boolean isMultiInstance() {
        return multiInstance;
    }
}
