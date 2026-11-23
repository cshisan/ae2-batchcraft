---
navigation:
  parent: index.md
  title: 样板P2MP资源接收端
  icon: pp2mp_resource_intake
  position: 40
item_ids:
- ae2_batchcraft:pp2mp_resource_intake
---

# 样板P2MP资源接收端

<ItemImage id="ae2_batchcraft:pp2mp_resource_intake" scale="3" />

从相邻来源获取资源，再通过输出端或供应端口持续补充机器的部件，不需要合成任务。<br />&#160;<br />

## 使用

**获取资源：** 被动模式接收来源主动推入的资源；主动模式从来源提取，可设置间隔和每轮数量，最小间隔为 `1 tick`。<br />&#160;<br />

**选择目标：** 接收端与目标所在的输出端、管理器接入同一 AE 网络，并绑定相同频率。资源会送往目标机器，不进入 ME 存储。<br />&#160;<br />

**分配与过滤：** 可选择轮询、优先或均分，并通过[供应过滤](provider_filter.md)限制资源和目标库存数量。<br />&#160;<br />

## 特殊使用说明

**未设置频率：** 不能向输出端或供应端口供应普通资源。<br />&#160;<br />

**网络供电：** 支持的 Applied Flux FE 资源可为所在 AE 网络充电。此用途不要求频率或网络已通电，但不支持直接接入普通 FE 能源线。<br />&#160;<br />

**目标无法接收：** 会等待可用目标；实际供应量受来源可取量、机器接口和目标容量限制。
