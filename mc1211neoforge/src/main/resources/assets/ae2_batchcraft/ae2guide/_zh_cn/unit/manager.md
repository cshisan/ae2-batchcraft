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

开启同步时，管理器使用输入端的返回、提取间隔和数量、破坏回收及红石设置。关闭同步后使用本地配置。

能源分配模式在当前 AE 网络中同步。即使管理器没有任务，它仍影响所属能源端口。

管理器的输出侧端口可以单独设置优先级。输出过滤标记和 AE2 反相卡在输出端口的优先级界面中配置；反相卡的行为与 AE2 原版过滤升级一致，会反转标记过滤结果。

管理器级的**单端口单槽**设置与传输端口的输出模式相互独立。**全部启用**会让每个输出端口只服务一个编码样板槽位，**全部关闭**允许槽位共享，**跟随端口配置**则使用各输出端口自己的开关。只有选择跟随端口配置时，端口自己的开关才可编辑。

传输、投掷和放置属于输出侧端口：它们把编码材料从 AE 网络送入机器或世界。返回、收集、破坏和提取属于输入侧端口：它们从机器或世界接收产物，并受活动任务的返回模式和严格模式控制。严格模式也适用于普通输出端的产物返回。

## 重置

重置只清除该单元的活动状态和待下发材料。

> 待下发材料会永久销毁。已经进入 AE2 返回路径的资源不会因重置而重新生成。

另见[频率绑定](../getting-started/frequency.md)及本页下方直接列出的单元端口。
