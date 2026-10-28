# 技能：资源与预约查询（query）
> 适用场景：用户问"有哪些资源 / 哪个有空 / 某天几点被占用 / 我的预约"。

## 接口速查
| 工具 | 调用方法 | 参数（*必填） | 返回 |
| --- | --- | --- | --- |
| query_resources | query_resources(type, keyword) | type*: 1教室 2实验室 3图书馆 4体育馆（可选）；keyword: 名称模糊词（可选） | resources[] = {id, name, type, building, openTime, closeTime, capacity} |
| find_free_resources | find_free_resources(date, start, end, type, campus, building, roomType, keyword) | date*: yyyy-MM-dd；start/end: "HH:mm"（可省略，默认现在起 2 小时）；type: 1/2/3/4；其余可选 | total；freeResources[] = {id, name, resourceType, campus, building, roomType, roomNo, capacity, unit, openTime, closeTime, remaining, freeUntil} |
| get_busy_slots | get_busy_slots(resourceId, date) | resourceId*: 整数；date*: yyyy-MM-dd | openTime/closeTime、busySlots[] = {kind, label, start, end, headcount?}（kind: course 上课 / reservation 已预约 / closure 闭馆）、freeSlots[] = ["08:00-10:45", ...] |
| list_my_reservations | list_my_reservations(status) | status: 1待打卡 2使用中 3已完成 4已取消 5爽约（可选） | reservations[] = {id, resourceId, resourceName, start, end, status} |

## 使用规范
1. 凡涉及具体资源名称、数量、空闲时段、预约记录，**必须先调接口**，只引用接口返回的数据，严禁编造。
2. 用户给了“地点类型 + 时间”，直接 `find_free_resources` 一次拿全该时段**真正可用**的资源，不要先 `query_resources` 再自己猜时间占不占。
3. 只有用户锁定到某个具体资源时，才用 `get_busy_slots` 看它当天的 `freeSlots`；`busySlots` 里 course 是上课、reservation 是已被约、closure 是闭馆，**三类都算不可用**，不能因为“没有预约记录”就说它空着。
4. 问“我的预约”：直接 list_my_reservations，系统自动按当前登录用户过滤，不要向用户索要用户 ID。
5. resourceId 一律来自接口的真实返回，禁止猜测或沿用历史对话中不存在的 id。
