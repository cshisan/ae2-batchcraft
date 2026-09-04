---
navigation:
  parent: unit/index.md
  title: 单元管理器
  icon: pattern_p2p_unit_manager
  position: 10
item_ids:
- ae2_batchcraft:pattern_p2p_unit_manager
- ae2_batchcraft:white_pattern_p2p_unit_manager
- ae2_batchcraft:light_gray_pattern_p2p_unit_manager
- ae2_batchcraft:gray_pattern_p2p_unit_manager
- ae2_batchcraft:black_pattern_p2p_unit_manager
- ae2_batchcraft:lime_pattern_p2p_unit_manager
- ae2_batchcraft:yellow_pattern_p2p_unit_manager
- ae2_batchcraft:orange_pattern_p2p_unit_manager
- ae2_batchcraft:brown_pattern_p2p_unit_manager
- ae2_batchcraft:red_pattern_p2p_unit_manager
- ae2_batchcraft:pink_pattern_p2p_unit_manager
- ae2_batchcraft:magenta_pattern_p2p_unit_manager
- ae2_batchcraft:purple_pattern_p2p_unit_manager
- ae2_batchcraft:blue_pattern_p2p_unit_manager
- ae2_batchcraft:light_blue_pattern_p2p_unit_manager
- ae2_batchcraft:cyan_pattern_p2p_unit_manager
- ae2_batchcraft:green_pattern_p2p_unit_manager
---

# 样板 P2P 单元管理器

<RecipeFor id="ae2_batchcraft:pattern_p2p_unit_manager" />

加入输入端的端点池，每次协调一个单元处理任务。

| 属性 | 值 |
| --- | --- |
| AE 频道 | `0` |
| 样板 P2P 频率 | 接收任务必须配置 |
| 单元身份 | 每个所属端口必须绑定 |
| 并发任务 | `1` |
| 颜色 | 福鲁伊克斯及 `16` 种线缆颜色 |

## 安装与绑定

将管理器作为线缆中心安装在 AE 子网上。目标方块不能已有中心线缆、线缆部件或伪装板。先向管理器加载输入端频率，再把管理器身份保存到记忆卡并加载到所有端口。

颜色遵循 AE 线缆连接规则，只决定可连接的线缆颜色，不代表端口归属。

## 接收任务

接收前，管理器确认样板中每种材料都有可兼容且能接受完整资源的绑定端口。**普通**、**掉落**和**放置**形式分别选择传输、投掷和放置端口。

接收后，待下发材料会持续记录直到发送完成。主产物与待下发材料分别完成，因此产物提前返回不会丢失尚未发送的材料。

## 配置

“同步”开关位于共享“通用配置”的标题行右侧。管理器始终保留本地值；开启时，输入端广播会覆盖返回、提取间隔和数量、单端口单槽、传输、破坏、红石、脉冲以及单元能源端口分配设置；关闭时忽略广播并继续使用本地值，再次开启会立即应用当前输入端配置。“单元通用配置”只包含单端口单槽设置。

传输、破坏、红石和能量四个单元端口分页共用一个左侧按钮，点击时按此顺序循环切换。

“单元端口（能量）”配置只控制该管理器收到 FE 后，向所属多个能源端口进行的第二级分配。即使管理器没有任务，该设置仍会生效。

管理器的输出侧端口可以单独设置优先级。输出过滤标记和 AE2 反相卡在输出端口的优先级界面中配置；反相卡的行为与 AE2 原版过滤升级一致，会反转标记过滤结果。

输入侧端口使用独立的返回过滤界面，同样包含材料标记和可选反相卡。配方查看器的拖放功能可用时，可以把物品或流体拖入输入侧、输出侧配置界面的标记槽。

管理器级的**单端口单槽**设置与传输端口的输出模式相互独立。**全部启用**会让每个输出端口只服务一个编码样板槽位，**全部关闭**允许槽位共享，**跟随端口配置**则使用各输出端口自己的开关。只有选择跟随端口配置时，端口自己的开关才可编辑。

传输、投掷和放置属于输出侧端口：它们把编码材料从 AE 网络送入机器或世界。返回、收集、破坏和提取属于输入侧端口：它们从机器或世界接收产物，并受活动任务的返回模式和严格模式控制。严格模式也适用于普通输出端的产物返回。

## 重置

重置只清除该单元的活动状态和待下发材料。

> 待下发材料会永久销毁。已经进入 AE2 返回路径的资源不会因重置而重新生成。

另见[频率绑定](../getting-started/frequency.md)及本页下方直接列出的单元端口。
