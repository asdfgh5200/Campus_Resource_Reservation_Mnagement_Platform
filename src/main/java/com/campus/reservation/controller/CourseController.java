package com.campus.reservation.controller;

import com.campus.reservation.common.Result;
import com.campus.reservation.common.UserContext;
import com.campus.reservation.dto.CourseImportDTO;
import com.campus.reservation.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    /** 导入课表（JSON 数组），单条填写也走此接口 */
    @PostMapping("/import")
    public Result<Integer> importCourses(@RequestBody List<CourseImportDTO> list) {
        UserContext.requireAdmin();
        return Result.ok(courseService.importCourses(list));
    }

    /** 课表列表（管理端） */
    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) Long roomId,
                                                  @RequestParam(required = false) Integer dayOfWeek,
                                                  @RequestParam(required = false) String building) {
        UserContext.requireAdmin();
        return Result.ok(courseService.list(roomId, dayOfWeek, building));
    }

    /** 删除课表记录（管理端） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        UserContext.requireAdmin();
        courseService.delete(id);
        return Result.ok();
    }

    /** 查询教室当日课表与空闲时段（用户端展示） */
    @GetMapping("/available")
    public Result<Map<String, Object>> available(@RequestParam Long roomId,
                                                 @RequestParam(required = false)
                                                 @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        return Result.ok(courseService.available(roomId, date));
    }
}
