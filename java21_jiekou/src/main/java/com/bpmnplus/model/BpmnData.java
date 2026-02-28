package com.bpmnplus.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents parsed data from a BPMN file.
 */
public class BpmnData {

    private String definitionsId = "Definitions_1";
    private List<BpmnProcess> processes = new ArrayList<>();
    private List<BpmnShape> shapes = new ArrayList<>();

    public String getDefinitionsId() {
        return definitionsId;
    }

    public void setDefinitionsId(String definitionsId) {
        this.definitionsId = definitionsId;
    }

    public List<BpmnProcess> getProcesses() {
        return processes;
    }

    public void setProcesses(List<BpmnProcess> processes) {
        this.processes = processes;
    }

    public List<BpmnShape> getShapes() {
        return shapes;
    }

    public void setShapes(List<BpmnShape> shapes) {
        this.shapes = shapes;
    }
}
