---
navigation:
  parent: index.md
  title: 单元端口管理器
  icon: pp2mp_unit_manager_fluix
  position: 50
item_ids:
- ae2_batchcraft:pp2mp_unit_manager_fluix
- ae2_batchcraft:pp2mp_unit_manager_white
- ae2_batchcraft:pp2mp_unit_manager_light_gray
- ae2_batchcraft:pp2mp_unit_manager_gray
- ae2_batchcraft:pp2mp_unit_manager_black
- ae2_batchcraft:pp2mp_unit_manager_lime
- ae2_batchcraft:pp2mp_unit_manager_yellow
- ae2_batchcraft:pp2mp_unit_manager_orange
- ae2_batchcraft:pp2mp_unit_manager_brown
- ae2_batchcraft:pp2mp_unit_manager_red
- ae2_batchcraft:pp2mp_unit_manager_pink
- ae2_batchcraft:pp2mp_unit_manager_magenta
- ae2_batchcraft:pp2mp_unit_manager_purple
- ae2_batchcraft:pp2mp_unit_manager_blue
- ae2_batchcraft:pp2mp_unit_manager_light_blue
- ae2_batchcraft:pp2mp_unit_manager_cyan
- ae2_batchcraft:pp2mp_unit_manager_green
---

# 单元端口管理器

<ItemImage id="ae2_batchcraft:pp2mp_unit_manager_fluix" scale="3" />

把多个功能端口组合成一个加工单元，用于分别投料、回收产物，或配合世界加工与红石设备。<br />&#160;<br />

## 使用

**绑定单元：** 管理器、端口和输入端接入同一 AE 网络。先给管理器加载输入端频率，再用内存卡潜行右键管理器保存，右键所属端口加载。<br />&#160;<br />

**频率与单元 ID：** 频率用于选择输入端，单元 ID 用于区分管理器。多个管理器可共用频率，各单元应使用独立 ID。<br />&#160;<br />

**统一设置：** 管理器可集中配置端口，也可开启同步跟随输入端。端口关闭同步后可使用自己的设置。<br />&#160;<br />

## 投料模式

**配置入口：** “通用”分页中的“输出型端口-投料模式”，适用于传输、掉落和放置端口，四种模式只能选一种。<br />&#160;<br />

**普通：** 一个端口可投送多个材料槽、多种资源。<br />&#160;<br />

**槽位独占：** 每个端口只投送分配给它的一个材料槽。同槽材料可以分多次送出。<br />&#160;<br />

**单个物品：** 每个端口每笔任务最多投送一个物品，或 1000 mB 的同一种流体。分多次投送也不会增加本笔任务的限额。<br />&#160;<br />

**同类型：** 每个端口每笔任务只投送一种具体资源，可来自多个材料槽。圆石与橡木、水与熔岩，以及附加数据不同的物品分别计算。<br />&#160;<br />

## 特殊使用说明

**空槽占位：** 槽位独占下，最后一个非空材料槽之前的空槽也占端口，包括开头、中间和连续空槽；末尾空槽不占位。其他投料模式不为空槽占位。<br />&#160;<br />

**占位端口：** 空槽的[输出方式](../pattern.md)决定所需端口：普通用传输，掉落用掉落，放置用放置。空槽不会发送材料或执行世界操作。<br />&#160;<br />

**空槽示例：** `[A][空][B]` 全部使用普通输出方式，且传输端口均为槽位独占时，至少需要三个端口，中间端口不会收到材料。<br />&#160;<br />

**端口数量：** 现有端口无法分配整笔材料时不会接单。例如全部使用单个物品模式，投送两个物品至少需要两个端口。同类型模式也允许把同一种资源分给多个端口。<br />&#160;<br />

**模式生效：** 修改模式从下一笔任务生效。关闭[阻挡模式](../configuration.md)后，材料发完即可在满足接单条件时重新使用端口，无需等上一笔产物。<br />&#160;<br />

**端口过滤：** 标记不消耗样品；空过滤允许全部资源，反相卡排除标记资源。[供应端口](provider.md)还可设置目标库存数量。<br />&#160;<br />

**回收产物：** 传输、掉落和放置端口只负责投料，需要另配返回、提取、收集等产物获取端口。
