package com.campus.reservation.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.reservation.common.Result;
import com.campus.reservation.common.UserContext;
import com.campus.reservation.entity.Resource;
import com.campus.reservation.service.ResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;

    @GetMapping("/page")
    public Result<Page<Resource>> page(@RequestParam(defaultValue = "1") Integer current,
                                       @RequestParam(defaultValue = "10") Integer size,
                                       @RequestParam(required = false) Integer type,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) Integer status,
                                       @RequestParam(required = false) String building,
                                       @RequestParam(required = false) String floor) {
        return Result.ok(resourceService.page(current, size, type, keyword, status, building, floor));
    }

    @GetMapping("/{id}")
    public Result<Resource> detail(@PathVariable Long id) {
        return Result.ok(resourceService.getById(id));
    }

    @PostMapping
    public Result<Resource> add(@RequestBody Resource resource) {
        UserContext.requireAdmin();
        return Result.ok(resourceService.add(resource));
    }

    @PutMapping
    public Result<Resource> update(@RequestBody Resource resource) {
        UserContext.requireAdmin();
        return Result.ok(resourceService.update(resource));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        UserContext.requireAdmin();
        resourceService.delete(id);
        return Result.ok();
    }

    @PutMapping("/{id}/status/{status}")
    public Result<Resource> changeStatus(@PathVariable Long id, @PathVariable Integer status) {
        UserContext.requireAdmin();
        return Result.ok(resourceService.changeStatus(id, status));
    }
}
