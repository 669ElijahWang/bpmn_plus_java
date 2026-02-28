package com.bpmnplus.cli;

import com.bpmnplus.service.BpmnConvertService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Optional command-line runner.
 * If command-line args are provided (file paths or directories), processes them
 * and converts BPMN files, mirroring the Python __main__ block in
 * convert_bpmn.py.
 * When no args are given (normal Spring Boot start), this runner does nothing.
 */
@Component
public class BpmnCliRunner implements CommandLineRunner {

    private final BpmnConvertService convertService;

    public BpmnCliRunner(BpmnConvertService convertService) {
        this.convertService = convertService;
    }

    @Override
    public void run(String... args) throws Exception {
        // Only activate when explicit file/dir args are passed via --convert=
        // Example: java -jar bpmn-plus.jar --convert=file1.bpmn --convert=./bpmn_dir
        boolean hasConvertArgs = false;
        for (String arg : args) {
            if (arg.startsWith("--convert=")) {
                hasConvertArgs = true;
                String path = arg.substring("--convert=".length());
                processPath(path);
            }
        }
        if (hasConvertArgs) {
            System.out.println("CLI conversion completed.");
        }
    }

    private void processPath(String pathStr) {
        File file = new File(pathStr);
        if (file.isDirectory()) {
            File[] bpmnFiles = file.listFiles((dir, name) -> name.endsWith(".bpmn") && !name.contains("_camunda"));
            if (bpmnFiles != null) {
                for (File f : bpmnFiles) {
                    convertFile(f);
                }
            }
        } else {
            convertFile(file);
        }
    }

    private void convertFile(File inputFile) {
        try {
            String content = Files.readString(inputFile.toPath(), StandardCharsets.UTF_8);
            String xml = convertService.performConversion(content, inputFile.getName());
            if (xml != null) {
                String baseName = inputFile.getName();
                int dotIdx = baseName.lastIndexOf('.');
                String newName = (dotIdx > 0 ? baseName.substring(0, dotIdx) : baseName) + "_camunda.bpmn";
                Path outPath = inputFile.toPath().getParent().resolve(newName);
                Files.writeString(outPath, xml, StandardCharsets.UTF_8);
                System.out.println("[OK] " + inputFile.getPath() + " -> " + outPath);
            }
        } catch (IOException e) {
            System.err.println("Error converting " + inputFile.getPath() + ": " + e.getMessage());
        }
    }
}
