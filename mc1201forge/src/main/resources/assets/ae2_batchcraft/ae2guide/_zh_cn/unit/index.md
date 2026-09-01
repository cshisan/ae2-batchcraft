---
navigation:
  parent: index.md
  title: 样板 P2P 单元
  icon: pattern_p2p_unit_manager
  position: 30
---

# 样板 P2P 单元

一个单元由一个管理器和任意数量的直属功能端口组成。管理器接收一个完整处理任务；端口负责发送材料、与世界交互、返回产物、输出红石或输送 FE。

<GameScene zoom="3.5" interactive={true}>
  <ImportStructure src="../assets/assemblies/unit-overview.snbt" />
  <IsometricCamera yaw="215" pitch="25" />
</GameScene>

| 端口组 | 任务条件 |
| --- | --- |
| 传输、投掷、放置 | 单元任务下发材料期间工作 |
| 返回、收集、破坏、提取 | 为可运行的单元任务返回产物 |
| 红石 | 单元任务可运行期间输出 |
| 能源 | 有效绑定管理器后持续工作 |

能源端口是单元任务门控的例外。管理器没有合成任务时，它也不会停止供能。

<SubPages />
