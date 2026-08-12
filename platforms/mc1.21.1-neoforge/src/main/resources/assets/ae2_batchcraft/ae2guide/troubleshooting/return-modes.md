---
navigation:
  parent: troubleshooting/index.md
  title: Choosing a Return Mode
  position: 20
---

# Choosing a Return Mode

Use **Unblocked** when a machine has an isolated product path and unrelated resources cannot enter it. Use **Strict** when products share a return path and the endpoint must reject resource types not declared by the active pattern.

If a return is rejected, check in this order:

1. Confirm the endpoint is working on the pattern you expect.
2. Compare the returned resource type with the pattern's products and byproducts.
3. Check whether the Output or Manager is synchronizing the Input configuration.
4. Temporarily use Unblocked only to distinguish a rule mismatch from a full ME network.

Strict mode cannot verify machine recipe completion and does not limit returned quantities. A resource of the expected type may be accepted early; pending input materials are still dispatched before the task can finish.
