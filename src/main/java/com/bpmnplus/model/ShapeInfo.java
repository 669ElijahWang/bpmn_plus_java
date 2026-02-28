package com.bpmnplus.model;

/**
 * Internal shape info used during BPMN XML generation.
 * Stores the computed coordinates and dimensions for diagram rendering.
 */
public class ShapeInfo {

    private String type;
    private String id;
    private Double xRaw;
    private Double yRaw;
    private Double x;
    private Double y;
    private double w;
    private double h;

    public ShapeInfo() {
    }

    public ShapeInfo(String type) {
        this.type = type;
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

    public Double getXRaw() {
        return xRaw;
    }

    public void setXRaw(Double xRaw) {
        this.xRaw = xRaw;
    }

    public Double getYRaw() {
        return yRaw;
    }

    public void setYRaw(Double yRaw) {
        this.yRaw = yRaw;
    }

    public Double getX() {
        return x;
    }

    public void setX(Double x) {
        this.x = x;
    }

    public Double getY() {
        return y;
    }

    public void setY(Double y) {
        this.y = y;
    }

    public double getW() {
        return w;
    }

    public void setW(double w) {
        this.w = w;
    }

    public double getH() {
        return h;
    }

    public void setH(double h) {
        this.h = h;
    }

    public boolean hasCoordinates() {
        return x != null && y != null;
    }
}
