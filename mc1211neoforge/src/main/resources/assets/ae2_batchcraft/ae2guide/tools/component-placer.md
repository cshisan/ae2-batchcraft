---
navigation:
  parent: tools/index.md
  title: AE Component Placer
  icon: component_placer
  position: 10
item_ids:
- ae2_batchcraft:component_placer
---

# AE Component Placer

<RecipeFor id="ae2_batchcraft:component_placer" />

The powered placer repeats one cable and one cable-attached part over a selected point, line, or plane.

## Select and Configure

1. `Shift + Right-click` a block to store the first point, then right-click the second point. A selection may be a point, line, or plane; solid volumes are rejected.
2. Open the GUI and mark a cable and/or a cable-attached part. Marker slots record the item type and do not consume the sample.
3. Choose the part's facing direction. X, Y, and Z offsets move the placement relative to the stored selection.
4. Optionally hold an AE2 Memory Card in the cursor, main hand, or off hand and right-click the frequency control to load a P2P frequency. Left-click resets it to `0000`.
5. Press **Place**. Occupied targets are skipped rather than replaced.

| Limit or source | Behavior |
| --- | --- |
| Selection size | Each axis at most `16`; a plane at most `16x16` |
| Materials | Connected AE network first, then nine local material slots |
| Missing network material | With a Crafting Card, opens AE2's craft-amount screen when possible |
| Power | Uses the placer's internal AE power and supports Energy Cards |

When placing a **Pattern P2P Input**, the placer deliberately ignores the loaded frequency so it cannot create a duplicate input group. Other supported P2P parts can receive the displayed frequency. The direction, offsets, marker choices, selection, and displayed frequency are stored on the tool until changed or cleared.
