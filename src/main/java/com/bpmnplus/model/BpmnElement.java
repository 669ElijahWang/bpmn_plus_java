package com.bpmnplus.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a BPMN flow node element (task, event, gateway, etc.).
 */
public class BpmnElement {

    private String type;
    private String id;
    private String name;
    private List<String> incoming = new ArrayList<>();
    private List<String> outgoing = new ArrayList<>();
    private boolean multiInstance;

    public BpmnElement() {
    }

    public BpmnElement(String type, String id, String name) {
        this.type = type;
        this.id = id;
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getIncoming() {
        return incoming;
    }

    public void setIncoming(List<String> incoming) {
        this.incoming = incoming;
    }

    public List<String> getOutgoing() {
        return outgoing;
    }

    public void setOutgoing(List<String> outgoing) {
        this.outgoing = outgoing;
    }

    public boolean isMultiInstance() {
        return multiInstance;
    }

    public void setMultiInstance(boolean multiInstance) {
        this.multiInstance = multiInstance;
    }
}
