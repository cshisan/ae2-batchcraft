# AE2 BatchCraft

**Use one set of processing patterns to drive a group of machines in parallel.**

Minecraft 1.21.1 NeoForge | Minecraft 1.20.1 / 1.16.5 / 1.12.2 Forge | Applied Energistics 2

**English** | [简体中文](README_zh-CN.md)

## What Does It Do?

AE2 BatchCraft adds a **one-to-many processing-pattern P2P network** to Applied Energistics 2.

A Pattern P2P input receives processing jobs from an AE2 Pattern Provider on Minecraft `1.20.1` and `1.21.1`, or an ME Interface on `1.12.2` and `1.16.5`, and
distributes them among multiple machine outputs or Pattern P2P Units. You can expand a production line by adding
endpoints and machines without copying the same patterns or spending one AE channel per machine.

## Included Content

| Item / Feature                  | Purpose                                                                                                                                                                                 |
|---------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Pattern P2P Tunnel (Input)**  | Receives processing jobs, distributes them among available endpoints, and returns products to the Pattern Provider or ME Interface. This is the only endpoint that uses `1` AE channel. |
| **Pattern P2P Tunnel (Output)** | Inserts ingredients into one adjacent machine and accepts returned products. It uses no AE channel.                                                                                     |
| **Pattern P2P Tunnel (Energy)** | Powers the AE subnet and distributes remaining FE to eligible machines and Unit Energy Ports. It uses no AE channel or P2P frequency.                                                   |
| **Pattern P2P Unit (Manager)**  | Joins the output group and coordinates several bound functional ports as one processing endpoint. Available in fluix and all AE2 cable colors.                                          |
| **Pattern P2P Unit Ports**      | Transfer, Drop, Place, Return, Extraction, Collect, Break, Redstone, and Energy ports provide material routing and world interaction without additional channels.                       |
| **Product Extraction Card**     | Lets an AE2 Pattern Provider actively extract filtered products from an adjacent machine. Available on Minecraft `1.20.1` and `1.21.1`.                                                 |
| **AE Component Placer**         | Batch-places AE cables and cable-attached parts over a point, line, or plane up to `16 x 16`, using player or AE network materials.                                                     |
| **Pattern Configuration**       | Configures ingredient input sides and Normal, Drop, or Place output forms. Minecraft `1.21.1` also provides batch distribution and per-pattern batch configuration.                     |

Transfer Ports support **Normal**, **Single Item**, and **Single Type** delivery modes. Separately, the Manager-level **Single-port single-slot** policy controls whether one output port may serve multiple encoded pattern slots: it can enable the restriction for all ports, disable it for all ports, or follow each port's own setting. Output ports can be assigned AE2 priorities and optional material markers; an AE2 Inverter Card reverses the marker filter. The adjacent machine remains the authority for its real inventory or tank capacity.

Collect Ports handle dropped items on every supported version. On Minecraft `1.20.1` and `1.21.1`, they can also collect
source fluids; on `1.16.5` and `1.12.2`, they collect items only.

## Why Use It?

- **Parallel processing:** processing jobs are distributed among available machines instead of always using the first
  one.
- **Fewer channels:** only the Pattern P2P input uses `1` channel; outputs, Unit Managers, and Unit Ports use none.
- **Centralized patterns:** keep processing patterns in one AE2 Pattern Provider, or one ME Interface on `1.12.2` and `1.16.5`, instead of copying them to every
  machine.
- **Flexible automation:** Unit Ports can route materials, interact with the world, return products, emit redstone, and
  supply FE.
- **Automatic product return:** outputs and return-type ports can send task products back through the input.
- **Centralized power:** an Energy Tunnel can power the subnet and distribute FE among eligible endpoints.
- **Fast expansion:** the AE Component Placer can build a row or plane of cable-and-part endpoints in one operation.

## Quick Start

### Build a Parallel Machine Group

1. Put processing patterns in an AE2 **Pattern Provider** on `1.20.1` and `1.21.1`, or an **ME Interface** on `1.12.2` and `1.16.5`, on the main
   network.
2. Build a powered AE subnet and install a **Pattern P2P Tunnel (Input)** with its front face against that block's
   output face.
