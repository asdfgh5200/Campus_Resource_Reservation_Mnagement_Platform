package com.campus.reservation.controller;

import com.campus.reservation.common.Result;
import com.campus.reservation.common.UserContext;
import com.campus.reservation.dto.CourseImportDTO;
import com.campus.reservation.service.AvailabilityService;
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
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;
    private final AvailabilityService availabilityService;

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
                                                  @RequestParam(required = false) String campus,
                                                  @RequestParam(required = false) String building) {
        UserContext.requireAdmin();
        return Result.ok(courseService.list(roomId, dayOfWeek, building, campus));
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

    /**
     * 空闲资源查询：按日期 + 时段 + 类型 + 校区/楼栋/细分类型/关键词筛选真正可用的资源。
     * type 缺省 1（教室）；实验室/体育馆也走这个接口（同一套可用性口径），体育馆额外返回 remaining（剩余数量）。
     * 缺省时段为「当前时间向上取整到半点起 2 小时」；building 与 roomType 可重复传参或逗号分隔。
     */
    @GetMapping("/free-rooms")
    public Result<List<Map<String, Object>>> freeRooms(
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime start,
            @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime end,
            @RequestParam(required = false) String campus,
            @RequestParam(required = false) List<String> building,
            @RequestParam(required = false) String floor,
            @RequestParam(required = false) List<String> roomType,
            @RequestParam(required = false) String keyword) {
        return Result.ok(availabilityService.freeResources(date, start, end, type, campus, building, roomType, floor, keyword));
    }
}
