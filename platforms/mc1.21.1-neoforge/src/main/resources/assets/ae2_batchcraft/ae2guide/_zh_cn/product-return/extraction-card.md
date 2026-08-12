---
navigation:
  parent: product-return/index.md
  title: 产物提取卡
  icon: product_extraction_card
  position: 30
item_ids:
- ae2_batchcraft:product_extraction_card
---

# 产物提取卡

<RecipeFor id="ae2_batchcraft:product_extraction_card" />

将卡插入 AE2 样板供应器升级槽，供应器工具栏会出现**产物提取设置**按钮。这是样板供应器自身的独立功能，不会开启或配置样板 P2P 输出端与单元提取端口。

| 设置 | 作用 |
| --- | --- |
| 提取间隔 | 两次提取尝试之间的 tick 数 |
| 提取数量 | 每次尝试最多执行的提取操作数 |
| 标记槽 | 参与筛选的资源类型 |
| 黑名单 | 提取标记资源以外的所有资源 |
| 白名单 | 只提取标记资源 |

同一标记筛选也会限制该样板供应器接受返回的资源。空白名单不会通过筛选路径提取或接收任何内容；空黑名单则不排除任何资源。

提取卡不会绕过 AE 存储容量。资源无法回到 ME 时，样板供应器会保留并沿原有生命周期重试。
