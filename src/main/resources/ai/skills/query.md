# 技能：资源与预约查询（query）
> 适用场景：用户问"有哪些资源 / 哪个有空 / 某天几点被占用 / 我的预约"。

## 接口速查
| 工具 | 调用方法 | 参数（*必填） | 返回 |
| --- | --- | --- | --- |
| query_resources | query_resources(type, keyword) | type*: 1教室 2实验室 3图书馆 4体育馆（可选）；keyword: 名称模糊词（可选） | resources[] = {id, name, type, building, openTime, closeTime, capacity} |
| get_busy_slots | get_busy_slots(resourceId, date) | resourceId*: 整数；date*: yyyy-MM-dd | busySlots[] = {start:"HH:mm", end:"HH:mm"}（已占用时段） |
| list_my_reservations | list_my_reservations(status) | status: 1待打卡 2使用中 3已完成 4已取消 5爽约（可选） | reservations[] = {id, resourceId, resourceName, start, end, status} |

## 使用规范
1. 凡涉及具体资源名称、数量、空闲时段、预约记录，**必须先调接口**，只引用接口返回的数据，严禁编造。
2. 问"XX 什么时候有空"：query_resources 找 id → get_busy_slots 查目标日期 → 用开放时段反推空闲。
3. 问"我的预约"：直接 list_my_reservations，系统自动按当前登录用户过滤，不要向用户索要用户 ID。
4. resourceId 一律来自 query_resources 的真实返回，禁止猜测或沿用历史对话中不存在的 id。
