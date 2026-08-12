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

将一个样板供应器收到的完整处理任务分配给多台机器。普通输出端直接负责一台机器；需要多种材料输出或世界交互时，则由单元管理器协调不同功能端口。

| 属性 | 行为 |
| --- | --- |
| 输入端频道占用 | `1` 个 AE 频道 |
| 输出端与单元占用 | 不额外占用 AE 频道 |
| 分配单位 | 一个完整处理任务 |
| 可返回资源 | AE 存储 API 支持的物品和流体 |

请从**快速开始**搭建第一个可用网络。鼠标悬浮本模组物品并按 AE2 指南键 `G`，可直接打开该物品对应的页面。

<SubPages />
