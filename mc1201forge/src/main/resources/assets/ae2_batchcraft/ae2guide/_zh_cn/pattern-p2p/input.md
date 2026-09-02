---
navigation:
  parent: index.md
  title: 样板P2P通道(输入)
  icon: pattern_p2p_tunnel_input
  position: 20
item_ids:
- ae2_batchcraft:pattern_p2p_tunnel_input
---

# 样板P2P通道(输入)

<RecipeFor id="ae2_batchcraft:pattern_p2p_tunnel_input" />

从正面的样板供应器接收完整处理任务，并在同频率的普通输出端和单元管理器之间分配。

| 属性 | 值 |
| --- | --- |
| AE 频道 | `1` |
| 频率 | 必须配置；`0000` 表示停用 |
| 相邻方块 | 样板供应器输出面 |
| 任务拆分 | 只在完整任务之间分配 |
| 任务分配方式 | 轮询、随机或优先 |

<GameScene zoom="6" background="transparent">
  <ImportStructure src="../assets/assemblies/input-provider.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 任务分配

右侧功能栏的任务分配按钮控制端点选择方式。**轮询**按照稳定顺序轮流选择端点；**随机**在每次分配时独立选择一个当前可用端点，允许连续选中同一端点；**优先**在每次分配时都从第一个端点开始，仅当前面的端点不能接受任务时才继续尝试后面的端点。

离线、未加载、忙碌、不在同一网络或无法接收完整材料计划的端点不可用。选中的端点接收该次处理推送的全部数量。

## 通用配置

输入端提供以下默认值：

- 产物返回模式。
- 轮询、随机或优先任务分配。
- 是否允许普通输出端主动提取产物。
- 普通输出端和单元提取端口的提取间隔与数量。
- 单元破坏回收和红石行为。

任务执行期间修改这些配置也会动态同步。单元管理器及其绑定端口会应用后续修改，无需重新下单。

普通输出端和单元管理器开启同步时跟随对应设置；关闭同步后，本地配置才会生效。

## 重置任务状态

从输入端重置，会影响当前频率和当前 AE 网络中所有已加载的普通输出端和单元管理器。

> 重置会永久销毁这些端点中仍待下发的材料。未加载端点无法远程重置。

另见[样板 P2P 输出端](output.md)、[单元管理器](../unit/manager.md)和[产物返回与提取](../product-return/index.md)。
