---
navigation:
  parent: getting-started/index.md
  title: 基础搭建
  icon: pattern_p2p_tunnel_input
  position: 10
---

# 基础搭建

<GameScene zoom="4" interactive={true}>
  <ImportStructure src="../assets/assemblies/basic-pattern-p2p.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 所需组件

| 组件 | 用途 |
| --- | --- |
| 样板供应器 | 存放处理样板并发起外部处理 |
| 样板 P2P 输入端 | 从样板供应器接收任务 |
| 样板 P2P 输出端 | 将分配到的材料发送给相邻库存 |
| AE 子网线缆 | 让输入端和输出端处于同一个 AE 网络 |
| 记忆卡 | 创建并复制 P2P 频率 |

## 搭建步骤

1. 在主合成网络放置样板供应器，并让其输出面朝向样板 P2P 输入端。
2. 将输入端接入 AE 子网。输入端会在该子网上占用 `1` 个频道。
3. 在每台机器前放置一个同网输出端，让输出端正面朝向机器。
4. 将所有端点绑定到同一个非零频率。
5. 把处理样板放入样板供应器并请求任务。

输入端向同频率的输出端和单元管理器分配任务，右侧功能栏可以选择轮询、随机或优先方式。完整下发把整次推送交给一个端点；批次分发遵守单独配置的份数限制。

## 首次运行检查

- 输入端、输出端和单元管理器位于同一个已加载 AE 网络。
- 输入端显示非零频率且节点活动。
- 每个输出端频率相同，正面朝向机器存储。
- 样板供应器的输出面直接朝向输入端。
- 处理样板声明了机器实际返回的产物。
- ME 存储有空间接收产物。

端点无法加入时查看[频率绑定](frequency.md)，拆分大型样板前查看[批次分发](../pattern-p2p/batch-distribution.md)，材料已输出但产物不返回时查看[产物返回流程](return-flow.md)。
