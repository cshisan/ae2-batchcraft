---
navigation:
  parent: index.md
  title: Unit Port Manager
  icon: pp2mp_unit_manager_fluix
  position: 50
item_ids:
- ae2_batchcraft:pp2mp_unit_manager_fluix
- ae2_batchcraft:pp2mp_unit_manager_white
- ae2_batchcraft:pp2mp_unit_manager_light_gray
- ae2_batchcraft:pp2mp_unit_manager_gray
- ae2_batchcraft:pp2mp_unit_manager_black
- ae2_batchcraft:pp2mp_unit_manager_lime
- ae2_batchcraft:pp2mp_unit_manager_yellow
- ae2_batchcraft:pp2mp_unit_manager_orange
- ae2_batchcraft:pp2mp_unit_manager_brown
- ae2_batchcraft:pp2mp_unit_manager_red
- ae2_batchcraft:pp2mp_unit_manager_pink
- ae2_batchcraft:pp2mp_unit_manager_magenta
- ae2_batchcraft:pp2mp_unit_manager_purple
- ae2_batchcraft:pp2mp_unit_manager_blue
- ae2_batchcraft:pp2mp_unit_manager_light_blue
- ae2_batchcraft:pp2mp_unit_manager_cyan
- ae2_batchcraft:pp2mp_unit_manager_green
---

# Unit Port Manager

<ItemImage id="ae2_batchcraft:pp2mp_unit_manager_fluix" scale="3" />

Combines functional ports into a processing unit for separate material delivery, product collection, world processing and redstone control.<br />&#160;<br />

## Usage

**Bind the unit:** Connect the manager, ports and input to the same AE network. Load the input's frequency onto the manager, then shift-right-click the manager with a memory card to save and right-click its ports to load.<br />&#160;<br />

**Frequency and unit ID:** Frequency selects the input; unit ID distinguishes managers. Managers may share a frequency, but separate units should use separate IDs.<br />&#160;<br />

**Shared settings:** Configure ports centrally on the manager, or enable synchronization to follow the input. Disable synchronization on a port to use its own settings.<br />&#160;<br />

## Feeding Modes

**Where to configure:** Use Output Ports - Feeding Mode on the General page. It applies to Transfer, Drop and Placement ports. Select one of the four modes.<br />&#160;<br />

**Normal:** One port may feed several ingredient slots and distinct resources.<br />&#160;<br />

**Slot Exclusive:** Each port feeds only its assigned ingredient slot. Materials from that slot may be delivered in several steps.<br />&#160;<br />

**Single Item:** Each port feeds at most one item or 1000 mB of one fluid per job. Splitting delivery into several steps does not increase that job's limit.<br />&#160;<br />

**Same Type:** Each port feeds one distinct resource per job, which may come from several ingredient slots. Cobblestone and oak logs, water and lava, and items with different additional data are separate resources.<br />&#160;<br />

## Notes

**Empty slots:** In Slot Exclusive mode, gaps before the last occupied ingredient slot also reserve ports, including leading and consecutive gaps. Trailing gaps do not. Other feeding modes do not reserve ports for gaps.<br />&#160;<br />

**Reserved port type:** The gap's [output method](../pattern.md) selects its port: Normal uses Transfer, Drop uses Drop, and Place uses Placement. Empty slots send no materials and perform no world operations.<br />&#160;<br />

**Gap example:** `[A][empty][B]`, all using the Normal output method and Slot Exclusive Transfer ports, requires at least three ports. The middle port receives no materials.<br />&#160;<br />

**Port count:** A job is not accepted if its materials cannot be assigned to the available ports. Feeding two items entirely through Single Item ports needs at least two ports. Same Type also allows one resource to be split across several ports.<br />&#160;<br />

**Mode changes:** Changes apply to the next job. With [blocking disabled](../configuration.md), ports can be reused once materials are sent and another job can be accepted, without waiting for previous products.<br />&#160;<br />

**Port filters:** Markers consume no samples. Empty filters allow all resources; an inverter card excludes marked resources. [Supply ports](provider.md) also support target stock quantities.<br />&#160;<br />

**Collect products:** Transfer, Drop and Placement ports only deliver materials. Add Return, Extraction, Collection or other product retrieval ports.
