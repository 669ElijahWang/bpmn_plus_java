package com.bpmnplus.service;

import com.bpmnplus.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Core BPMN conversion service.
 * Parses non-standard BPMN XML via regex and rebuilds it as Camunda Cloud
 * (Zeebe) standard format.
 * This is a faithful Java port of convert_bpmn.py.
 */
@Service
public class BpmnConvertService {

    private static final Logger log = LoggerFactory.getLogger(BpmnConvertService.class);

    // ── Default sizes for elements ──────────────────────────────────────────
    private static final Map<String, int[]> DIMENSIONS = new LinkedHashMap<>();
    static {
        DIMENSIONS.put("startEvent", new int[] { 36, 36 });
        DIMENSIONS.put("endEvent", new int[] { 36, 36 });
        DIMENSIONS.put("userTask", new int[] { 100, 80 });
        DIMENSIONS.put("exclusiveGateway", new int[] { 50, 50 });
        DIMENSIONS.put("parallelGateway", new int[] { 50, 50 });
        DIMENSIONS.put("inclusiveGateway", new int[] { 50, 50 });
        DIMENSIONS.put("eventBasedGateway", new int[] { 50, 50 });
        DIMENSIONS.put("complexGateway", new int[] { 50, 50 });
        DIMENSIONS.put("task", new int[] { 100, 80 });
    }
    private static final int[] DEFAULT_SIZE = { 100, 80 };

    // ── Standard BPMN flow-node tags ────────────────────────────────────────
    private static final List<String> FLOW_NODE_TAGS = List.of(
            "startEvent", "endEvent", "userTask", "serviceTask", "scriptTask",
            "sendTask", "receiveTask", "manualTask", "businessRuleTask", "task",
            "exclusiveGateway", "parallelGateway", "inclusiveGateway",
            "eventBasedGateway", "complexGateway", "subProcess", "callActivity",
            "intermediateCatchEvent", "intermediateThrowEvent", "boundaryEvent");

    // ── Custom non-standard tags mapped to standard BPMN types ──────────────
    private static final Map<String, CustomTagMapping> CUSTOM_TAG_MAP = new LinkedHashMap<>();
    static {
        CUSTOM_TAG_MAP.put("countersignTask", new CustomTagMapping("userTask", true));
        CUSTOM_TAG_MAP.put("multiInstanceTask", new CustomTagMapping("userTask", true));
    }

    // =====================================================================
    // Public API
    // =====================================================================

    /**
     * Convert BPMN content string. Returns the converted XML or null on failure.
     */
    public String performConversion(String content, String filename) {
        try {
            BpmnData data = parseFileContent(content);
            if (data.getProcesses().isEmpty()) {
                log.warn("Warning: No processes found in {}", filename);
                return null;
            }
            return buildBpmn(data);
        } catch (Exception e) {
            log.error("Conversion error in {}: {}", filename, e.getMessage(), e);
            return null;
        }
    }

    // =====================================================================
    // Parsing
    // =====================================================================

    private BpmnData parseFileContent(String content) {
        BpmnData data = new BpmnData();

        // Extract definitions basics
        Matcher defMatch = Pattern.compile("<(?:\\w+:)?definitions\\b([^>]*)>", Pattern.DOTALL).matcher(content);
        if (defMatch.find()) {
            String attrs = defMatch.group(1);
            String id = extractAttr(attrs, "id");
            data.setDefinitionsId(id != null ? id : "Definitions_1");
        }

        // Extract process blocks
        Pattern processPattern = Pattern.compile(
                "<(?:\\w+:)?process\\b([^>]*)>(.*?)</(?:\\w+:)?process>", Pattern.DOTALL);
        Matcher procMatcher = processPattern.matcher(content);

        while (procMatcher.find()) {
            String procAttrs = procMatcher.group(1).trim();
            String procBody = procMatcher.group(2);

            String procId = extractAttr(procAttrs, "id");
            String procName = extractAttr(procAttrs, "name");

            BpmnProcess proc = new BpmnProcess(
                    procId != null ? procId : "Process_" + shortUuid(),
                    procName != null ? procName : "Process_Name");

            // Parse standard flow nodes
            for (String tagName : FLOW_NODE_TAGS) {
                parseNodes(proc, procBody, tagName, tagName, false);
            }

            // Parse custom/non-standard tags
            Set<String> existingIds = new HashSet<>();
            for (BpmnElement e : proc.getElements()) {
                existingIds.add(e.getId());
            }
            for (Map.Entry<String, CustomTagMapping> entry : CUSTOM_TAG_MAP.entrySet()) {
                String customTag = entry.getKey();
                CustomTagMapping mapping = entry.getValue();
                parseCustomNodes(proc, procBody, customTag, mapping.getMappedType(),
                        mapping.isMultiInstance(), existingIds);
            }

            // Parse sequence flows
            parseFlows(proc, procBody);

            data.getProcesses().add(proc);
        }

        // Extract Shapes
        parseShapes(data, content);

        return data;
    }

