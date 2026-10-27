package com.campus.reservation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.reservation.common.BizException;
import com.campus.reservation.dto.HolidayDTO;
import com.campus.reservation.entity.Holiday;
import com.campus.reservation.mapper.HolidayMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HolidayService {

    private final HolidayMapper holidayMapper;

    public List<Holiday> list() {
        return holidayMapper.selectList(Wrappers.<Holiday>lambdaQuery()
                .orderByDesc(Holiday::getStartDate));
    }

    public Holiday add(HolidayDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank()
                || dto.getStartDate() == null || dto.getEndDate() == null) {
            throw BizException.badRequest("缺少必要参数");
        }
        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw BizException.badRequest("结束日期不能早于开始日期");
        }
        Holiday holiday = new Holiday();
        holiday.setName(dto.getName());
        holiday.setStartDate(dto.getStartDate());
        holiday.setEndDate(dto.getEndDate());
        holidayMapper.insert(holiday);
        return holiday;
    }

    public void delete(Long id) {
        if (holidayMapper.selectById(id) == null) {
            throw BizException.notFound("节假日不存在");
        }
        holidayMapper.deleteById(id);
    }
}
