package com.bpmnplus.controller;

import com.bpmnplus.model.ConvertResult;
import com.bpmnplus.service.BpmnConvertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Web controller providing the HTML frontend and the /convert API endpoint.
 * Equivalent to the FastAPI endpoints in app.py.
 */
@RestController
public class BpmnController {

    private static final Logger log = LoggerFactory.getLogger(BpmnController.class);

    private final BpmnConvertService convertService;

    public BpmnController(BpmnConvertService convertService) {
        this.convertService = convertService;
    }

    /**
     * Serve the main HTML page.
     */
    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> index() throws IOException {
        ClassPathResource resource = new ClassPathResource("static/index.html");
        String html;
        try (InputStream is = resource.getInputStream()) {
            html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        return ResponseEntity.ok(html);
    }

    /**
     * Batch convert uploaded BPMN files.
     */
    @PostMapping("/convert")
    public ResponseEntity<Map<String, Object>> batchConvert(
            @RequestParam("files") MultipartFile[] files) {

        List<ConvertResult> results = new ArrayList<>();
        for (MultipartFile file : files) {
            try {
                String content = new String(file.getBytes(), StandardCharsets.UTF_8);
                String converted = convertService.performConversion(content, file.getOriginalFilename());

                if (converted != null) {
                    String baseName = file.getOriginalFilename();
                    int dotIdx = baseName != null ? baseName.lastIndexOf('.') : -1;
                    String newName = (dotIdx > 0 ? baseName.substring(0, dotIdx) : baseName)
                            + "_camunda.bpmn";
                    results.add(new ConvertResult(newName, converted, true));
                    log.info("✓ {}", file.getOriginalFilename());
                } else {
                    results.add(new ConvertResult(file.getOriginalFilename(), "", false));
                    log.warn("✗ {}: no processes found", file.getOriginalFilename());
                }
            } catch (Exception e) {
                results.add(new ConvertResult(file.getOriginalFilename(), "", false));
                log.error("✗ {}: {}", file.getOriginalFilename(), e.getMessage(), e);
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("results", results);
        return ResponseEntity.ok(response);
    }
}
