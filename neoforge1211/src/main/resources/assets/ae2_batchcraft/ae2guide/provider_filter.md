---
navigation:
  parent: resource_intake.md
  title: Supply Filtering
  position: 10
---

# Supply Filtering

Controls which resources continuous supply may send and how much stock the destination should maintain.<br />&#160;<br />

## Usage

**Whitelist:** Mark allowed resources. Middle-click a marker to set its target amount; `0` means unlimited.<br />&#160;<br />

**Blacklist:** Install an inverter card to exclude marked resources instead. Target amounts no longer apply.<br />&#160;<br />

**Where to configure:** Open Supply Filtering on a resource intake, output or manager. [Supply ports](unit/provider.md) also offer local settings.<br />&#160;<br />

**Synchronization:** Outputs and managers can follow the intake, and Supply ports can follow their manager. Disable synchronization for independent settings.<br />&#160;<br />

## Notes

**Empty filter:** All resources are allowed when nothing is marked.<br />&#160;<br />

**Target amount:** This is the stock to maintain at the destination, not the amount sent each time. Exact quantities cannot be guaranteed when the machine's inventory cannot be read reliably.<br />&#160;<br />

**Virtual inverter card:** A synchronized inverter card only indicates blacklist mode and cannot be taken.
