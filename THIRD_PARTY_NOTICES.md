# 第三方组件与许可证声明 / Third-Party Notices

New Hunger System 本体以 **MIT License** 发布（见 `LICENSE`，Copyright (c) 2026 THE_RED_K）。
本项目包含或参考了下列第三方作品，相应义务在此履行。许可证全文位于 `licenses/` 目录。

---

## 1. Chest Cavity — 贴图资源

| 文件 | 说明 |
|---|---|
| `src/main/resources/assets/new_hunger_system/textures/item/stomach.png` | 取自 Chest Cavity（16×16 原图） |
| `src/main/resources/assets/new_hunger_system/textures/item/golden_stomach.png` | 基于 `stomach.png` 重绘的金色变体（18×18），衍生/修改作品 |
| `src/main/resources/assets/new_hunger_system/textures/gui/stomach_icons.png` | 由 `scripts/gen_stomach_icons.py` 从 `stomach.png` 程序化生成的 96×128 HUD 图集，衍生/修改作品 |

**来源**：Chest Cavity，作者 **David Troyer**（Fabric 项目，源码 `chestcavity-master`）

**许可证**（三处声明均已在本地源码/页面核实）：

- 源码 `src/main/resources/fabric.mod.json`：`"license": "CC0-1.0"`
- CurseForge 页面：**Apache License 2.0**
- Modrinth 页面：**CC0-1.0**

仓库本身**未附 LICENSE / NOTICE 文件**（`chestcavity-master` 内仅有 `readme.md`，且 readme 无许可章节）。

**文件比对记录**（2026-11-28，与原仓库 `assets/chestcavity/textures/item/stomach.png` 对比）：

| 本项目文件 | 原图 | 差异 |
|---|---|---|
| `textures/item/stomach.png` | 16×16，2527 B，SHA-256 `3F8279A3AD5F3024…` | 本项目同尺寸 482 B，哈希不同 → 已重新编码/压缩，属修改 |
| `textures/item/golden_stomach.png` | — | 18×18，由原图重绘并加金色描边，属衍生 |
| `textures/gui/stomach_icons.png` | — | 96×128，由 `scripts/gen_stomach_icons.py` 从 `stomach.png` 程序化生成，属衍生 |

**义务履行**：

- 本项目按三处声明中**最严格**的 **Apache-2.0** 履行：保留 `Copyright (c) David Troyer` 归属声明，
  并随附完整许可证文本 `licenses/Apache-2.0.txt`（满足 Apache-2.0 §4(a) 的“向接收者提供许可证副本”）。
- 上表三个文件均已依 Apache-2.0 §4(b) 在此**显著标注修改事实**。
- 若以 **CC0-1.0**（源码 `fabric.mod.json` 与 Modrinth 页面声明）作为授权依据，则署名与许可证副本义务本可免除；
  为兼容 CurseForge 的 Apache-2.0 声明，本项目仍保留完整声明。CC0-1.0 全文见 `licenses/CC0-1.0.txt`。

> 说明：原项目未提供 NOTICE 文件；若其后续补充 NOTICE，分发时应一并保留。

---

## 2. PeacefulDepletion 1.0.2 — 合并的行为逻辑

**来源**：PeacefulDepletion，作者 MapleSugar365
（<https://github.com/MapleSugar365/PeacefulDepletion>，构建于 2025-07-10，版本 1.0.2）

**许可证**：**MIT**（见其 `META-INF/mods.toml` 的 `license="MIT"`）

**合并内容**：

- `dev/newhungersystem/mixin/PlayerPeacefulMixin.java`（`Player#aiStep` 的和平模式分支处理）
- `dev/newhungersystem/mixin/FoodDataMixin.java` 中“和平模式照常扣除饥饿值”的分支
- `HungerConfig` 的 `[peaceful] disablePeacefulAutoRegen` 配置项

原实现的手法是把 `Level#getDifficulty()` 的返回值伪装成 `Difficulty.HARD`；本项目改为受配置控制，
并额外把原版和平模式的 `setFoodLevel(+1)` 钳制到最大饥饿值。

**义务履行**：保留原作者的版权声明与 MIT 许可声明，全文见 `licenses/PeacefulDepletion-MIT.txt`。

---

## 许可证文件索引

| 文件 | 用途 |
|---|---|
| `LICENSE` | 本项目自身（MIT） |
| `licenses/PeacefulDepletion-MIT.txt` | PeacefulDepletion 的 MIT 许可与版权声明 |
| `licenses/Apache-2.0.txt` | Chest Cavity 贴图（Apache-2.0 声明） |
| `licenses/CC0-1.0.txt` | Chest Cavity 贴图（CC0 声明） |
| `licenses/LGPL-3.0.txt` | farsighted-mobs（LGPL-3.0，预备） |
