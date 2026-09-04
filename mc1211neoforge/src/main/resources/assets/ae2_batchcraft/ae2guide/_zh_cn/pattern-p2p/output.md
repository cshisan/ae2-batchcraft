---
navigation:
  parent: index.md
  title: 样板P2P通道(输出)
  icon: pattern_p2p_tunnel_output
  position: 21
item_ids:
- ae2_batchcraft:pattern_p2p_tunnel_output
---

# 样板P2P通道(输出)

<RecipeFor id="ae2_batchcraft:pattern_p2p_tunnel_output" />

为正面的机器处理任务，按样板编码的方向插入材料，并把产物返回样板供应器。

| 属性 | 值 |
| --- | --- |
| AE 频道 | `0` |
| 频率 | 必须配置 |
| 活动任务 | 任务返回和提取需要 |
| 支持传输 | AE 兼容的物品和流体 |
| 暂存任务上限 | 最多 `64` 个兼容任务 |

<GameScene zoom="6" background="transparent">
  <ImportStructure src="../assets/assemblies/output-machine.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 材料插入

自动方向会从输出端接触机器的面插入。样板显式编码的方向是绝对世界方向；如果该面拒绝资源，输出端不会回退到接触面。

目标只接收部分资源时，余量保持待下发并继续重试。存在待下发材料时，端点不能接收不兼容的新任务。

完整下发要求该端点接收整次处理推送。批次分发会根据当前容量分配整数份材料，该端点一次接收本轮分配到的聚合材料。

## 产物返回

机器或管道可主动向输出端只入不出的返回能力推送产物。输入端开启提取后，输出端也可主动从相邻机器拉取；两条路径都应用任务的返回配置。

## 配置与供能

输出端 GUI 直接显示“输出端分页分组”。“通用配置”包含产物返回模式、提取间隔和提取数量，并在标题行右侧显示“同步”开关；“输出端通用配置”包含当前输出端的产物提取开关。任务重置位于右侧功能栏。

开启同步时，输入端广播会覆盖输出端本地的产物返回模式、产物提取开关、提取间隔和提取数量，对应控件不可编辑。关闭同步后会保留最后一次同步到的值并忽略后续广播；再次开启同步会立即应用当前输入端配置。新的提取即使在无阻挡模式下也要求存在活动且可运行任务；已经拉出并进入恢复队列的资源可在任务结束后继续排空。

已配置且节点活动的输出端也会持续接收样板P2P通道(能量)的 FE，并传给相邻机器；不需要活动合成任务。

另见[批次分发](batch-distribution.md)、[材料输出方向](../troubleshooting/material-directions.md)和[端点主动提取](../product-return/endpoint-extraction.md)。
