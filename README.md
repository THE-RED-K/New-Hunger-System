# New Hunger System

> 让"吃"真正经过胃 —— Minecraft 1.20.1 的饥饿系统重写 + 可被其它模组接管的 Lib。

**Minecraft 1.20.1** · **Forge 47.x** · **MIT** · 作者 Illus

---

## 定位

本模组有双重身份：

- **饥饿系统重写（系统模组）**：原版是"吃 → 立刻加饥饿值"。本模组把它拆成
  **食物 → 消化值 → 饥饿值**，并在中间加入消化饱和度、过载惩罚、呕吐、睡眠代谢等一整套"代谢层"。
  吃起来不再是瞬间回血条，而是"胃里慢慢消化"。
- **库（Lib）**：代谢过程中的每一步都有对外 Forge 事件与 API。整合包与其它模组可以改数值、加规则、
  替换默认行为，**不需要修改本模组源码**。这是它与"写死数值的饥饿系统"最大的区别。

设计取向：

- **不设"总开关"** —— 不想用直接删掉模组即可；需要关掉某个子系统时把对应数值调到中性值（例如把惩罚强度设为 0）。
- **数值全部可配** —— 上限、速率、阈值、惩罚强度、回血曲线、呕吐、睡眠结算都在配置里。
- **不覆盖原版语义** —— 原版饥饿条、饱和度、血量、饥饿效果、药水效果仍然照常工作，只是被"重定向"到消化层。

---

## 核心机制

```
             ┌──────────────────────────────┐
   进食 ───► │ 消化值 digestion (0~30)       │
             │ 消化饱和度 digestionSaturation │──(每 0.5s, 1:1)──► 原版饱和度
             └───────────────┬──────────────┘
                             │ (每 0.5s, 1:1, 经进位缓存)
                             ▼
                       原版饥饿值 (0~20)
```

- **吃食物**：食物的 `nutrition` 转化为消化值（默认 1:1，可配倍率或按物品覆盖）；
  消化饱和度按原版公式 `nutrition × saturationModifier × 2` 增加。原版 `eat()` 直接加的饥饿值会被还原。
- **消化**：每 0.5 秒把 `baseDigestionRate`（默认 **0.15**）点消化值换算成饥饿值，1:1；饥饿值是整数，
  余数留在进位缓存里不丢。消化饱和度同理换算成原版饱和度（受"饱和度 ≤ 饥饿值"约束）。
- **满值暂停**：饥饿值到上限就停止转化（不消耗、不浪费），等有空间自动继续。

默认数值：

| 项 | 默认 | 说明 |
|---|---|---|
| 最大饥饿值 / 饱和度 | 20 / 20 | 可用 API 提升，会被持久化到存档 |
| 最大消化值 / 消化饱和度 | 30 / 30 | 同上 |
| 开局数值 | 饥饿 20 / 饱和 5 / 消化 20 / 消化饱和 5 | 可配 |
| 消化转化速率 | 0.15 / 0.5s | 30 点消化条约 100 秒转完 |

---

## 功能

### 代谢与惩罚

- **进食接管**：`FoodData#eat` 被重写，食物营养不再直接进饥饿值；`needsFood` 可配置为"任何时候都能吃"。
- **回血曲线（双轨并行）**：两轨**共用** `slowRegenHungerRatio`（默认 60%）门槛，低于门槛时即使饱和度充足也**完全停止回血**；
  **快轨**由饱和度驱动（默认每 10 tick）且**只消耗饱和度**，**慢轨**由饥饿值达标驱动（每 40 tick）且**只消耗饥饿值**，
  两本燃料账本互相独立（不共用原版 `exhaustionLevel`），饱和度未耗尽时两轨**同时回血、各烧各的燃料**，
  饥饿值跌破 60% 后回血会**自发暂停**，并减少消化值堆积到呕吐阈值的概率。
  两轨共用同一套回血量公式（仅由饥饿值缩放，**消化值不影响回血**），按**实际回血量** × 6.0 计入各自账本，每满 4 点扣 1 点对应燃料。保留原版 `naturalRegeneration` 游戏规则。
- **属性惩罚**：饥饿值低于阈值 → 攻击/攻速/移速/挖掘下降；消化值过载 → 攻击力与攻速/移速/游泳/挖掘再降。
  消化过载的惩罚幅度有**不可配置的硬上限**：攻击力最多 −30%，各速度最多 −60%（默认曲线到呕吐点时正好达到上限）。
  惩罚通过三个自定义属性实现，见下节。
- **过载加速**：消化值进入惩罚区（> `digestionPenaltyStartPercent`，默认 66.67%）后，`消化值 → 饥饿值` 的转化速率
  随消化值线性放大 —— 起点 ×1，到呕吐点时 ×1.5（默认 `baseDigestionRate` 0.15/0.5s → 0.225/0.5s），
  吃撑越重消化越快，缩短惩罚停留时间。「加速区」与「惩罚区」共用同一配置，不会错位。
