package dev.newhungersystem.api;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

import java.util.List;

/**
 * 服务端配置与上限 API (Lib 友好, 不写死 20)。
 * <p>
 * 实际最大饥饿值          = baseMaxHunger          + bonusMaxHunger          （可配置, 默认 20）
 * 实际最大饱和度          = baseMaxSaturation      + bonusMaxSaturation      （可配置, 默认 20）
 * 实际最大消化值          = baseMaxDigestion        + bonusMaxDigestion        （可配置, 默认 30）
 * 实际最大消化饱和度      = baseMaxDigestionSaturation + bonusMaxDigestionSaturation（可配置, 默认 30）
 * 四个上限均提供 addMax* API 以在运行时提升 (可传负值撤销)。
 * <p>
 * 开局数值 (starting): 新玩家生成时使用的饥饿值/饱和度/消化值/消化饱和度。
 * <p>
 * 其余数值全部接线到配置分组，无硬编码：{@code penalties}（饥饿/消化惩罚阈值与强度、疾跑门槛）、
 * {@code regen}（回血曲线/计价/节拍/慢回倍率/饥饿掉血）、{@code vomit}（阈值/时长/反胃等级）、
 * {@code food}（AlwaysEat、原版效果重定向、消耗值上限、饥饿效果消耗值）。
 * <p>
 * 按用户要求<b>不提供"关闭整个模组"的总开关</b> —— 不想用这个模组的玩家直接删掉即可。
 */
