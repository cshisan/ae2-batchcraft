---
navigation:
  parent: pattern-p2p/input.md
  title: Batch Distribution
  position: 10
---

# Batch Distribution

The Input's General Configuration offers two dispatch modes.

| Mode | Behavior |
| --- | --- |
| **Full Dispatch** | One endpoint must accept the entire processing push at once |
| **Batch Distribution** | The push may be delivered as integer multiples of a configured smallest share |

## Configure the Pattern

In processing-pattern mode in the AE2 Pattern Encoding Terminal, hover the primary output and press `Ctrl + Middle Mouse Button`. Enter the **Batch Count**, then press **Confirm** or Enter. Closing or returning without confirmation does not save the edited value.

The minimum and default are `1`. The maximum is limited by the encoded input and output quantities. A value above the maximum is corrected to the maximum; a value that cannot divide every declared quantity is corrected to `1`.

The check only proves that the encoded quantities are divisible. It cannot prove the smallest recipe accepted by the adjacent inventory. For example, if the real minimum is `2 A + 2 B -> 2 C` but the pattern is `4 A + 4 B -> 4 C`, setting the count to `4` describes `1 A + 1 B -> 1 C`; the destination may never start. Use a count that preserves the actual minimum ratio.

## Delivery

The configured count is the maximum number of shares, not the number of endpoints that must be used. The Input considers currently available endpoints and their current capacity. Each selected endpoint receives one aggregated delivery containing an integer number of shares; it does not receive a separate task object for every share.

Materials not yet requested remain in the adjacent AE2 Pattern Provider. When capacity becomes available, the Input requests and distributes another planned delivery. Materials already accepted by the Input are tracked until their assigned endpoint accepts them.

Only matching processing patterns may continue the same active series. A newly placed Input has no context for materials left from an earlier session and refuses them until it receives a valid processing-pattern request.

See [Pattern P2P Tunnel (Input)](input.md) and [Material Output Directions](../troubleshooting/material-directions.md).
