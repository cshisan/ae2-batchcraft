---
navigation:
  parent: index.md
  title: 红石单元端口
  icon: pp2mp_unit_port_redstone
  position: 130
item_ids:
- ae2_batchcraft:pp2mp_unit_port_redstone
---

# 红石单元端口

<ItemImage id="ae2_batchcraft:pp2mp_unit_port_redstone" scale="3" />

在单元任务期间输出红石信号，用于启动或控制相邻设备。<br />&#160;<br />

## 使用

**配置位置：** [绑定单元管理器](manager.md)，在管理器红石分页设置模式、强度、脉冲宽度和周期，时间单位为 `tick`。<br />&#160;<br />

**单次触发：** 每笔任务输出一次脉冲。<br />&#160;<br />

**周期脉冲：** 任务期间按所设周期重复输出。<br />&#160;<br />

**持续输出：** 任务期间保持红石信号。<br />&#160;<br />

## 特殊使用说明

**触发条件：** 信号跟随单元任务，持续资源供应不会单独触发。<br />&#160;<br />

**并发任务：** 关闭[阻挡模式](../configuration.md)后，多笔任务的脉冲可能重叠。机器需要每笔任务独立触发时，请保持阻挡开启。
