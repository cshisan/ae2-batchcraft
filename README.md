# AE2 BatchCraft

**Share processing patterns across a parallel production line.**

Minecraft **1.21.1** · **NeoForge** · **Applied Energistics 2** · Project version **2.0.1**

**English** | [简体中文](README_zh-CN.md)

## Overview

AE2 BatchCraft connects one AE2 Pattern Provider to multiple processing machines through a Pattern P2MP network.
Keep the processing patterns in the provider, then expand production by adding outputs and machines.
For processes that need separate input and output faces, world interactions, or redstone control, combine functional
ports under a Unit Manager.

The processing network supports two routes:

```text
Pattern Provider → Input Port → Output Port → Machine
Pattern Provider → Input Port → Unit Manager → Unit Ports → Machines / World
```

The Input Port uses one AE channel. Output Ports, Unit Managers, and Unit Ports do not consume additional channels.
They still need a powered AE network and loaded chunks. This channel saving applies to the routing components;
other devices, such as the ME IO Port, have their own requirements.

## Features

| Component | Purpose |
|---|---|
| **Pattern P2MP Input Port** | Receives processing jobs and selects available outputs or units using round robin, random, or priority allocation. Supports full dispatch and batch distribution. |
| **Pattern P2MP Output Port** | Sends ingredients to an adjacent machine and returns its products to the original Pattern Provider. Supports active product extraction and continuous resource supply. |
| **Pattern P2MP Unit Manager** | Coordinates several ports as one processing unit. Frequency selects the input group; a unit ID identifies the unit. Available in fluix and AE2 cable colors. |
| **Unit Ports** | Transfer, Drop, Place, Return, Extraction, Collect, Break, Redstone, and Provider ports cover material delivery, product retrieval, world interactions, and stock replenishment. |
| **PP2MP Pattern** | Encodes processing recipes with ingredient output methods, directions, batch counts, and a real-product marker for fake crafting. |
| **Pattern P2MP Resource Intake** | Receives or actively retrieves resources and supplies same-frequency outputs and Provider ports without a crafting job. Supports resource filters and target stock quantities. |
| **Fake Crafting Converter** | Uses paired item tickets to represent a job and releases the fake-product tickets when the marked real product has returned in sufficient quantity. |
| **ME Component Placer** | Places cables and parts over a point, line, or plane, up to 16 blocks per axis. Supports network materials, Memory Card settings, and crafting requests for missing materials. |
| **ME IO Port** | Fills or empties storage cells and moves completed cells to its output slots, selected adjacent inventories, or a preferred destination with a fallback. |

## Quick Start

### Connect ordinary machines

1. Put processing patterns in a Pattern Provider on the crafting network.
2. Build a powered AE network for the P2MP components; this can be a separate subnet.
   Place an Input Port facing the provider's output face and give the input a channel.
3. Place an Output Port facing each machine, on the same AE network as the input.
4. Sneak-right-click the input with an AE2 Memory Card to save its frequency, then right-click each output to load it.
5. Configure the machine face to accept ingredients and return products. Either let the machine push products into
   the output, or enable product extraction in the output's settings.
6. Request the recipe from AE2. The input dispatches work to available endpoints and returns products through the
   original provider.

Frequency `0000` is unconfigured. A shared frequency does not connect separate AE networks, load chunks, or override
a machine's sided inventory rules.

### Combine ports into a unit

1. Place a Unit Manager and the required ports on the input's AE network.
2. Load the input's frequency onto the manager with a Memory Card.
3. Sneak-right-click the manager with the card, then right-click each port to load its frequency and unit ID.
4. Use Transfer, Drop, or Place ports for ingredients. Add Return, Extraction, Collect, or Break ports as needed to
   retrieve products; add Redstone ports for processes that require a signal.
5. Use a PP2MP Pattern when ingredients need a particular output method. Managers can share an input frequency,
   but separate units should have separate unit IDs.

Transfer, Drop, and Place ports deliver ingredients; arrange a product-return path as well.
See the [Unit Manager guide](neoforge1211/src/main/resources/assets/ae2_batchcraft/ae2guide/unit/manager.md) for binding and port configuration.

## Processing Controls

### Output-port feeding modes

Configure **Output Ports - Feeding Mode** on the manager's **General** page, or disable synchronization on a port
to configure it independently. These four mutually exclusive modes apply to **Transfer, Drop, and Place** ports:

| Mode | Behavior per port and job |
|---|---|
| **Normal** | Delivers materials from multiple ingredient slots, including different resources. |
| **Slot Exclusive** | Serves one assigned ingredient slot. Empty slots before the last occupied slot also reserve ports; trailing empty slots do not. |
| **Single Item** | Delivers at most one item or 1000 mB of one fluid for the whole job, not once per tick. |
| **Same Type** | Delivers one distinct resource, even if it appears in several slots. Cobblestone and oak logs, water and lava, and items with different data components are separate resources. |

Mode changes apply to the next job. A job needs enough eligible ports for its ingredients.
In Slot Exclusive mode, `[A][empty][B]` with Normal ingredient output methods requires three Transfer ports;
the middle port is reserved without receiving materials.

### PP2MP patterns and batches

Right-click a PP2MP Pattern to open its editor, or use it on a network-connected AE block to access network storage
while editing. PP2MP Patterns support processing recipes.

- Hover over an ingredient and press `Ctrl + Middle Mouse Button` to configure its output method and direction.
  Empty ingredient slots can also be configured for slot reservation.
