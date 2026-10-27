package com.campus.reservation.dto;

import com.campus.reservation.entity.Reservation;
import com.campus.reservation.entity.ReservationLog;
import com.campus.reservation.entity.Violation;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 单条预约的「当时情况」：预约本体（含资源）+ 该预约的完整操作时间线 + 由此产生的违规记录。
 * 用户端预约详情侧拉框、违规记录详情侧拉框都查这一份数据，保证双方看到的是同一段事实。
 */
@Data
@AllArgsConstructor
public class ReservationTraceVO {

    /** 预约本体，resource 已回填 */
    private Reservation reservation;

    /** 操作时间线（旧 → 新），含用户本人、管理员与系统自动三类操作人 */
    private List<ReservationLog> logs;

    /** 关联违规记录（violation.reservation_id = 预约 ID），含已撤销的 */
    private List<Violation> violations;
}
