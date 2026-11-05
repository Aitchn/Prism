# Mekanism on Prism (validation, not a product)

This module tests whether Prism can express a Mekanism-style mod as a plain plugin. It does not copy Mekanism
and is never published. Textures are flat placeholders.

- `logic/` is Bukkit-free: `EnergyBuffer`, `ChemicalTank`, `Recipe`, `Machine`, `Networks` (cables and pipes),
  `Cuboid` (multiblock validator), `Catalog` (machines and the osmium chain). Covered by unit tests.
- The Bukkit/Prism adapter is `MekanismMod`, `MekanismBlock` (one behavior type driven by YAML) and `MekanismWorld`.
- Everything Prism could not express is listed in [docs/gaps.md](../../docs/gaps.md), section "Found in the
  Mekanism rebuild".
