package com.bpmnplus.model;

/**
 * Represents the result of converting a single BPMN file.
 */
public class ConvertResult {

    private String filename;
    private String content;
    private boolean success;

    public ConvertResult() {
    }

    public ConvertResult(String filename, String content, boolean success) {
        this.filename = filename;
        this.content = content;
        this.success = success;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}