- **呕吐**：消化值达到阈值（默认 100%）→ 清空消化值/消化饱和度 + 反胃；开启溢出缓存时由缓存取代呕吐。
- **溢出**：写入超过上限的部分会广播 `DigestionOverflowEvent`；可选"溢出缓存"会把溢出排队，之后有空位再回填（默认关闭）。
- **睡眠结算**：起床时对饥饿/饱和/消化/消化饱和做可配置的增减（默认全 0，即保持原版行为）。
- **其他**：原版"饱和""饥饿"效果被重定向到消化层；创造/旁观模式豁免惩罚；死亡复活可选保留饥饿值。
- **和平模式不再自动恢复**（合并自 PeacefulDepletion，MIT）：`[peaceful] disablePeacefulAutoRegen` 默认开启时，
  和平模式不再自动回血、不再自动补饥饿，饥饿值也照常消耗（等价于按 HARD 判定）；关闭可恢复原版行为。
  原版那条 `setFoodLevel(+1)` 在本模组中始终钳制到 `maxHunger`，即使关闭配置也不会越过上限。
- **低饥饿死亡保底**：死亡时饥饿值低于 `maxHunger × starvationRespawnHungerPercent`（默认 30%）→ 复活后把饥饿值补到该值；
  同一个配置值同时作为判定阈值与给予值，设为 0% 则整个机制关闭；只抬高不压低。
- **死亡损失**：`deathHungerLossPercent` / `deathSaturationLossPercent` / `deathDigestionLossPercent` /
  `deathDigestionSaturationLossPercent`（各默认 0 = 关闭），按**当前值**的百分比扣减，复活后数值 = 当前值 × (1 − pct%)；
  先扣损失再做低饥饿保底，因此保底仍然是最低值。
- **死亡消化值开关**：`keepDigestionOnDeath`（默认 true）与 `keepHungerOnRespawn` 完全独立；
  设为 false 时死亡后消化值/消化饱和度重置为 `startDigestion` / `startDigestionSaturation`，
  并跳过消化部分的死亡损失百分比（不会"清空后再扣一遍"）。非死亡 Clone（维度切换）永远保留。

### HUD（仿 AppleSkin，可逐项关闭）

- 原版饥饿条正上方一行显示**消化条**（胃图标），消化饱和度以黄色叠在同一行
- 手持食物时显示**回复预显**（红=消化值增量、黄=消化饱和度增量，带闪烁）
- 饥饿行底部的**消耗值底栏**
- 右侧**数值**（`20+10` 格式，饥饿棕 / 饱和金 / 消化肉色）
- F3 调试行附加消化数据；原版饥饿条抖动动画可开关

### 开发者接口

| 类型 | 内容 |
|---|---|
| 自定义属性 | `new_hunger_system:hunger_efficiency`、`new_hunger_system:digestion_efficiency`（速度类）、`new_hunger_system:digestion_attack_efficiency`（攻击力）（默认 1.0，可被任何模组加修饰器，直接决定攻击/攻速/移速/挖掘/游泳的惩罚强度；消化侧两项受硬上限钳制） |
| 数据门面 | `DigestionHelper`：`get / optional / set / add / addDigestion / addDigestionSaturation / reset / resetToStart / registerFoodValue` |
| 上限 API | `HungerConfig.addMaxHunger / addMaxSaturation / addMaxDigestion / addMaxDigestionSaturation`（运行期调用会持久化进当前存档） |
| 数据驱动 | 配置 `[food] digestionOverrides`（`"modid:item=value"`）+ 代码登记 `registerFoodValue`，优先级：配置 > 代码 > 内置公式 |

**对外 Forge 事件**

| 事件 | 时机 | 能力 |
|---|---|---|
| `FoodDigestionEvent` | 吃完食物、写入消化值之前 | 改消化值/消化饱和度的增量，或取消本次消化收益 |
| `DigestionConversionEvent` | 每 0.5s，消化→饥饿 / 消化饱和→饱和各一次 | 改本次转化量（会被 clamp 到拥有量与可用空间），或跳过本次 |
| `DigestionVomitEvent` | 达到呕吐阈值、清空之前 | 取消呕吐，或改反胃时长/等级 |
| `DigestionOverflowEvent` | 写入超过上限时 | 读取溢出量并可标记已处理（内置缓存就是这么实现的消费者） |

### 药水效果

| 效果 | 作用 | 药水时长 |
|---|---|---|
| `digestive_aid`（助消化） | 每级 **+20%** 消化速率 | 3:00 |
| `instant_digestion`（瞬间消化） | 转化速率 **×10**，与助消化/消化不良**叠乘** | 1:00 |
| `indigestion`（消化不良） | 每级 **−20%** 消化速率 | 1:30 |

助消化与消化不良**同级互相抵消**（净倍率 = `1 + 0.2 × (助消化等级 − 消化不良等级)`）。
三种药水目前**没有酿造配方**，由整合包/附属自行接入。

### 命令

```
/digestion get [target]                      查看消化值/消化饱和度/进位缓存/溢出缓存/上限加成
/digestion reset [target]                    消化值归零（OP）
/digestion start [target]                    重置为配置的开局数值（OP）
/digestion set <消化值> <消化饱和度> [target]  直接设定（OP）
/digestion bonus <hunger|saturation|digestion|digestionSaturation> <amount>   调整上限加成并持久化（OP）
```

