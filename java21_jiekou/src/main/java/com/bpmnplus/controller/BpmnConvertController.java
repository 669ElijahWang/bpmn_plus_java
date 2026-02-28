package com.bpmnplus.controller;

import com.bpmnplus.model.ConvertResult;
import com.bpmnplus.service.BpmnConvertService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class BpmnConvertController {

    private final BpmnConvertService bpmnConvertService;

    @Autowired
    public BpmnConvertController(BpmnConvertService bpmnConvertService) {
        this.bpmnConvertService = bpmnConvertService;
    }

    /**
     * 单文件转换接口
     * 接收上传的 BPMN 文件，返回转换后的 BPMN XML 内容及状态。
     *
     * @param file BPMN 文件 (multipart/form-data)
     * @return ConvertResult 对象包含文件名和内容
     */
    @PostMapping("/convert")
    public ResponseEntity<ConvertResult> convertFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(new ConvertResult(null, "上传的文件为空", false));
        }

        try {
            String originalFileName = file.getOriginalFilename();
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);

            // 执行核心转换逻辑
            String convertedContent = bpmnConvertService.performConversion(content, originalFileName);

            if (convertedContent == null || convertedContent.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        new ConvertResult(originalFileName, "转换失败：无法从文件中解析出有效的流程", false));
            }

            // 生成新文件名
            String newFileName = "converted_" + (originalFileName != null ? originalFileName : "process.bpmn");
            return ResponseEntity.ok(new ConvertResult(newFileName, convertedContent, true));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(
                    new ConvertResult(null, "系统内部错误: " + e.getMessage(), false));
        }
    }
}
