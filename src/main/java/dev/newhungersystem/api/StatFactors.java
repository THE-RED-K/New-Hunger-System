package dev.newhungersystem.api;

import net.minecraft.util.Mth;

/**
 * 消化/饥饿阈值对应的属性与回血缩放因子 (1.0 = 无影响, 越低惩罚越重)。
 * <p>
 * 基准说明: 饥饿类百分比以 <b>maxHunger</b> 为基准，消化类百分比以 <b>maxDigestion</b> 为基准。
 * 两者都由配置 + API 加成决定（不写死 20/30）且可以各自独立变化，所以不能混用基准。
 * 阈值与强度全部来自 {@link HungerConfig} 的 {@code penalties} / {@code regen} / {@code vomit} 配置组。
 */
public final class StatFactors {

    /**
     * 攻击力最大惩罚比例 —— <b>硬上限, 不可配置</b>。
     * <p>
     * 消化过载对攻击力的削弱最多 30% (效率最低 0.70)。写成常量而不是配置项,
     * 是为了保证任何配置改动 (maxDigestion / digestionPenaltyStartPercent /
     * vomitThresholdPercent) 都无法让惩罚越过这条线。
     */
    public static final double MAX_ATTACK_PENALTY = 0.30;

    /**
     * 速度类(攻击速度/移动速度/游泳速度/挖掘速度)最大惩罚比例 —— <b>硬上限, 不可配置</b>。
     * <p>
     * 最多 60% (效率最低 0.40), 同样不随配置变化。
     */
    public static final double MAX_SPEED_PENALTY = 0.60;

    private StatFactors() {
    }

    /** 当前最大饥饿值 = 配置 baseMaxHunger + API 加成 (不写死 20) */
    public static float maxHunger() {
        return HungerConfig.maxHunger();
    }

    /** 当前最大消化值 = 配置 baseMaxDigestion + API 加成 (不写死 30) */
    public static float maxDigestion() {
        return HungerConfig.maxDigestion();
    }

    /** 攻击/攻速/移速/挖掘: 饥饿值低于阈值时按比例惩罚, 否则 1.0 */
    public static double hungerAttrFactor(float foodLevel) {
        double max = maxHunger();
        if (max <= 0.0) {
            // 上限被配成 0 (或负) 时没有"饥饿百分比"可言; 必须提前返回,
            // 否则 0/0 得到 NaN, 会被写进 AttributeModifier 让攻击力/移速变成 NaN
            return 1.0;
        }
        double pct = (foodLevel / max) * 100.0;
        double threshold = HungerConfig.hungerPenaltyThresholdPercent();
        if (pct >= threshold) {
            return 1.0;
        }
        return Math.max(0.0, 1.0 - (threshold - pct) * HungerConfig.hungerPenaltyPerPercent());
    }

    /**
     * 消化过载的原始强度: 0.0 = 未进入惩罚区, 1.0 = 已到呕吐点(惩罚满载)。
     * <p>
     * 从 {@code digestionPenaltyStartPercent}（默认 66.67 = 2/3 消化条）开始线性上升，
     * 到 {@code vomitThresholdPercent}（呕吐点）达到 1.0；两个锚点都以 <b>maxDigestion</b> 为基准，
     * 因此 maxDigestion / maxHunger / 呕吐阈值任意独立变化都不会让曲线错位。
     * <p>
     * 这里只给出 0~1 的"过载程度", 具体惩罚幅度由
     * {@link #MAX_ATTACK_PENALTY} / {@link #MAX_SPEED_PENALTY} 决定 —— 两者都是硬上限,
     * 所以本方法的输出无论怎么配都不会让效率跌破上限线。
     */
    private static double digOverloadRatio(float digestion) {
        double max = maxDigestion();
        if (max <= 0.0) {
            return 0.0;
        }
        double pct = (digestion / max) * 100.0;
        double start = HungerConfig.digestionPenaltyStartPercent();
        double end = HungerConfig.vomitThresholdPercent();
        if (end <= start || pct <= start) {
            return 0.0;
        }
        return Mth.clamp((pct - start) / (end - start), 0.0, 1.0);
    }