    /**
     * Parse standard block and self-closing nodes for a given tag name.
     */
    private void parseNodes(BpmnProcess proc, String procBody,
            String tagName, String mappedType, boolean isMultiInstance) {
        // Block elements: <tagName ...>...</tagName>
        Pattern blockPattern = Pattern.compile(
                "<(?:\\w+:)?" + tagName + "\\b([^>]*)>(.*?)</(?:\\w+:)?" + tagName + ">",
                Pattern.DOTALL);
        Matcher blockMatcher = blockPattern.matcher(procBody);
        while (blockMatcher.find()) {
            String attrs = blockMatcher.group(1);
            String body = blockMatcher.group(2);
            if (attrs.trim().endsWith("/"))
                continue;

            String id = extractAttr(attrs, "id");
            if (id == null || id.isEmpty())
                continue;

            BpmnElement elem = new BpmnElement(mappedType, id,
                    optional(extractAttr(attrs, "name")));
            elem.setIncoming(findAll("<(?:\\w+:)?incoming>(.*?)</(?:\\w+:)?incoming>", body));
            elem.setOutgoing(findAll("<(?:\\w+:)?outgoing>(.*?)</(?:\\w+:)?outgoing>", body));
            elem.setMultiInstance(isMultiInstance);
            proc.getElements().add(elem);
        }

        // Self-closing: <tagName ... />
        Pattern scPattern = Pattern.compile(
                "<(?:\\w+:)?" + tagName + "\\b([^>]*)/>", Pattern.DOTALL);
        Matcher scMatcher = scPattern.matcher(procBody);
        Set<String> existingIds = new HashSet<>();
        for (BpmnElement e : proc.getElements()) {
            existingIds.add(e.getId());
        }
        while (scMatcher.find()) {
            String attrs = scMatcher.group(1);
            String id = extractAttr(attrs, "id");
            if (id != null && !id.isEmpty() && !existingIds.contains(id)) {
                BpmnElement elem = new BpmnElement(mappedType, id,
                        optional(extractAttr(attrs, "name")));
                elem.setMultiInstance(isMultiInstance);
                proc.getElements().add(elem);
                existingIds.add(id);
            }
        }
    }

    /**
     * Parse custom/non-standard nodes (e.g. countersignTask).
     */
    private void parseCustomNodes(BpmnProcess proc, String procBody,
            String customTag, String mappedType,
            boolean isMultiInstance, Set<String> existingIds) {
        Pattern pattern = Pattern.compile(
                "<(?:\\w+:)?" + customTag + "\\b([^>]*)>(.*?)</(?:\\w+:)?" + customTag + ">",
                Pattern.DOTALL);
        Matcher matcher = pattern.matcher(procBody);
        while (matcher.find()) {
            String attrs = matcher.group(1);
            String body = matcher.group(2);
            String id = extractAttr(attrs, "id");
            if (id != null && !id.isEmpty() && !existingIds.contains(id)) {
                BpmnElement elem = new BpmnElement(mappedType, id,
                        optional(extractAttr(attrs, "name")));
                elem.setIncoming(findAll("<(?:\\w+:)?incoming>(.*?)</(?:\\w+:)?incoming>", body));
                elem.setOutgoing(findAll("<(?:\\w+:)?outgoing>(.*?)</(?:\\w+:)?outgoing>", body));
                elem.setMultiInstance(isMultiInstance);
                proc.getElements().add(elem);
                existingIds.add(id);
            }
        }
    }

