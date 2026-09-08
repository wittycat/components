package com.wittycat.knowledgebase.controller;

import com.wittycat.knowledgebase.dto.KnowledgeDocumentDto;
import com.wittycat.knowledgebase.entity.KnowledgeDocument;
import com.wittycat.knowledgebase.rag.DocumentIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final DocumentIngestionService ingestionService;

    /** 上传文本文档到知识库 */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        log.info("[Knowledge] 收到文件上传请求, filename={}, size={} bytes", 
                file.getOriginalFilename(), file.getSize());
        try {
            KnowledgeDocument doc = ingestionService.ingestFile(file);
            log.info("[Knowledge] 文件上传成功, docId={}, filename={}", doc.getId(), doc.getFilename());
            return ResponseEntity.ok(Map.of(
                    "id", doc.getId(),
                    "filename", doc.getFilename(),
                    "message", "上传成功"
            ));
        } catch (IllegalArgumentException e) {
            log.warn("[Knowledge] 文件上传失败（参数错误）: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Knowledge] 文件上传失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "上传失败: " + e.getMessage()));
        }
    }

    /** 直接提交文本到知识库 */
    @PostMapping("/text")
    public ResponseEntity<?> ingestText(@RequestBody Map<String, String> body) {
        String filename = body.get("filename");
        String content = body.get("content");
        log.info("[Knowledge] 收到文本导入请求, filename={}, content长度={}", 
                filename, content != null ? content.length() : 0);
        try {
            KnowledgeDocument doc = ingestionService.ingestText(filename, content);
            log.info("[Knowledge] 文本导入成功, docId={}, filename={}", doc.getId(), doc.getFilename());
            return ResponseEntity.ok(Map.of(
                    "id", doc.getId(),
                    "filename", doc.getFilename(),
                    "message", "上传成功"
            ));
        } catch (IllegalArgumentException e) {
            log.warn("[Knowledge] 文本导入失败（参数错误）: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Knowledge] 文本导入失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "上传失败: " + e.getMessage()));
        }
    }

    /** 列出知识库文档（不含正文，避免内存溢出） */
    @GetMapping("/documents")
    public List<KnowledgeDocumentDto> listDocuments() {
        log.info("[Knowledge] 查询知识库文档列表");
        return ingestionService.listDocuments();
    }

    /** 删除知识库文档 */
    @DeleteMapping("/documents/{id}")
    public void deleteDocument(@PathVariable Long id) {
        log.info("[Knowledge] 删除知识库文档, docId={}", id);
        ingestionService.deleteDocument(id);
    }
}
