# 技能：预约与取消（reservation）
> 适用场景：用户表达"预约 / 订 / 约 / 取消"意图。两个接口都**只生成草案**——前端弹确认卡片、用户点击后才真正执行，不存在误操作风险，应大胆调用。

## 接口速查
| 工具 | 调用方法 | 参数（*必填） | 产出草案 |
| --- | --- | --- | --- |
| propose_reservation | propose_reservation(resourceId, startTime, endTime, headcount, remark) | resourceId*；startTime* / endTime*: "yyyy-MM-dd HH:mm:ss"（空格分隔，不是 T）；headcount: 默认 1；remark: 可选 | create_reservation（确认卡片 → POST /api/reservations） |
| propose_cancel | propose_cancel(reservationId) | reservationId*: 来自 list_my_reservations 的真实 id | cancel_reservation（确认卡片 → POST /api/reservations/{id}/cancel） |

## 标准流程（预约）
1. 先用 `find_free_resources(date, start, end, type/campus/building)` 拿该时段**真正可用**的资源（服务端已排除上课、已被预约、闭馆、维修）；
2. 从返回的 freeResources 里挑资源；若要锁定到某个具体资源，用 `get_busy_slots` 看它当天的 freeSlots/busySlots；
3. `propose_reservation` 生成草案（默认 headcount=1），不要再向用户复述确认。

## 冲突与调整
- `propose_reservation` 会先做一次真实可用性判定：被占用/闭馆/超出开放时间的时段会返回 `status=blocked` 且**不出草案**。此时必须换时段或换资源重试，禁止把同一个被占用的时段反复推给用户。
- 需要微调时，改 start/end 后用 `find_free_resources` 重新确认，再生成草案，并在回复中说明调整了什么。
- 取消流程：先 list_my_reservations 定位 reservationId，再 propose_cancel；只允许操作本人预约。

## 硬性约束（服务端会真实校验，违反将下单失败）
- 实验室/体育馆最多提前 7 天，图书馆最多提前 1 天（即"明天"），教室无需预约；
- 预约时段必须落在资源 openTime~closeTime 内；同一时段每人只能有一个预约；
- 命中闭馆（总体或类型闭馆时段）时普通用户不可预约；
- 体育馆按容量累加：同一时段 headcount 之和不得超过 capacity，超过需减人。