    /**
     * Parse sequence flows from a process body.
     */
    private void parseFlows(BpmnProcess proc, String procBody) {
        Pattern flowPattern = Pattern.compile(
                "<(?:\\w+:)?sequenceFlow\\b([^>]*)(?:>(.*?)</(?:\\w+:)?sequenceFlow>|/>)",
                Pattern.DOTALL);
        Matcher flowMatcher = flowPattern.matcher(procBody);
        while (flowMatcher.find()) {
            String attrs = flowMatcher.group(1);
            String body = flowMatcher.group(2) != null ? flowMatcher.group(2) : "";

            BpmnFlow flow = new BpmnFlow();
            String fid = extractAttr(attrs, "id");
            flow.setId(fid != null ? fid : "Flow_" + shortUuid());
            flow.setSourceRef(optional(extractAttr(attrs, "sourceRef")));
            flow.setTargetRef(optional(extractAttr(attrs, "targetRef")));
            flow.setName(optional(extractAttr(attrs, "name")));

            // Parse condition expression
            Matcher condMatcher = Pattern.compile(
                    "<(?:\\w+:)?conditionExpression[^>]*>(.*?)</(?:\\w+:)?conditionExpression>",
                    Pattern.DOTALL).matcher(body);
            if (condMatcher.find()) {
                flow.setCondition(condMatcher.group(1).trim());
            }

            if (flow.getId() != null && !flow.getId().isEmpty()) {
                proc.getFlows().add(flow);
            }
        }
    }

    /**
     * Parse BPMNShape elements from the full content.
     */
    private void parseShapes(BpmnData data, String content) {
        Pattern shapePattern = Pattern.compile(
                "<(?:\\w+:)?BPMNShape\\b([^>]*)>.*?\\b(?:\\w+:)?Bounds\\b([^>]*)/?>", Pattern.DOTALL);
        Matcher shapeMatcher = shapePattern.matcher(content);
        while (shapeMatcher.find()) {
            String sAttrs = shapeMatcher.group(1);
            String bAttrs = shapeMatcher.group(2);

            BpmnShape shape = new BpmnShape();
            String bpmnElement = extractAttr(sAttrs, "bpmnElement");
            shape.setBpmnElement(bpmnElement != null ? bpmnElement : "");
            String sid = extractAttr(sAttrs, "id");
            shape.setId(sid != null ? sid : "Shape_" + shortUuid());
            shape.setX(extractIntAttr(bAttrs, "x"));
            shape.setY(extractIntAttr(bAttrs, "y"));
            shape.setWidth(extractIntAttr(bAttrs, "width"));
            shape.setHeight(extractIntAttr(bAttrs, "height"));

            if (!shape.getBpmnElement().isEmpty()) {
                data.getShapes().add(shape);
            }
        }
    }

    // =====================================================================
    // XML Building
    // =====================================================================