- Hover over the first product and press `Ctrl + Middle Mouse Button` to set the batch count, then select
  **Batch Distribution** on the input.
- For example, `4 A + 4 B → 4 C` with two batches sends `2 A + 2 B → 2 C` per batch.
  All ingredient and product quantities, including the real-product marker when present, must divide evenly.
  Each batch must still match a valid machine recipe.
- **Full Dispatch** sends the whole recipe to one output or unit. Batch Distribution uses the configured batches;
  it does not automatically reduce a batch to fit a smaller machine.

See [PP2MP Patterns](neoforge1211/src/main/resources/assets/ae2_batchcraft/ae2guide/pattern.md)
and [Batch Distribution](neoforge1211/src/main/resources/assets/ae2_batchcraft/ae2guide/batch.md).

### Blocking and product return

**Blocking is enabled by default:** an endpoint waits for its current job to finish before accepting another.
With blocking disabled, it may accept another job after the previous ingredients have been delivered, provided the
new ingredients can be accepted. It does not have to wait for the previous products.

Product-return matching and blocking are separate settings. Product extraction supports intervals as short as
**1 tick**, but actual throughput depends on the source, machine interface, and return capacity.
See [Common Configuration](neoforge1211/src/main/resources/assets/ae2_batchcraft/ae2guide/configuration.md)
for matching rules, synchronization, and task reset behavior.

## Additional Automation

- **Continuous supply:** connect a Resource Intake to a source and bind it to the same frequency as the target
  outputs or unit managers. Use Provider ports for units. Filters can maintain target stock quantities without
  starting a craft. Ordinary resource supply requires a configured frequency. Supported Applied Flux FE resources
  can also charge the local AE network without a frequency; this is not a direct connection for ordinary FE cables.
- **Fake crafting:** pair input and fake-product tickets in a same-frequency Fake Crafting Converter, then mark
  the ticket ingredient in a PP2MP Pattern. A dedicated real-product marker appears above the Encode button.
  This marker is not an extra recipe output. Only one converter may serve a frequency on the same network;
  valid ticket pairs and sufficient real-product returns are required.
- **Batch placement:** the ME Component Placer can use stored materials or a bound wireless AE network.
  Right-click a frequency or unit ID field while holding a Memory Card with the cursor to load that value;
  left-click to clear it. Creative mode waives tool energy, but still requires materials and, when using network
  materials, a valid wireless connection.
- **Storage-cell handling:** the ME IO Port supports internal output, external output, and either destination
  as the preferred choice with the other as fallback. It requires power and a channel.

## Guides and Integrations

With **GuideMe** installed, hover over a mod item and hold the guide key, **G** by default, to open its page.
The bundled guide is available in English and Simplified Chinese.

- [Getting Started](neoforge1211/src/main/resources/assets/ae2_batchcraft/ae2guide/getting_started.md)
- [Fake Crafting Converter](neoforge1211/src/main/resources/assets/ae2_batchcraft/ae2guide/fake_crafting_converter.md)
- [Resource Intake](neoforge1211/src/main/resources/assets/ae2_batchcraft/ae2guide/resource_intake.md)
- [ME Component Placer](neoforge1211/src/main/resources/assets/ae2_batchcraft/ae2guide/component_placer.md)
- [ME IO Port](neoforge1211/src/main/resources/assets/ae2_batchcraft/ae2guide/io_port.md)

Optional **JEI** integration provides recipe transfer to the PP2MP editor and mod aliases for `@ae2bc` and
`@batchcraft` searches; alias search must be enabled in JEI. AE2's tooltip integration also supplies frequency,
unit ID, and task information to **Jade** where applicable.

## Requirements and Installation

This source tree targets **Minecraft 1.21.1 on NeoForge**. It does not contain Forge or other Minecraft-version modules.

| Component | Version configured in this project | Requirement |
|---|---|---|
| Minecraft | `1.21.1` | Required |
| NeoForge | `21.1.238` | Metadata accepts `21.1+` |
| Applied Energistics 2 | `19.2.17` | Required; metadata accepts `19.0.0 ≤ version < 20.0.0` |
| Java | `21` | Required for the game and Gradle |
| GuideMe | `21.1.16` | Optional client guide; metadata accepts `21.1.16+` |
| JEI | `19.27.0.335` | Optional recipe-viewer integration |

The versions above come from the current build configuration, not a claim that every accepted dependency version
has been tested. Install the mod, AE2, and their required dependencies on both the client and server.
Use the optional client integrations as needed.

## Building

Use **JDK 21** and the included Gradle Wrapper from the repository root.
The `shared` module targets Java 8; Gradle is configured to provision the required toolchains.
The first build needs network access to download dependencies.

```powershell
# Windows PowerShell: build both modules and run their tests
.\gradlew.bat buildAll

# Run unit tests only
.\gradlew.bat :shared:test :neoforge1211:test

# Launch the development client
.\gradlew.bat :neoforge1211:runClient
```

On Linux or macOS, use `./gradlew` instead of `.\gradlew.bat`.

The mod JAR is written to `neoforge1211/build/libs/ae2-batchcraft-1.21.1-2.0.1-neoforge.jar`.
It includes the shared code; the `shared` module's JAR is not a separate mod to install.

| Directory | Contents |
|---|---|
| `shared/` | Platform-independent Java 8 code |
| `neoforge1211/` | Minecraft 1.21.1 NeoForge implementation, resources, guides, and tests |

## License

The project declares **GNU GPL 3.0** in its build configuration and mod metadata.
