---
navigation:
  parent: unit/index.md
  title: 能源端口
  icon: pattern_p2p_unit_port_energy
  position: 100
item_ids:
- ae2_batchcraft:pattern_p2p_unit_port_energy
---

# 单元端口（能源）

<RecipeFor id="ae2_batchcraft:pattern_p2p_unit_port_energy" />

持续接收同一 AE 网络中样板P2P通道(能量)提供的 FE，并输送给端口正面的设备。

| 属性 | 值 |
| --- | --- |
| 单元身份 | 必须绑定 |
| 活动合成任务 | **不需要** |
| 资源 | FE |
| 目标 | 端口正面的相邻设备 |
| 配置来源 | 所属管理器的能源分配模式 |

只要绑定有效管理器且相邻设备可接收 FE，端口就会参与供能。管理器可以处于空闲且没有任何处理任务，供能仍会持续。

管理器以单元身份归组能源端口，并提供分配模式。**均分**按需求分享可用能量；**轮询**在供能不足时轮换接收者优先级。该设置在当前 AE 网络中同步。

端口不会从 AE2 内部能源服务取电，只转发[样板P2P通道(能量)](../pattern-p2p/energy.md)提供的 FE。
