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

## 产物返回

机器或管道可主动向输出端只入不出的返回能力推送产物。输出端开启提取后，也可主动从相邻机器拉取；两条路径都应用任务的返回配置。

## 配置与供能

输出端始终拥有本地的返回模式、产物提取、提取间隔和提取数量。开启同步时，输入端广播会覆盖这些本地值；关闭同步时，输出端忽略广播并使用本地值。因此关闭同步时由输出端自己的“开启/关闭”按钮决定是否提取，开启同步时按钮状态跟随输入端。新的提取只会在输出端存在活动且可运行任务时进行；无阻挡模式也不会绕过此要求。已经拉出但等待返回的资源会进入恢复队列，任务结束后仍可继续排空。

已配置且节点活动的输出端也会持续接收样板P2P通道(能量)的 FE，并传给相邻机器；不需要活动合成任务。

另见[材料输出方向](../troubleshooting/material-directions.md)和[端点主动提取](../product-return/endpoint-extraction.md)。
