package com.core.test0812.interfaces;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.core.foundation.model.Result;
import com.core.test0812.application.Test0812ApplicationService;
import com.core.test0812.application.dto.Test0812DTO;
import com.core.test0812.domain.model.Test0812Entity;
import com.core.test0812.interfaces.request.CreateTest0812Request;
import com.core.test0812.interfaces.request.UpdateTest0812Request;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * 0812测试表 - 接口层
 */
@Tag(name = "0812测试表", description = "t_0812 增删改查接口")
@RestController
@RequestMapping("/api/test0812")
public class Test0812Controller {

    private final Test0812ApplicationService test0812ApplicationService;

    public Test0812Controller(Test0812ApplicationService test0812ApplicationService) {
        this.test0812ApplicationService = test0812ApplicationService;
    }

    @Operation(summary = "分页查询")
    @GetMapping
    public Result<Page<Test0812DTO>> page(
            @Parameter(description = "页码", example = "1")
            @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数", example = "10")
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(test0812ApplicationService.page(pageNum, pageSize));
    }

    @Operation(summary = "根据ID查询")
    @GetMapping("/{id}")
    public Result<Test0812DTO> getById(
            @Parameter(description = "主键ID", example = "1")
            @PathVariable Long id) {
        return Result.success(test0812ApplicationService.getById(id));
    }

    @Operation(summary = "新增")
    @PostMapping
    public Result<Test0812DTO> create(@RequestBody CreateTest0812Request request) {
        Test0812Entity entity = new Test0812Entity();
        entity.setName(request.name());
        entity.setDescription(request.description());
        return Result.success(test0812ApplicationService.create(entity));
    }

    @Operation(summary = "修改")
    @PutMapping("/{id}")
    public Result<Test0812DTO> update(
            @Parameter(description = "主键ID", example = "1")
            @PathVariable Long id,
            @RequestBody UpdateTest0812Request request) {
        Test0812Entity entity = new Test0812Entity();
        entity.setName(request.name());
        entity.setDescription(request.description());
        return Result.success(test0812ApplicationService.update(id, entity));
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Parameter(description = "主键ID", example = "1")
            @PathVariable Long id) {
        test0812ApplicationService.delete(id);
        return Result.success();
    }
}
