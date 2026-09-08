package com.core.userinfo.interfaces;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.core.foundation.model.Result;
import com.core.userinfo.application.UserInfoApplicationService;
import com.core.userinfo.application.dto.UserInfoDTO;
import com.core.userinfo.domain.model.UserInfoEntity;
import com.core.userinfo.interfaces.request.CreateUserInfoRequest;
import com.core.userinfo.interfaces.request.UpdateUserInfoRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * 用户信息表 - 接口层
 */
@Tag(name = "用户信息表", description = "t_user_info 增删改查接口")
@RestController
@RequestMapping("/api/userinfo")
public class UserInfoController {

    private final UserInfoApplicationService userInfoApplicationService;

    public UserInfoController(UserInfoApplicationService userInfoApplicationService) {
        this.userInfoApplicationService = userInfoApplicationService;
    }

    @Operation(summary = "分页查询")
    @GetMapping
    public Result<Page<UserInfoDTO>> page(
            @Parameter(description = "页码", example = "1")
            @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数", example = "10")
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(userInfoApplicationService.page(pageNum, pageSize));
    }

    @Operation(summary = "根据ID查询")
    @GetMapping("/{id}")
    public Result<UserInfoDTO> getById(
            @Parameter(description = "主键ID", example = "1")
            @PathVariable Long id) {
        return Result.success(userInfoApplicationService.getById(id));
    }

    @Operation(summary = "新增")
    @PostMapping
    public Result<UserInfoDTO> create(@RequestBody CreateUserInfoRequest request) {
        UserInfoEntity entity = new UserInfoEntity();
        entity.setName(request.name());
        entity.setDescription(request.description());
        return Result.success(userInfoApplicationService.create(entity));
    }

    @Operation(summary = "修改")
    @PutMapping("/{id}")
    public Result<UserInfoDTO> update(
            @Parameter(description = "主键ID", example = "1")
            @PathVariable Long id,
            @RequestBody UpdateUserInfoRequest request) {
        UserInfoEntity entity = new UserInfoEntity();
        entity.setName(request.name());
        entity.setDescription(request.description());
        return Result.success(userInfoApplicationService.update(id, entity));
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Parameter(description = "主键ID", example = "1")
            @PathVariable Long id) {
        userInfoApplicationService.delete(id);
        return Result.success();
    }
}
