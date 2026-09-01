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

从正面的 AE2 样板供应器接收处理任务，并在同频率的普通输出端和单元管理器之间分配。

| 属性 | 值 |
| --- | --- |
| AE 频道 | `1` |
| 频率 | 必须配置；`0000` 表示停用 |
| 相邻方块 | 样板供应器输出面 |
| 分发模式 | 完整下发或批次分发 |

<GameScene zoom="6" background="transparent">
  <ImportStructure src="../assets/assemblies/input-provider.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## 任务分配

在**完整下发**中，输入端按轮询顺序遍历可用端点。离线、未加载、忙碌、不在同一 AE 网络或无法接收完整材料计划的端点会被跳过。选中的一个端点接收整次处理推送。

在**批次分发**中，输入端只向相邻 AE2 样板供应器请求下一份已规划材料，并按已配置最小份数的整数倍分配。尚未下发的材料继续留在样板供应器中，直到端点重新具备容量。启用前请先阅读[批次分发](batch-distribution.md)。

## 通用配置

输入端提供以下默认值：

- 产物返回模式。
- 完整下发或批次分发。
- 是否允许普通输出端主动提取产物。
- 普通输出端和单元提取端口的提取间隔与数量。
- 单元破坏回收和红石行为。

任务执行期间修改这些配置也会动态同步。单元管理器及其绑定端口会应用后续修改，无需重新下单。

普通输出端和单元管理器开启同步时跟随对应设置；关闭同步后，本地配置才会生效。

## 重置任务状态

从输入端重置，会影响当前频率和当前 AE 网络中所有已加载的普通输出端和单元管理器。

> 重置会永久销毁这些端点中仍待下发的材料。未加载端点无法远程重置。

另见[批次分发](batch-distribution.md)、[样板 P2P 输出端](output.md)、[单元管理器](../unit/manager.md)和[产物返回与提取](../product-return/index.md)。
