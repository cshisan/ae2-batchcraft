---
navigation:
  parent: index.md
  title: 提取单元端口
  icon: pp2mp_unit_port_extract
  position: 100
item_ids:
- ae2_batchcraft:pp2mp_unit_port_extract
---

# 提取单元端口

<ItemImage id="ae2_batchcraft:pp2mp_unit_port_extract" scale="3" />

主动从相邻机器或库存获取任务产物，适合不能自动输出产物的机器。<br />&#160;<br />

## 使用

**连接机器：** [绑定单元管理器](manager.md)，正面对准机器允许提取的一面。<br />&#160;<br />

**过滤与速度：** 可设置产物过滤；间隔和数量在管理器通用配置中设置，最小间隔为 `1 tick`。<br />&#160;<br />

## 特殊使用说明

**需要任务：** 单元需有可运行任务，提取的资源还要符合管理器的[返回模式](../configuration.md)。<br />&#160;<br />

**提取数量：** 设置的是每轮上限。来源不足、机器限制或返回目标容量不足时，实际提取量会减少。<br />&#160;<br />

**开关区别：** 普通输出端的“产物提取”开关不控制本端口。