public final class HungerConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue BASE_DIGESTION_RATE;

    // ---------------- 上限 (limits) ----------------

    /** baseMaxHunger: 可配置, 默认 20. 实际最大饥饿值 = 此值 + bonusMaxHunger */
    public static final ForgeConfigSpec.DoubleValue MAX_HUNGER;

    /** baseMaxSaturation: 可配置, 默认 20. 实际最大饱和度 = 此值 + bonusMaxSaturation */
    public static final ForgeConfigSpec.DoubleValue MAX_SATURATION;

    /** baseMaxDigestion: 可配置, 默认 30 (原版最大饥饿值×1.5). 实际最大消化值 = 此值 + bonusMaxDigestion */
    public static final ForgeConfigSpec.DoubleValue MAX_DIGESTION;

    /** baseMaxDigestionSaturation: 可配置, 默认 30. 实际最大消化饱和度 = 此值 + bonusMaxDigestionSaturation */
    public static final ForgeConfigSpec.DoubleValue MAX_DIGESTION_SATURATION;

    // ---------------- 开局数值 (starting) ----------------

    /** 开局饥饿值 (原版默认 20) */
    public static final ForgeConfigSpec.DoubleValue START_HUNGER;

    /** 开局饱和度 (原版默认 5) */
    public static final ForgeConfigSpec.DoubleValue START_SATURATION;

    /** 开局消化值 (默认 20) */
    public static final ForgeConfigSpec.DoubleValue START_DIGESTION;

    /** 开局消化饱和度 (默认 5) */
    public static final ForgeConfigSpec.DoubleValue START_DIGESTION_SATURATION;

    /** 死亡复活是否保留死亡前的饥饿值/饱和度 (默认 true). 关闭则复活使用开局数值 */
    public static final ForgeConfigSpec.BooleanValue KEEP_HUNGER_ON_RESPAWN;

    // ---------------- 惩罚阈值 / 属性因子 (penalties) ----------------

    public static final ForgeConfigSpec.DoubleValue HUNGER_PENALTY_THRESHOLD_PERCENT;
    public static final ForgeConfigSpec.DoubleValue HUNGER_PENALTY_PER_PERCENT;
    /** 消化过载惩罚起点 (% of maxDigestion); 终点固定为 vomiting 阈值 vomitThresholdPercent */
    public static final ForgeConfigSpec.DoubleValue DIGESTION_PENALTY_START_PERCENT;
    public static final ForgeConfigSpec.DoubleValue SPRINT_HUNGER_THRESHOLD_PERCENT;

    // ---------------- 回血 (regen) ----------------

    public static final ForgeConfigSpec.DoubleValue REGEN_AT_ZERO_HUNGER_PERCENT;
    public static final ForgeConfigSpec.DoubleValue REGEN_AT_FULL_HUNGER_PERCENT;
    public static final ForgeConfigSpec.DoubleValue REGEN_EXHAUSTION_PER_HEALTH;
    public static final ForgeConfigSpec.DoubleValue FAST_REGEN_INTERVAL_TICKS;
    public static final ForgeConfigSpec.DoubleValue SLOW_REGEN_SPEED_DIVISOR;
    public static final ForgeConfigSpec.DoubleValue SLOW_REGEN_HUNGER_RATIO;
    public static final ForgeConfigSpec.DoubleValue STARVE_DAMAGE_INTERVAL_TICKS;
    public static final ForgeConfigSpec.DoubleValue STARVE_DAMAGE_AMOUNT;

    // ---------------- 呕吐 (vomit) ----------------

    public static final ForgeConfigSpec.DoubleValue VOMIT_THRESHOLD_PERCENT;
    public static final ForgeConfigSpec.DoubleValue VOMIT_DURATION_TICKS;
    public static final ForgeConfigSpec.DoubleValue VOMIT_NAUSEA_AMPLIFIER;

    // ---------------- 食物 / 原版效果 (food) ----------------

    public static final ForgeConfigSpec.BooleanValue ALWAYS_EDIBLE;
    public static final ForgeConfigSpec.BooleanValue REDIRECT_MOB_EFFECTS;
    public static final ForgeConfigSpec.DoubleValue MAX_EXHAUSTION;
    public static final ForgeConfigSpec.DoubleValue HUNGER_EFFECT_EXHAUSTION_PER_TICK;
    /** 全局倍率: 食物 nutrition -> 消化值 的缩放系数 (1.0 = 原始 1:1) */
    public static final ForgeConfigSpec.DoubleValue DIGESTION_PER_NUTRITION;
    /** per-item 绝对消化值覆盖 ("modid:item=value"), 优先于全局倍率与代码登记层 */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DIGESTION_OVERRIDES;

    // ---------------- 溢出缓存 (overflow) ----------------

    public static final ForgeConfigSpec.BooleanValue ENABLE_OVERFLOW_CACHE;
    public static final ForgeConfigSpec.DoubleValue OVERFLOW_CACHE_CAPACITY;

    // ---------------- 睡觉结算 (sleep) ----------------

    public static final ForgeConfigSpec.DoubleValue SLEEP_HUNGER_DELTA;
    public static final ForgeConfigSpec.DoubleValue SLEEP_SATURATION_DELTA;
    public static final ForgeConfigSpec.DoubleValue SLEEP_DIGESTION_DELTA;
    public static final ForgeConfigSpec.DoubleValue SLEEP_DIGESTION_SATURATION_DELTA;

    // ---------------- 运行时加成 (API) ----------------

    /**
     * 启动阶段（尚无服务器）由模组注册的加成：只存在于本次进程，不写入存档 ——
     * 因为这类调用每次启动都会重新执行，持久化反而会随世界重复叠加。
     */
    private static double baseBonusMaxHunger = 0.0D;
    private static double baseBonusMaxSaturation = 0.0D;
    private static double baseBonusMaxDigestion = 0.0D;
    private static double baseBonusMaxDigestionSaturation = 0.0D;

    /** 当前存档的持久化加成（{@link HungerBonusData}）；null = 未绑定（无服务器） */
    private static HungerBonusData persisted;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("starting");
        START_HUNGER = builder
                .comment("Starting food level for a new player (vanilla default 20).")
                .defineInRange("startHunger", 20.0D, 0.0D, 1000.0D);
        START_SATURATION = builder
                .comment("Starting saturation level for a new player (vanilla default 5).")
                .defineInRange("startSaturation", 5.0D, 0.0D, 1000.0D);
        START_DIGESTION = builder
                .comment("Starting digestion value for a new player (default 20).")
                .defineInRange("startDigestion", 20.0D, 0.0D, 1000.0D);
        START_DIGESTION_SATURATION = builder
                .comment("Starting digestion saturation for a new player (default 5).")
                .defineInRange("startDigestionSaturation", 5.0D, 0.0D, 1000.0D);
        builder.pop();

        builder.push("respawn");
        KEEP_HUNGER_ON_RESPAWN = builder
                .comment("If true, keep the player's food level & saturation on death-respawn (overrides vanilla reset to 20/5).",
                        "Also controls digestion value / digestion saturation: when false they are reset to",
                        "startDigestion / startDigestionSaturation too, so old digestion cannot refill hunger.",
                        "Non-death player clones (dimension change / returning from the End) always keep them.",
                        "Default true.")
                .define("keepHungerOnRespawn", true);
        builder.pop();

        builder.push("digestion");
        BASE_DIGESTION_RATE = builder
                .comment("Points converted from digestion/digestion saturation to hunger/saturation every 0.5s",
                        "Default: 0.15")
                .defineInRange("baseDigestionRate", 0.15D, 0.0D, 20.0D);
        builder.pop();

        builder.push("limits");
        MAX_HUNGER = builder
                .comment("baseMaxHunger. Actual max hunger = this + bonus (added via API). Default 20.")
                .defineInRange("maxHunger", 20.0D, 1.0D, 1000.0D);
        MAX_SATURATION = builder
                .comment("baseMaxSaturation. Actual max saturation = this + bonus (added via API). Default 20.")
                .defineInRange("maxSaturation", 20.0D, 1.0D, 1000.0D);
        MAX_DIGESTION = builder
                .comment("baseMaxDigestion. Actual max digestion = this + bonus (added via API). Default 30.",
                        "Raise this to increase the digestion cap independently of hunger/saturation caps.")
                .defineInRange("maxDigestion", 30.0D, 0.0D, 1000.0D);
        MAX_DIGESTION_SATURATION = builder
                .comment("baseMaxDigestionSaturation. Actual max digestion saturation = this + bonus (added via API). Default 30.")
                .defineInRange("maxDigestionSaturation", 30.0D, 0.0D, 1000.0D);
        builder.pop();

        builder.push("penalties");
        HUNGER_PENALTY_THRESHOLD_PERCENT = builder
                .comment("Hunger percent (of maxHunger) below which attack/speed/mining penalties begin. Default 30.")
                .defineInRange("hungerPenaltyThresholdPercent", 30.0D, 0.0D, 100.0D);
        HUNGER_PENALTY_PER_PERCENT = builder
                .comment("Factor lost per 1 percent below the hunger threshold (0.02 = 2%). Default 0.02.")
                .defineInRange("hungerPenaltyPerPercent", 0.02D, 0.0D, 1.0D);
        DIGESTION_PENALTY_START_PERCENT = builder
                .comment("Digestion percent (of maxDigestion, NOT maxHunger) where overload penalties begin.",
                        "Default 66.67 = 2/3 of the digestion bar.",
                        "The curve is a straight line from efficiency 1.0 here down to 0.0 at",
                        "vomitThresholdPercent, so it stays correct when maxDigestion, maxHunger or the",
                        "vomit threshold are changed independently.")
                .defineInRange("digestionPenaltyStartPercent", 66.67D, 0.0D, 100.0D);
        SPRINT_HUNGER_THRESHOLD_PERCENT = builder
                .comment("Sprint requires foodLevel above maxHunger * this percent. Default 30 (vanilla = 6/20).")
                .defineInRange("sprintHungerThresholdPercent", 30.0D, 0.0D, 100.0D);
        builder.pop();

        builder.push("regen");
        REGEN_AT_ZERO_HUNGER_PERCENT = builder
                .comment("Heal percent per fast-regen tick at zero hunger. Default 50.")
                .defineInRange("regenAtZeroHungerPercent", 50.0D, 0.0D, 1000.0D);
        REGEN_AT_FULL_HUNGER_PERCENT = builder
                .comment("Heal percent per fast-regen tick at full hunger (vanilla fast regen = 1 heart/0.5s). Default 60.")
                .defineInRange("regenAtFullHungerPercent", 60.0D, 0.0D, 1000.0D);
        REGEN_EXHAUSTION_PER_HEALTH = builder
                .comment("Vanilla regen pricing: exhaustion added per 1 health healed. Default 6.0.")
                .defineInRange("regenExhaustionPerHealth", 6.0D, 0.0D, 100.0D);
        FAST_REGEN_INTERVAL_TICKS = builder
                .comment("Ticks between saturation-driven (fast) regen ticks. Default 10 (0.5s).")
                .defineInRange("fastRegenIntervalTicks", 10.0D, 1.0D, 1000.0D);
        SLOW_REGEN_SPEED_DIVISOR = builder
                .comment("Slow regen interval = fastRegenIntervalTicks * this value (same heal per tick). Default 4.")
                .defineInRange("slowRegenSpeedDivisor", 4.0D, 1.0D, 100.0D);
        SLOW_REGEN_HUNGER_RATIO = builder
                .comment("Slow regen requires foodLevel >= maxHunger * this ratio. Default 0.9 (vanilla 18/20).")
                .defineInRange("slowRegenHungerRatio", 0.9D, 0.0D, 1.0D);
        STARVE_DAMAGE_INTERVAL_TICKS = builder
                .comment("Ticks between starvation damage ticks. Default 80 (vanilla).")
                .defineInRange("starveDamageIntervalTicks", 80.0D, 1.0D, 1000.0D);
        STARVE_DAMAGE_AMOUNT = builder
                .comment("Damage dealt per starvation tick. Default 1.0 (vanilla).")
                .defineInRange("starveDamageAmount", 1.0D, 0.0D, 100.0D);
        builder.pop();

        builder.push("vomit");
        VOMIT_THRESHOLD_PERCENT = builder
                .comment("Vomit when digestion reaches this percent of maxDigestion. Default 100.")
                .defineInRange("vomitThresholdPercent", 100.0D, 1.0D, 100.0D);
        VOMIT_DURATION_TICKS = builder
                .comment("Nausea duration in ticks after vomiting. Default 600 (30s).")
                .defineInRange("vomitDurationTicks", 600.0D, 1.0D, 10000.0D);
        VOMIT_NAUSEA_AMPLIFIER = builder
                .comment("Nausea amplifier (0 = Nausea I, 1 = Nausea II). Default 0.")
                .defineInRange("vomitNauseaAmplifier", 0.0D, 0.0D, 10.0D);
        builder.pop();

        builder.push("food");
        ALWAYS_EDIBLE = builder
                .comment("If true, implement the AlwaysEat effect: food can always be eaten, even at full hunger/digestion.",
                        "If false, needsFood() falls back to foodLevel < maxHunger (lets other mods handle it). Default true.")
                .define("alwaysEdible", true);
        REDIRECT_MOB_EFFECTS = builder
                .comment("If true, vanilla Saturation/Hunger effects apply to digestion value/saturation instead of food data.",
                        "Default true.")
                .define("redirectMobEffects", true);
        MAX_EXHAUSTION = builder
                .comment("Exhaustion needed to consume 1 saturation point (vanilla 4.0). Also the HUD exhaustion bar maximum.")
                .defineInRange("maxExhaustion", 4.0D, 0.1D, 100.0D);
        HUNGER_EFFECT_EXHAUSTION_PER_TICK = builder
                .comment("Vanilla Hunger effect exhaustion per tick per amplifier level. Default 0.005.")
                .defineInRange("hungerEffectExhaustionPerTick", 0.005D, 0.0D, 10.0D);
        DIGESTION_PER_NUTRITION = builder
                .comment("Global multiplier: food nutrition -> digestion value (digestion saturation scales with it too).",
                        "1.0 = original 1:1 (default). 0.0 = eating gives no digestion at all.",
                        "Applied BEFORE FoodDigestionEvent, so listeners still receive the scaled value and may override it.")
                .defineInRange("digestionPerNutrition", 1.0D, 0.0D, 100.0D);
        DIGESTION_OVERRIDES = builder
                .comment("Per-item ABSOLUTE digestion overrides for pack makers, format \"modid:item=value\".",
                        "Config entry wins over the global digestionPerNutrition multiplier and over",
                        "DigestionHelper.registerFoodValue(Item, float) registered in code.",
                        "FoodDigestionEvent is still fired afterwards, so mods may still change it.",
                        "Example: [\"minecraft:cake=6.0\", \"minecraft:golden_carrot=9.0\"]. Default empty.")
                .defineListAllowEmpty(List.of("digestionOverrides"), List.of(),
                        o -> o instanceof String s && s.contains("="));
        builder.pop();

        builder.push("overflow");
        ENABLE_OVERFLOW_CACHE = builder
                .comment("If false (default), overflow is simply discarded (a DigestionOverflowEvent is still fired for other mods).",
                        "If true, overflow goes into a per-player cache and is fed back into digestion as space frees up;",
                        "vomiting is suppressed while enabled (the cache replaces it).",
                        "This is a per-feature option, NOT a master switch for the whole mod.")
                .define("enableOverflowCache", false);
        OVERFLOW_CACHE_CAPACITY = builder
                .comment("Maximum storable overflow per cache (digestion / digestion saturation separately).",
                        "0 disables storing. Default 100.")
                .defineInRange("overflowCacheCapacity", 100.0D, 0.0D, 10000.0D);
        builder.pop();

        builder.push("sleep");
        SLEEP_HUNGER_DELTA = builder
                .comment("Food level change after waking up. Positive = restore, negative = LOSE hunger.",
                        "Vanilla beds restore nothing. Most hunger mods restore hunger here, but this mod's",
                        "design suggests losing hunger overnight is more appropriate (sleep produces no food).",
                        "Default 0 = no change.")
                .defineInRange("sleepHungerDelta", 0.0D, -1000.0D, 1000.0D);
        SLEEP_SATURATION_DELTA = builder
                .comment("Saturation change after waking up (positive = restore, negative = lose). Default 0.")
                .defineInRange("sleepSaturationDelta", 0.0D, -1000.0D, 1000.0D);
        SLEEP_DIGESTION_DELTA = builder
                .comment("Digestion value change after waking up (positive = restore, negative = lose). Default 0.")
                .defineInRange("sleepDigestionDelta", 0.0D, -1000.0D, 1000.0D);
        SLEEP_DIGESTION_SATURATION_DELTA = builder
                .comment("Digestion saturation change after waking up (positive = restore, negative = lose). Default 0.")
                .defineInRange("sleepDigestionSaturationDelta", 0.0D, -1000.0D, 1000.0D);
        builder.pop();

        SPEC = builder.build();
    }

    private HungerConfig() {
    }

    // ---------------- 上限查询 ----------------

    /**
     * 服务端权威上限 = 配置 base + 服务端加成（<b>忽略</b>客户端同步缓存）。
     * 同步包上报的就是这四个值，因此不会出现"客户端缓存喂回服务端"的回环。
     */
    public static double serverMaxHunger() {
        return dbl(MAX_HUNGER, 20.0F) + serverBonusMaxHunger();
    }

    public static double serverMaxSaturation() {
        return dbl(MAX_SATURATION, 20.0F) + serverBonusMaxSaturation();
    }

    public static double serverMaxDigestion() {
        return dbl(MAX_DIGESTION, 30.0F) + serverBonusMaxDigestion();
    }

    public static double serverMaxDigestionSaturation() {
        return dbl(MAX_DIGESTION_SATURATION, 30.0F) + serverBonusMaxDigestionSaturation();
    }

    // 实际上限：客户端收到同步包后以服务端权威值为准（专用服的 SERVER 配置不会下发到客户端，
    // 否则 HUD 条数/上限/疾跑门槛会和服务端不一致）；未收到时退回本地计算。

    /** 实际最大饥饿值 */
    public static float maxHunger() {
        return (float) (ClientDigestionData.hasServerCaps
                ? ClientDigestionData.serverMaxHunger : serverMaxHunger());
    }

    /** 实际最大饱和度 */
    public static float maxSaturation() {
        return (float) (ClientDigestionData.hasServerCaps
                ? ClientDigestionData.serverMaxSaturation : serverMaxSaturation());
    }

    /** 实际最大消化值 */
    public static float maxDigestion() {
        return (float) (ClientDigestionData.hasServerCaps
                ? ClientDigestionData.serverMaxDigestion : serverMaxDigestion());
    }

    /** 实际最大消化饱和度 */
    public static float maxDigestionSaturation() {
        return (float) (ClientDigestionData.hasServerCaps
                ? ClientDigestionData.serverMaxDigestionSaturation : serverMaxDigestionSaturation());
    }

    // ---------------- 加成读取: 服务端权威 / 客户端同步 ----------------

    /** 服务端权威加成 = 运行期 base + 本存档持久值 (同步包上报的就是这个值) */
    public static double serverBonusMaxHunger() {
        return baseBonusMaxHunger + (persisted == null ? 0.0D : persisted.getBonusMaxHunger());
    }

    public static double serverBonusMaxSaturation() {
        return baseBonusMaxSaturation + (persisted == null ? 0.0D : persisted.getBonusMaxSaturation());
    }

    public static double serverBonusMaxDigestion() {
        return baseBonusMaxDigestion + (persisted == null ? 0.0D : persisted.getBonusMaxDigestion());
    }

    public static double serverBonusMaxDigestionSaturation() {
        return baseBonusMaxDigestionSaturation
                + (persisted == null ? 0.0D : persisted.getBonusMaxDigestionSaturation());
    }

    /** 供 {@link HungerBonusStorage} 在服务器启停时绑定/解绑存档数据 */
    static void bindPersisted(HungerBonusData data) {
        persisted = data;
    }

    static void clearPersisted() {
        persisted = null;
    }

    static HungerBonusData persistedOrNull() {
        return persisted;
    }

    // ---------------- 配置未加载时的安全读取 ----------------
    // ForgeConfigSpec.ConfigValue#get() 在配置未加载时会抛 IllegalStateException。
    // 本模组的 API 是公开的，其它模组可能在 RegisterEvent / 静态初始化（早于配置加载）
    // 里调用 HungerConfig / DigestionHelper，因此所有热读取口都回退到内置默认值。

    private static boolean loaded() {
        return SPEC != null && SPEC.isLoaded();
    }

    private static float dbl(ForgeConfigSpec.ConfigValue<? extends Number> value, float fallback) {
        return loaded() ? value.get().floatValue() : fallback;
    }

    private static int integer(ForgeConfigSpec.ConfigValue<? extends Number> value, int fallback) {
        return loaded() ? value.get().intValue() : fallback;
    }

    private static boolean flag(ForgeConfigSpec.ConfigValue<Boolean> value, boolean fallback) {
        return loaded() ? value.get() : fallback;
    }

    // ---------------- 开局数值查询 ----------------

    public static float startHunger() {
        return dbl(START_HUNGER, 20.0F);
    }

    public static float startSaturation() {
        return dbl(START_SATURATION, 5.0F);
    }

    public static float startDigestion() {
        return dbl(START_DIGESTION, 20.0F);
    }

    public static float startDigestionSaturation() {
        return dbl(START_DIGESTION_SATURATION, 5.0F);
    }

    /** 死亡复活是否保留死亡前的饥饿值/饱和度 */
    public static boolean keepHungerOnRespawn() {
        return flag(KEEP_HUNGER_ON_RESPAWN, true);
    }

    // ---------------- 惩罚阈值 / 属性因子 ----------------

    public static float hungerPenaltyThresholdPercent() {
        return dbl(HUNGER_PENALTY_THRESHOLD_PERCENT, 30.0F);
    }

    public static float hungerPenaltyPerPercent() {
        return dbl(HUNGER_PENALTY_PER_PERCENT, 0.02F);
    }

    /** 消化过载惩罚起点 (% of maxDigestion) */
    public static float digestionPenaltyStartPercent() {
        return dbl(DIGESTION_PENALTY_START_PERCENT, 66.67F);
    }

    /** 疾跑门槛的绝对饥饿值 = maxHunger * 配置百分比 */
    public static float sprintHungerThreshold() {
        return maxHunger() * dbl(SPRINT_HUNGER_THRESHOLD_PERCENT, 30.0F) / 100.0F;
    }

    // ---------------- 回血 ----------------

    public static float regenAtZeroHungerPercent() {
        return dbl(REGEN_AT_ZERO_HUNGER_PERCENT, 50.0F);
    }

    public static float regenAtFullHungerPercent() {
        return dbl(REGEN_AT_FULL_HUNGER_PERCENT, 60.0F);
    }

    public static float regenExhaustionPerHealth() {
        return dbl(REGEN_EXHAUSTION_PER_HEALTH, 6.0F);
    }

    public static int fastRegenIntervalTicks() {
        return integer(FAST_REGEN_INTERVAL_TICKS, 10);
    }

    /** 慢回节拍 = 快回节拍 × 倍率, 保证"速度为快回 1/N、单次回血量相同"的关系不被配错 */
    public static int slowRegenIntervalTicks() {
        return Math.max(1, fastRegenIntervalTicks() * integer(SLOW_REGEN_SPEED_DIVISOR, 4));
    }

    public static float slowRegenHungerRatio() {
        return dbl(SLOW_REGEN_HUNGER_RATIO, 0.9F);
    }

    public static int starveDamageIntervalTicks() {
        return integer(STARVE_DAMAGE_INTERVAL_TICKS, 80);
    }

    public static float starveDamageAmount() {
        return dbl(STARVE_DAMAGE_AMOUNT, 1.0F);
    }

    // ---------------- 呕吐 ----------------

    public static float vomitThresholdPercent() {
        return dbl(VOMIT_THRESHOLD_PERCENT, 100.0F);
    }

    public static int vomitDurationTicks() {
        return integer(VOMIT_DURATION_TICKS, 600);
    }

    public static int vomitNauseaAmplifier() {
        return integer(VOMIT_NAUSEA_AMPLIFIER, 0);
    }

    // ---------------- 食物 / 原版效果 ----------------

    public static boolean alwaysEdible() {
        return flag(ALWAYS_EDIBLE, true);
    }

    public static boolean redirectMobEffects() {
        return flag(REDIRECT_MOB_EFFECTS, true);
    }

    public static float maxExhaustion() {
        return dbl(MAX_EXHAUSTION, 4.0F);
    }

    public static float hungerEffectExhaustionPerTick() {
        return dbl(HUNGER_EFFECT_EXHAUSTION_PER_TICK, 0.005F);
    }

    /** 食物 nutrition -> 消化值的全局倍率 (1.0 = 1:1) */
    public static float digestionPerNutrition() {
        return dbl(DIGESTION_PER_NUTRITION, 1.0F);
    }

    /** per-item 绝对消化值覆盖原始列表 ("modid:item=value") */
    public static List<? extends String> digestionOverrides() {
        return loaded() ? DIGESTION_OVERRIDES.get() : List.of();
    }

    public static boolean enableOverflowCache() {
        return flag(ENABLE_OVERFLOW_CACHE, false);
    }

    public static float overflowCacheCapacity() {
        return dbl(OVERFLOW_CACHE_CAPACITY, 100.0F);
    }

    public static float sleepHungerDelta() {
        return dbl(SLEEP_HUNGER_DELTA, 0.0F);
    }

    public static float sleepSaturationDelta() {
        return dbl(SLEEP_SATURATION_DELTA, 0.0F);
    }

    public static float sleepDigestionDelta() {
        return dbl(SLEEP_DIGESTION_DELTA, 0.0F);
    }

    public static float sleepDigestionSaturationDelta() {
        return dbl(SLEEP_DIGESTION_SATURATION_DELTA, 0.0F);
    }

    // ---------------- 上限 API (可在运行时提升, 传负值撤销) ----------------
    // 服务器运行中 -> 写入当前存档 (SavedData, 重启/重进世界仍在);
    // 无服务器 (启动阶段 / 专用客户端) -> 只作用于本次进程 (baseBonus)。

    public static void addMaxHunger(double amount) {
        if (persisted != null) {
            persisted.addBonusMaxHunger(amount);
        } else {
            baseBonusMaxHunger += amount;
        }
    }

    public static void addMaxSaturation(double amount) {
        if (persisted != null) {
            persisted.addBonusMaxSaturation(amount);
        } else {
            baseBonusMaxSaturation += amount;
        }
    }

    public static void addMaxDigestion(double amount) {
        if (persisted != null) {
            persisted.addBonusMaxDigestion(amount);
        } else {
            baseBonusMaxDigestion += amount;
        }
    }

    public static void addMaxDigestionSaturation(double amount) {
        if (persisted != null) {
            persisted.addBonusMaxDigestionSaturation(amount);
        } else {
            baseBonusMaxDigestionSaturation += amount;
        }
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SPEC);
    }
}
