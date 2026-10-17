package com.campus.reservation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.reservation.entity.Resource;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ResourceMapper extends BaseMapper<Resource> {

    /**
     * 预约并发控制的行级悲观锁：锁定资源行直到事务结束（SELECT ... FOR UPDATE）。
     * 同一资源的并发预约在此串行化，消除“查冲突→插入”的竞态窗口；锁为当前读，能看到已提交的冲突记录。
     */
    @Select("SELECT id FROM resource WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    Long lockForReservation(@Param("id") Long id);
}