---

## 配置

两个配置文件，进游戏自动生成：

- `config/new_hunger_system-server.toml`（服务端，**数值平衡都在这里**）
- `config/new_hunger_system-client.toml`（客户端，只影响 HUD 显示）

| 分组 | 主要项 |
|---|---|
| `limits` | 四上限的基础值（20/20/30/30） |
| `starting` | 新玩家开局数值（20/5/20/5） |
| `digestion` | `baseDigestionRate` = 0.15 |
| `penalties` | 饥饿惩罚阈值/强度、消化过载起点（默认 66.67%）与呕吐点、疾跑饥饿门槛 |
| `regen` | 快回/慢回节拍、回血计价、饿死间隔与伤害 |
| `vomit` | 呕吐阈值（100%）、反胃时长/等级 |
| `food` | 随时进食、效果重定向、`digestionPerNutrition`、`digestionOverrides` |
| `overflow` | 溢出缓存开关（默认 false）与容量 |
| `peaceful` | `disablePeacefulAutoRegen`（默认 true）：禁用和平模式自动回血/自动补饥饿，和平模式饥饿照常消耗 |
| `respawn` | 死亡是否保留饥饿值（`keepHungerOnRespawn`）、死亡是否保留消化值（`keepDigestionOnDeath`，独立开关）、低饥饿死亡复活保底（`starvationRespawnHungerPercent`，默认 30%，0 = 关闭）、四项死亡损失百分比（默认 0 = 关闭） |
| `sleep` | 起床后四项数值的增减（默认 0） |
| 客户端 | 消化条/饱和叠层/手持预显/消耗值底栏/数字/F3/原版抖动 + AppleSkin 兼容抑制 |

> 所有公开的读取方法在配置未加载时会回退到内置默认值，因此其它模组在注册阶段（早于配置加载）调用本模组 API 也不会崩。

---

## 兼容性

- **AppleSkin**（可选，客户端）：装了会自动识别，可按配置抑制它的饥饿/饱和/消耗值 overlay 以避免与本模组 HUD 重叠。
  在 `mods.toml` 中声明为 `mandatory=false / side=CLIENT`。
- **原版**：饥饿条、饱和度、血量、饥饿效果、饱和效果、药水效果均照常工作，只是被重定向。
- 与其它修改 `FoodData` 的模组可能冲突（本模组使用较低 Mixin 优先级接管 `eat/tick/needsFood`）。
- **Difficulty Overhaul**：无 Mixin 冲突（它注入 `Player.hurt` / `Mob` / `DifficultyInstance` / 创建世界界面，
  本模组的和平模式开关注入 `Player.aiStep`）。但两者对"和平模式"的语义相反——它的和平模式描述为"饥饿不下降、
  血量恢复"，而本模组 `disablePeacefulAutoRegen=true`（默认）会禁用这些特性。需要它的和平模式行为时把该项设为 `false`。
- **CakeBlock**：蛋糕切片已重定向到消化层（`+2` 消化值 / `+0.4` 消化饱和度），不再直接加饥饿值。

---

## 第三方与许可

- 本模组本体：**MIT**（`LICENSE`，Copyright (c) 2026 THE_RED_K）
- Chest Cavity（作者 David Troyer）贴图：`textures/item/stomach.png`、由其重绘的
  `textures/item/golden_stomach.png`、以及由它生成的 `textures/gui/stomach_icons.png`；
  源码 `fabric.mod.json` 与 Modrinth 页面声明 **CC0-1.0**，CurseForge 页面声明 **Apache-2.0**，按最严格的 Apache-2.0 履行
- PeacefulDepletion（合并的和平模式逻辑）：**MIT**，MapleSugar365
- farsighted-mobs（仅参考思路；已拉取仓库逐文件比对，无代码/资源复制）：**LGPL-3.0**，Sindarin27

完整归属、修改说明与许可证全文见 `THIRD_PARTY_NOTICES.md` 与 `licenses/`。

---

## 安装

1. 安装 Minecraft 1.20.1 + Forge 47.x
2. 把 jar 放进 `mods/`
3. 单机与服务器都需要安装（本模组包含服务端逻辑，且带自定义网络包，两端版本需一致）

---

## 从源码构建

```bash
./gradlew build
```

产物在 `build/libs/`。开发环境：JDK 17、Forge 47.4.20、Parchment 映射。

---

## 已知限制

- 消化条按原版饥饿条的样式绘制，**固定 10 格（=20 点）**：把 `maxDigestion` 提到 20 以上时，
  条只会显示前 20 点，超出部分以右侧数字为准；低于 20 时会按比例缩短。原版饥饿条在 `maxHunger > 20` 时同理。
- 饿死伤害**不做原版难度兜底**（原版 Easy/Normal 会给你留血），强度完全由 `starveDamage*` 配置决定。
- 食物 → 消化值没有 datapack JSON，用配置列表 + `registerFoodValue` + 事件代替。
- 药水无酿造配方（如上）。

---

## 许可证

MIT
