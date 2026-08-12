---
navigation:
  parent: product-return/index.md
  title: Product Extraction Card
  icon: product_extraction_card
  position: 30
item_ids:
- ae2_batchcraft:product_extraction_card
---

# Product Extraction Card

<RecipeFor id="ae2_batchcraft:product_extraction_card" />

Install the card in an AE2 Pattern Provider upgrade slot. A **Product Extraction Settings** button then appears in the provider toolbar. This is an independent Pattern Provider feature; it does not enable or configure Pattern P2P Outputs or Unit Extraction Ports.

| Setting | Effect |
| --- | --- |
| Interval | Ticks between extraction attempts |
| Amount | Maximum extraction operations per attempt |
| Marker slots | Resource types used by the filter |
| Blacklist | Extract everything except marked resources |
| Whitelist | Extract only marked resources |

The same marker filter also limits which returned resources that Pattern Provider accepts. An empty whitelist therefore extracts and accepts nothing through the filtered path; an empty blacklist excludes nothing.

The card does not bypass AE storage capacity. If the provider cannot return a resource to ME, it retains the resource and retries through the normal provider lifecycle.