3. Install one **Pattern P2P Tunnel (Output)** in front of each processing machine on the same subnet.
4. `Shift + Right-click` the input with an AE2 Memory Card to generate and save a frequency.
5. Right-click every output with the same Memory Card to assign that frequency.
6. Request a processing craft. The input selects the next available endpoint and sends the job to it.

Output-type Unit Ports (Transfer, Drop, and Place) can be opened empty-handed to configure their AE2 priority, material markers, and optional Inverter Card. Return-type ports (Return, Collect, Break, and Extraction) follow the active task's return and strict-mode rules.

The input and all endpoints must be on the same AE subnet, powered, and loaded. Frequency `0000` means unconfigured.
Offline, unloaded, busy, or blocked endpoints are skipped.

By default, **Full Dispatch** sends one complete request to one endpoint. For example, `100 A + 800 B -> 100 C` must fit
into one machine before it is accepted.

Minecraft `1.21.1` also provides **Batch Distribution**. Select it in the input's General Configuration, then hover over
the processing pattern's primary output and press `Ctrl + Middle Mouse Button` to open Batch Configuration. A batch
count of `100` for `100 A + 800 B -> 100 C` defines units of `1 A + 8 B -> 1 C`; the input distributes capacity-sized
multiples among available machines while unsent materials remain in the Pattern Provider. The game only checks
divisibility, so the configured ratio must match the machine's real recipe.

### Build a Pattern P2P Unit

Use a Unit when a process needs several input methods, return paths, or world interactions instead of one machine face.

1. Install a **Pattern P2P Unit (Manager)** as a cable center on the subnet.
2. Load the Pattern P2P input frequency from a Memory Card onto the Manager.
3. `Shift + Right-click` the Manager with a Memory Card to save its identity.
4. Right-click each functional port with that card to bind it to the Manager.
5. In processing-pattern mode, hover over an input ingredient and press `Ctrl + Middle Mouse Button` to select its input
   side and Normal, Drop, or Place output form.

The Manager participates in the same endpoint group as normal outputs. Its ports must be on the same AE subnet and
remain bound to that Manager's identity.

### Optional Automation

- Configure return mode and product extraction in the Pattern P2P input. Synchronized Unit Managers apply those settings
  to their bound ports during active tasks, including changes made while the task is running.
- On Minecraft `1.20.1` and `1.21.1`, install a **Product Extraction Card** in a Pattern Provider to configure its own
  adjacent-machine extraction and product filter.
- Place a **Pattern P2P Tunnel (Energy)** toward an FE source to power the subnet and eligible machines, then choose
  passive/active input and even/round-robin distribution in its GUI.
- Use the **AE Component Placer** to select a point, line, or plane, choose a cable and part, optionally load a Memory
  Card frequency, and place the configured endpoints in one action.

## In-game Guide

Minecraft `1.20.1` and `1.21.1` include an English and Chinese in-game guide
when [GuideME](https://modrinth.com/mod/guideme) is installed. Hover over an item from this mod and press AE2's guide
key, `G`, to open its page. Both guides cover recipes, setup, Unit Ports, product return, energy settings, and task
reset behavior; the `1.21.1` guide also covers batch distribution.

## Screenshot

<img width="2227" height="1199" alt="AE2 BatchCraft in-game setup" src="https://github.com/user-attachments/assets/c5ad93a2-4e99-4b5d-aae8-54771553ebdb" />

## Requirements

| Minecraft | Loader               | Applied Energistics 2 | Java      | In-game Guide |
|-----------|----------------------|-----------------------|-----------|---------------|
| `1.21.1`  | NeoForge `21.1.238`  | `19.2.17`             | `21`      | Yes           |
| `1.20.1`  | Forge `47.4.10`      | `15.4.10`             | `17`      | Yes           |
| `1.16.5`  | Forge `36.2.42`      | `8.4.7`               | `8`       | No            |
| `1.12.2`  | Forge `14.23.5.2847` | `rv6-stable-7`        | `8`       | No            |

Install the release built for your Minecraft version and its required dependencies on both the client and server.

## License

AE2 BatchCraft is licensed under the [GNU General Public License v3.0](LICENSE) (`GPL-3.0-only`).
