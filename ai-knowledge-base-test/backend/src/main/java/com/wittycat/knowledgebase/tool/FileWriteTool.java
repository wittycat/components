package com.wittycat.knowledgebase.tool;

import com.wittycat.knowledgebase.config.AgentConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class FileWriteTool implements AgentTool {

    private final AgentConfig agentConfig;

    @Override
    public String getName() {
        return "write_file";
    }

    @Override
    public String getDescription() {
        return "将内容写入工作目录中的指定文件。如果文件不存在则创建，存在则覆盖。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "path", Map.of("type", "string", "description", "相对于工作目录的文件路径"),
                "content", Map.of("type", "string", "description", "要写入的文件内容")
        ));
        schema.put("required", List.of("path", "content"));
        return schema;
    }

    @Override
    public String execute(Map<String, Object> arguments) throws Exception {
        String relativePath = (String) arguments.get("path");
        String content = (String) arguments.get("content");

        Path workspace = Paths.get(agentConfig.getWorkspaceDir()).toAbsolutePath().normalize();
        Files.createDirectories(workspace);

        Path filePath = workspace.resolve(relativePath).normalize();
        if (!filePath.startsWith(workspace)) {
            return "错误：不允许写入工作目录外的文件";
        }

        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
        return "文件写入成功: " + relativePath + " (" + content.length() + " 字符)";
    }
}