    /**
     * 消化过载对<b>攻击力</b>的效率: 1.0 = 无惩罚, 最低 {@code 1 - MAX_ATTACK_PENALTY} (0.70)。
     * <p>
     * 默认曲线在惩罚区内线性削弱, 呕吐点时正好达到 30% 的硬上限; 不是线性掉到 0。
     */
    public static double digAttackFactor(float digestion) {
        return 1.0 - digOverloadRatio(digestion) * MAX_ATTACK_PENALTY;
    }

    /**
     * 消化过载对<b>速度类</b>(攻击速度/移动速度/游泳速度/挖掘速度)的效率:
     * 1.0 = 无惩罚, 最低 {@code 1 - MAX_SPEED_PENALTY} (0.40)。
     * <p>
     * 同样默认线性削弱, 呕吐点时正好达到 60% 的硬上限。
     */
    public static double digSpeedFactor(float digestion) {
        return 1.0 - digOverloadRatio(digestion) * MAX_SPEED_PENALTY;
    }

    /**
     * 过载加速倍率: 消化值超过惩罚起点（同一个配置 {@code digestionPenaltyStartPercent}）后，
     * 消化值 → 饥饿值 的转化速率随消化值线性放大 —— 起点处 ×1，满值处 ×(100 / 起点百分比)
     * （默认起点 66.67% → 满值 ×1.5）。
     * <p>
     * 「加速区」与「惩罚区」刻意共用同一配置：吃撑越重，消化越快，惩罚停留时间越短，
     * 调惩罚起点时两者不会错位。消化值低于起点时恒为 1.0，即保持原来的恒定速率。
     */
    public static float digConversionMultiplier(float digestion) {
        float max = maxDigestion();
        if (max <= 0.0F) {
            return 1.0F;
        }
        float start = (float) (max * HungerConfig.digestionPenaltyStartPercent() / 100.0);
        if (start <= 0.0F) {
            // 起点被配成 0: 没有可参照的门槛, 不加速 (否则会除零/无限放大)
            return 1.0F;
        }
        float clamped = Mth.clamp(digestion, 0.0F, max);
        if (clamped <= start) {
            return 1.0F;
        }
        return clamped / start;
    }

    /**
     * 原始消化过载曲线 (1.0 → 0.0, 未套用任何惩罚上限)。
     * <p>
     * 仅保留给外部调用方做"过载程度"参考; 游戏内的属性惩罚已改用
     * {@link #digAttackFactor} / {@link #digSpeedFactor}, 回血不再受消化值影响。
     *
     * @deprecated 实际惩罚请使用 {@link #digAttackFactor} / {@link #digSpeedFactor}
     */
    @Deprecated
    public static double digAttrFactor(float digestion) {
        return 1.0 - digOverloadRatio(digestion);
    }

    /**
     * 回血量百分比: 饥饿值间接缩放回血量 (饥饿值不再直接触发回血, 由饱和度触发)。
     * 饥饿值 0 -> regenAtZeroHungerPercent, 饥饿值满 -> regenAtFullHungerPercent。
     */
    public static float foodRegenPercent(int foodLevel) {
        float max = maxHunger();
        if (max <= 0.0F) {
            return 0.0F;   // 避免 0/0 = NaN 经 player.heal 把血量写成 NaN
        }
        float zero = HungerConfig.regenAtZeroHungerPercent();
        float full = HungerConfig.regenAtFullHungerPercent();
        // 钳制: API 可能把 maxHunger 调低到当前 foodLevel 以下; 不 clamp 会让回血百分比越过 full 档。
        // 同时挡住异常负数, 保证返回值 >= 0, 避免 NaN/负值经 player.heal 污染血量。
        float ratio = Mth.clamp(foodLevel / max, 0.0F, 1.0F);
        return Math.max(0.0F, zero + (full - zero) * ratio);
    }
}
