package com.bpmnplus.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single BPMN process containing elements and sequence flows.
 */
public class BpmnProcess {

    private String id;
    private String name;
    private List<BpmnElement> elements = new ArrayList<>();
    private List<BpmnFlow> flows = new ArrayList<>();

    public BpmnProcess() {
    }

    public BpmnProcess(String id, String name) {
        this.id = id;
        this.name = name;
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

    public List<BpmnElement> getElements() {
        return elements;
    }

    public void setElements(List<BpmnElement> elements) {
        this.elements = elements;
    }

    public List<BpmnFlow> getFlows() {
        return flows;
    }

    public void setFlows(List<BpmnFlow> flows) {
        this.flows = flows;
    }
}
