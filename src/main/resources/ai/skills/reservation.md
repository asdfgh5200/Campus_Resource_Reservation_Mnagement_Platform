# 技能：预约与取消（reservation）
> 适用场景：用户表达"预约 / 订 / 约 / 取消"意图。两个接口都**只生成草案**——前端弹确认卡片、用户点击后才真正执行，不存在误操作风险，应大胆调用。

## 接口速查
| 工具 | 调用方法 | 参数（*必填） | 产出草案 |
| --- | --- | --- | --- |
| propose_reservation | propose_reservation(resourceId, startTime, endTime, headcount, remark) | resourceId*；startTime* / endTime*: "yyyy-MM-dd HH:mm:ss"（空格分隔，不是 T）；headcount: 默认 1；remark: 可选 | create_reservation（确认卡片 → POST /api/reservations） |
| propose_cancel | propose_cancel(reservationId) | reservationId*: 来自 list_my_reservations 的真实 id | cancel_reservation（确认卡片 → POST /api/reservations/{id}/cancel） |

## 标准流程（预约）
1. query_resources 拿到候选 resourceId；
2. get_busy_slots 确认目标时段不冲突；
3. propose_reservation 直接生成草案（默认 headcount=1），不要再向用户复述确认。

## 冲突与调整
- 目标时段已被占用：自动向前后各扩展 30 分钟挑选接近的空闲时段生成草案，并在回复中说明调整了什么。
- 取消流程：先 list_my_reservations 定位 reservationId，再 propose_cancel；只允许操作本人预约。

## 硬性约束（服务端会真实校验，违反将下单失败）
- 实验室/体育馆最多提前 7 天，图书馆最多提前 1 天（即"明天"），教室无需预约；
- 预约时段必须落在资源 openTime~closeTime 内；同一时段每人只能有一个预约；
- 命中闭馆（总体或类型闭馆时段）时普通用户不可预约。
