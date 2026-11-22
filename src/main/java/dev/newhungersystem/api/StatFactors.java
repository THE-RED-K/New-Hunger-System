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
     * 消化过载惩罚: 从 {@code digestionPenaltyStartPercent}（默认 66.67 = 2/3 消化条）开始，
     * 线性降到 {@code vomitThresholdPercent}（呕吐点）处归零；两个锚点之前恒为 1.0。
     * <p>
     * 两个锚点都以 <b>maxDigestion</b> 为基准，因此 maxDigestion / maxHunger / 呕吐阈值
     * 任意独立变化都不会让曲线错位（旧实现以 maxHunger 为基准，上限一改就会出现
     * "后半条永久 0% 效率" 或 "惩罚完全失效"）。
     */
    public static double digAttrFactor(float digestion) {
        double max = maxDigestion();
        if (max <= 0.0) {
            return 1.0;
        }
        double pct = (digestion / max) * 100.0;
        double start = HungerConfig.digestionPenaltyStartPercent();
        double end = HungerConfig.vomitThresholdPercent();
        if (end <= start || pct <= start) {
            return 1.0;
        }
        return Mth.clamp((end - pct) / (end - start), 0.0, 1.0);
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