    private String buildBpmn(BpmnData data) {
        List<String> lines = new ArrayList<>();
        lines.add("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        lines.add("<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" " +
                "xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\" " +
                "xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "xmlns:zeebe=\"http://camunda.org/schema/zeebe/1.0\" " +
                "xmlns:di=\"http://www.omg.org/spec/DD/20100524/DI\" " +
                "xmlns:modeler=\"http://camunda.org/schema/modeler/1.0\" " +
                "id=\"Definitions_1\" targetNamespace=\"http://bpmn.io/schema/bpmn\" " +
                "exporter=\"Camunda Modeler\" exporterVersion=\"5.42.0\" " +
                "modeler:executionPlatform=\"Camunda Cloud\" " +
                "modeler:executionPlatformVersion=\"8.8.0\">");

        // Build shape map & gateway set
        Map<String, ShapeInfo> shapeMap = new LinkedHashMap<>();
        Set<String> gateways = new HashSet<>();

        for (BpmnProcess proc : data.getProcesses()) {
            for (BpmnElement e : proc.getElements()) {
                if (e.getType().contains("Gateway")) {
                    gateways.add(e.getId());
                }
                shapeMap.put(e.getId(), new ShapeInfo(e.getType()));
            }
        }

        // Parse dimensions and determine bounds
        for (BpmnShape s : data.getShapes()) {
            ShapeInfo si = shapeMap.get(s.getBpmnElement());
            if (si == null)
                continue;

            String stype = si.getType();
            int[] dim = DIMENSIONS.getOrDefault(stype, DEFAULT_SIZE);
            int dw = dim[0], dh = dim[1];

            double w = s.getWidth() != null ? s.getWidth() : dw;
            double h = s.getHeight() != null ? s.getHeight() : dh;
            Double origX = s.getX() != null ? (double) s.getX() : null;
            Double origY = s.getY() != null ? (double) s.getY() : null;

            // If width and height are both missing, treat x,y as center and convert to
            // top-left
            if (s.getWidth() == null && s.getHeight() == null) {
                if (origX != null)
                    origX -= w / 2;
                if (origY != null)
                    origY -= h / 2;
            }

            si.setId(s.getBpmnElement() + "_di");
            si.setXRaw(origX);
            si.setYRaw(origY);
            si.setW(w);
            si.setH(h);
        }

        // Calc coordinate offset
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        boolean hasX = false, hasY = false;
        for (ShapeInfo si : shapeMap.values()) {
            if (si.getXRaw() != null) {
                minX = Math.min(minX, si.getXRaw());
                hasX = true;
            }
            if (si.getYRaw() != null) {
                minY = Math.min(minY, si.getYRaw());
                hasY = true;
            }
        }
        double offX = hasX ? Math.max(0, 100 - minX) : 0;
        double offY = hasY ? Math.max(0, 100 - minY) : 0;

        for (ShapeInfo si : shapeMap.values()) {
            if (si.getXRaw() != null) {
                si.setX(si.getXRaw() + offX);
                si.setY(si.getYRaw() + offY);
            }
        }

        // ── Process elements ────────────────────────────────────────────────
        for (BpmnProcess proc : data.getProcesses()) {
            lines.add("  <bpmn:process id=\"" + esc(proc.getId()) + "\" name=\"" +
                    esc(proc.getName()) + "\" isExecutable=\"true\">");

            for (BpmnElement e : proc.getElements()) {
                String tag = "bpmn:" + e.getType();
                String nAttr = (e.getName() != null && !e.getName().isEmpty())
                        ? " name=\"" + esc(e.getName()) + "\""
                        : "";
                lines.add("    <" + tag + " id=\"" + esc(e.getId()) + "\"" + nAttr + ">");
                for (String inc : e.getIncoming()) {
                    lines.add("      <bpmn:incoming>" + esc(inc) + "</bpmn:incoming>");
                }
                for (String out : e.getOutgoing()) {
                    lines.add("      <bpmn:outgoing>" + esc(out) + "</bpmn:outgoing>");
                }
                if (e.isMultiInstance()) {
                    lines.add("      <bpmn:multiInstanceLoopCharacteristics />");
                }
                lines.add("    </" + tag + ">");
            }

            for (BpmnFlow f : proc.getFlows()) {
                String nAttr = (f.getName() != null && !f.getName().isEmpty())
                        ? " name=\"" + esc(f.getName()) + "\""
                        : "";
                lines.add("    <bpmn:sequenceFlow id=\"" + esc(f.getId()) +
                        "\" sourceRef=\"" + esc(f.getSourceRef()) +
                        "\" targetRef=\"" + esc(f.getTargetRef()) + "\"" + nAttr + ">");
                if (f.getCondition() != null && !f.getCondition().isEmpty()
                        && gateways.contains(f.getSourceRef())) {
                    String cond = f.getCondition().startsWith("=")
                            ? f.getCondition()
                            : "=" + f.getCondition();
                    lines.add("      <bpmn:conditionExpression xsi:type=\"bpmn:tFormalExpression\">" +
                            esc(cond) + "</bpmn:conditionExpression>");
                }
                lines.add("    </bpmn:sequenceFlow>");
            }
            lines.add("  </bpmn:process>");
        }

        // ── Diagram ─────────────────────────────────────────────────────────
        if (!data.getProcesses().isEmpty()) {
            lines.add("  <bpmndi:BPMNDiagram id=\"BPMNDiagram_1\">");
            lines.add("    <bpmndi:BPMNPlane id=\"BPMNPlane_1\" bpmnElement=\"" +
                    esc(data.getProcesses().get(0).getId()) + "\">");

            for (Map.Entry<String, ShapeInfo> entry : shapeMap.entrySet()) {
                String eid = entry.getKey();
                ShapeInfo si = entry.getValue();
                if (si.hasCoordinates()) {
                    lines.add("      <bpmndi:BPMNShape id=\"" + esc(si.getId()) +
                            "\" bpmnElement=\"" + esc(eid) + "\">");
                    lines.add("        <dc:Bounds x=\"" + (int) Math.round(si.getX()) +
                            "\" y=\"" + (int) Math.round(si.getY()) +
                            "\" width=\"" + (int) Math.round(si.getW()) +
                            "\" height=\"" + (int) Math.round(si.getH()) + "\" />");
                    lines.add("      </bpmndi:BPMNShape>");
                }
            }

            // ── Edges ───────────────────────────────────────────────────────
            for (BpmnProcess proc : data.getProcesses()) {
                for (BpmnFlow f : proc.getFlows()) {
                    ShapeInfo src = shapeMap.get(f.getSourceRef());
                    ShapeInfo tgt = shapeMap.get(f.getTargetRef());
                    if (src == null || tgt == null || !src.hasCoordinates() || !tgt.hasCoordinates()) {
                        continue;
                    }
                    buildEdge(lines, f, src, tgt);
                }
            }
            lines.add("    </bpmndi:BPMNPlane>");
            lines.add("  </bpmndi:BPMNDiagram>");
        }

        lines.add("</bpmn:definitions>");
        return String.join("\n", lines);
    }

