---
navigation:
  parent: index.md
  title: Common Settings
  position: 180
---

# Common Settings

Shared settings for outputs and unit managers, covering product returns, further jobs, synchronization and resets.<br />&#160;<br />

## Return Modes

**Strict: Full Match.** The resource and its additional data must match, including item names and enchantments.<br />&#160;<br />

**Strict: Ignore NBT.** Items ignore custom NBT only. Names, enchantments and other properties must still match.<br />&#160;<br />

**Strict: Registry ID Only.** Matches the registry ID within the same resource type, without comparing additional properties.<br />&#160;<br />

**Unblocked.** Accepts undeclared resources during a job. Only resources that fully match a required product count toward completion.<br />&#160;<br />

**Completion:** Ordinary jobs require every declared product in the required amount. Fake crafting uses the [real output marker](fake_crafting_converter.md). Extra quantities may return while a job is active. These settings do not change AE2's own crafting matching rules.<br />&#160;<br />

## Blocking Mode

**Enabled (default):** Waits for the current job to finish before accepting another. Applies to full dispatch and batch distribution.<br />&#160;<br />

**Disabled:** Another job can start after all previous materials have been sent and the target can accept new ones, without waiting for earlier products. Unsent materials or materials awaiting return still require waiting.<br />&#160;<br />

**Enabling again:** Existing jobs continue, and all must finish before another is accepted. Matching products complete earlier jobs first.<br />&#160;<br />

**Different settings:** Unblocked return mode controls which resources may return. Job blocking mode controls when another job may start.<br />&#160;<br />

**Redstone recipes:** Pulses from concurrent jobs may overlap. Keep blocking enabled when each job needs a separate trigger.<br />&#160;<br />

## Synchronization and Extraction

**Follow settings:** Outputs and managers can follow the input's job settings. See [Supply Filtering](provider_filter.md) for filter synchronization. Synchronized settings are read-only; disabling synchronization retains their current values.<br />&#160;<br />

**Where settings apply:** The product extraction switch controls ordinary outputs. Manager extraction settings apply to Extraction and Collection ports. Resource intakes have separate active acquisition settings.<br />&#160;<br />

**Interval and amount:** The minimum extraction interval is `1 tick`. Amount is a per-cycle limit, subject to stock, machine restrictions and return capacity. See [Collection ports](unit/collect.md) for dropped-item quantities.<br />&#160;<br />

**Resuming extraction:** Extraction may take a short time to resume after being idle or blocked.<br />&#160;<br />

## Resetting Jobs

**Reset scope:** Resetting an input cancels its waiting batches and resets endpoints on the same frequency. An output or manager resets only its own jobs.<br />&#160;<br />

**Materials and crafting:** Unsent materials wait to be returned. Materials already inserted and blocks already placed or broken are not restored. Cancel AE2 CPU crafting jobs separately.
