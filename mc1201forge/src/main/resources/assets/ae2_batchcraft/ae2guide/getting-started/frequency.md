---
navigation:
  parent: getting-started/index.md
  title: Frequency Binding
  position: 20
---

# Frequency Binding

Pattern P2P frequencies group one input with normal outputs and Unit Managers. Unit identity is a second, separate binding used only between a Manager and its ports.

## Pattern P2P Frequency

1. Hold an AE2 Memory Card and `Shift + Right-click` the input.
2. If the input was unconfigured, it creates a new frequency and stores it on the card.
3. Right-click each normal output or Unit Manager with that card.

Frequency `0000` means unconfigured. An input cannot distribute tasks and an output cannot accept tasks or receive P2P energy until it has a valid frequency.

> A frequency cannot be changed while that input group has an active task. Finish or deliberately reset the task first.

## Unit Identity

1. Bind the Unit Manager to the input frequency.
2. `Shift + Right-click` the Manager with a Memory Card to save its identity.
3. Right-click every Unit Port that belongs to this Manager.

Ports must be on the same AE grid as their Manager. Two Managers never share a port, even if they use the same Pattern P2P frequency.

## Component Placer

The Component Placer deliberately ignores its displayed frequency when placing a **Pattern P2P Input**, preventing duplicate inputs with one frequency. Other supported P2P endpoints may still receive the displayed frequency.