    /**
     * Build a BPMNEdge element with waypoints, mirroring the Python edge-routing
     * logic.
     */
    private void buildEdge(List<String> lines, BpmnFlow f, ShapeInfo src, ShapeInfo tgt) {
        double scx = src.getX() + src.getW() / 2;
        double scy = src.getY() + src.getH() / 2;
        double tcx = tgt.getX() + tgt.getW() / 2;
        double tcy = tgt.getY() + tgt.getH() / 2;

        double dx = tcx - scx;
        double dy = tcy - scy;

        String srcFace, tgtFace;
        if (Math.abs(dx) >= Math.abs(dy)) {
            srcFace = dx >= 0 ? "RIGHT" : "LEFT";
            tgtFace = dx >= 0 ? "LEFT" : "RIGHT";
        } else {
            srcFace = dy >= 0 ? "BOTTOM" : "TOP";
            tgtFace = dy >= 0 ? "TOP" : "BOTTOM";
        }

        double[] p1 = getPoint(src, srcFace);
        double[] p2 = getPoint(tgt, tgtFace);

        List<double[]> pts = new ArrayList<>();
        pts.add(p1);

        boolean srcHorizontal = srcFace.equals("LEFT") || srcFace.equals("RIGHT");
        boolean tgtHorizontal = tgtFace.equals("LEFT") || tgtFace.equals("RIGHT");

        if (srcHorizontal && tgtHorizontal) {
            if (Math.abs(p1[1] - p2[1]) > 10) {
                double midX = (p1[0] + p2[0]) / 2;
                pts.add(new double[] { midX, p1[1] });
                pts.add(new double[] { midX, p2[1] });
            }
        } else if (!srcHorizontal && !tgtHorizontal) {
            if (Math.abs(p1[0] - p2[0]) > 10) {
                double midY = (p1[1] + p2[1]) / 2;
                pts.add(new double[] { p1[0], midY });
                pts.add(new double[] { p2[0], midY });
            }
        } else {
            if (srcHorizontal) {
                pts.add(new double[] { p2[0], p1[1] });
            } else {
                pts.add(new double[] { p1[0], p2[1] });
            }
        }
        pts.add(p2);

        lines.add("      <bpmndi:BPMNEdge id=\"" + esc(f.getId()) +
                "_di\" bpmnElement=\"" + esc(f.getId()) + "\">");
        for (double[] pt : pts) {
            lines.add("        <di:waypoint x=\"" + (int) Math.round(pt[0]) +
                    "\" y=\"" + (int) Math.round(pt[1]) + "\" />");
        }
        lines.add("      </bpmndi:BPMNEdge>");
    }

    private double[] getPoint(ShapeInfo box, String face) {
        switch (face) {
            case "RIGHT":
                return new double[] { box.getX() + box.getW(), box.getY() + box.getH() / 2 };
            case "LEFT":
                return new double[] { box.getX(), box.getY() + box.getH() / 2 };
            case "BOTTOM":
                return new double[] { box.getX() + box.getW() / 2, box.getY() + box.getH() };
            case "TOP":
                return new double[] { box.getX() + box.getW() / 2, box.getY() };
            default:
                return new double[] { box.getX(), box.getY() };
        }
    }

    // =====================================================================
    // Utility helpers
    // =====================================================================

    private String extractAttr(String attrs, String name) {
        Matcher m = Pattern.compile("\\b" + name + "=\"([^\"]*)\"").matcher(attrs);
        return m.find() ? m.group(1) : null;
    }

    private Integer extractIntAttr(String attrs, String name) {
        String v = extractAttr(attrs, name);
        if (v == null)
            return null;
        try {
            return (int) Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<String> findAll(String regex, String text) {
        List<String> results = new ArrayList<>();
        Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(text);
        while (m.find()) {
            results.add(m.group(1));
        }
        return results;
    }

    private String esc(String text) {
        if (text == null)
            return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String optional(String s) {
        return s != null ? s : "";
    }

    private String shortUuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 7);
    }
}
