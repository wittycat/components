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
public class FileReadTool implements AgentTool {

    private final AgentConfig agentConfig;

    @Override
    public String getName() {
        return "read_file";
    }

    @Override
    public String getDescription() {
        return "读取工作目录中指定文件的内容。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "path", Map.of("type", "string", "description", "相对于工作目录的文件路径")
        ));
        schema.put("required", List.of("path"));
        return schema;
    }

    @Override
    public String execute(Map<String, Object> arguments) throws Exception {
        String relativePath = (String) arguments.get("path");
        Path filePath = resolveSafePath(relativePath);

        if (!Files.exists(filePath)) {
            return "错误：文件不存在 - " + relativePath;
        }
        if (!Files.isRegularFile(filePath)) {
            return "错误：路径不是文件 - " + relativePath;
        }

        String content = Files.readString(filePath);
        if (content.length() > 10000) {
            content = content.substring(0, 10000) + "\n...(内容已截断)";
        }
        return content;
    }

    private Path resolveSafePath(String relativePath) {
        Path workspace = Paths.get(agentConfig.getWorkspaceDir()).toAbsolutePath().normalize();
        Path resolved = workspace.resolve(relativePath).normalize();
        if (!resolved.startsWith(workspace)) {
            throw new SecurityException("不允许访问工作目录外的文件");
        }
        return resolved;
    }
}
