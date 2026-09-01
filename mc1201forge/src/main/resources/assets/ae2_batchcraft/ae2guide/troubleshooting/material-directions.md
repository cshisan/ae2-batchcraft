---
navigation:
  parent: troubleshooting/index.md
  title: Material Output Directions
  position: 10
---

# Material Output Directions

In processing-pattern mode in an AE2 Pattern Encoding Terminal, hover an input ingredient and press `Ctrl + Middle Mouse Button` to choose its output form and direction. The values are stored in the encoded pattern and appear in its tooltip.

| Output form | Unit destination |
| --- | --- |
| Normal | Transfer Port |
| Drop | Drop Port |
| Place | Place Port |

**Automatic direction** uses the face through which the relevant endpoint connects to the machine. An absolute direction such as Up or North uses only that world direction and does not fall back to the connected face.

If the encoded pattern tooltip lacks the configuration, confirm that the terminal was in processing-pattern mode, configure the ingredient again, and then press **Encode Pattern**. Inspect the final encoded pattern before inserting it into an AE2 Pattern Provider.

Standard Pattern Providers ignore BatchCraft's output-form extension. It is interpreted by Pattern P2P Outputs and Unit material ports.
