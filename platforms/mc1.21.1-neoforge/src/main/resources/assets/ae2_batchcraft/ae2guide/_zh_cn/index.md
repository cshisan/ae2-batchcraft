---
navigation:
  title: AE2 批量工艺
  icon: pattern_p2p_tunnel_input
  position: 1000
---

# AE2 批量工艺

<Row gap="16">
  <ItemImage id="ae2_batchcraft:pattern_p2p_tunnel_input" scale="3" />
  <ItemImage id="ae2_batchcraft:pattern_p2p_tunnel_output" scale="3" />
  <ItemImage id="ae2_batchcraft:pattern_p2p_unit_manager" scale="3" />
  <ItemImage id="ae2_batchcraft:component_placer" scale="3" />
</Row>

将一个 AE2 样板供应器收到的处理任务分配给多个端点。普通输出端直接负责一个相邻库存；需要多种材料输出或世界交互时，则由单元管理器协调不同功能端口。

| 属性 | 行为 |
| --- | --- |
| 输入端频道占用 | `1` 个 AE 频道 |
| 输出端与单元占用 | 不额外占用 AE 频道 |
| 分发模式 | 完整下发、批次分发 |
| 可返回资源 | AE 存储 API 支持的物品和流体 |

请从**快速开始**搭建第一个可用网络。鼠标悬浮本模组物品并按 AE2 指南键 `G`，可直接打开该物品对应的页面。拆分大型样板前请先阅读[批次分发](pattern-p2p/batch-distribution.md)。

<SubPages />
