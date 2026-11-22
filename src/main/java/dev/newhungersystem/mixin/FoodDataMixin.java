package dev.newhungersystem.mixin;

import dev.newhungersystem.api.Digestion;
import dev.newhungersystem.api.DigestionCapability;
import dev.newhungersystem.api.HungerConfig;
import dev.newhungersystem.api.ModAttributes;
import dev.newhungersystem.api.StatFactors;
import dev.newhungersystem.NewHungerSystem;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.GameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import static dev.newhungersystem.api.MixinPriorities.TAKE_OVER_FINAL;

/**
 * 核心接管型 mixin: 本类含全部 {@code @Overwrite}, 故按约定使用最低优先级
 * {@code TAKE_OVER_FINAL}（-Max）；仅做注入的 FoodDataInitMixin / MobEffectMixin 保持默认 1000。
 */
@Mixin(value = FoodData.class, priority = TAKE_OVER_FINAL)
public abstract class FoodDataMixin {

    /** 临时诊断 (排查整合包兼容问题时启用, 排查完毕后移除) */
    private static final Logger DIAG = LoggerFactory.getLogger(NewHungerSystem.MOD_ID + ".diag");
    private static int TICK_COUNT;

    @Shadow
    private int foodLevel;
    @Shadow
    private float saturationLevel;
    @Shadow
    private float exhaustionLevel;
    /** 原版计时器, 本模组现在只用于饥饿掉血分支 */
    @Shadow
    private int tickTimer;
    /** 原版每 tick 记录上一次饥饿值, 供原版饥饿条的变化动画读取 */
    @Shadow
    private int lastFoodLevel;
    @Shadow
    public abstract void addExhaustion(float amount);

    /** 快轨(饱和度驱动)独立计时器 — 与慢轨并行, 互不抢占 */
    private int nhsFastRegenTimer;
    /** 慢轨(饥饿值驱动)独立计时器 — 饱和度 > 0 时同样在跑 */
    private int nhsSlowRegenTimer;

    /**
     * 重写原版饥饿回血曲线; 全部数值来自 {@link HungerConfig} 的 regen / food 配置组。
     * <p>
     * <b>双轨并行</b>：
     * <ul>
     *   <li><b>共同钳制</b>：两轨都要求 饥饿值 ≥ {@code maxHunger × slowRegenHungerRatio}（默认 60%），
     *       跌破即两轨同时停止 —— 这是"回着回着自发暂停"的点；</li>
     *   <li>快轨 = 饱和度 &gt; 0 且受伤，节拍 {@code fastRegenIntervalTicks}（默认 10 tick）；</li>
     *   <li>慢轨 = 饥饿值达标且受伤，节拍 {@code slowRegenIntervalTicks}（默认 40 tick）。
     *       <b>不再要求饱和度耗尽</b>，所以饥饿值达标时两轨会同时回血、同时计价消耗值。</li>
     * </ul>
     * 两轨各自有独立计时器，任一轨条件不满足就立刻把自己的计时器归零（防止恢复条件时瞬间连发）。
     * 由于两轨都在扣消耗值，饥饿值会持续下降，跌破慢轨门槛后回血<b>自发暂停</b>，
     * 同时腾出空间让消化值继续转化，降低消化值堆积到呕吐阈值的概率。
     * <p>
     * 单次回血量 = {@code foodRegenPercent(foodLevel)/100 × digRegenFactor(player)}，
     * 计价 = <b>实际回血量</b> × {@code regenExhaustionPerHealth}（默认 6.0）。
     * 保留原版 gamerule；饥饿掉血是独立第三分支，**故意不做原版难度判定**
     * (Normal 保 1 血 / Easy 保 10 血 / Peaceful 免伤由原版处理，本模组按配置无条件扣血)。
     *
     * @author THEREDK
     * @reason 实现饱和度与饥饿值双轨并行的回血曲线；饥饿值缩放回血量并作为慢轨门槛，消化过载额外减速
     */
    @Overwrite
    public void tick(Player player) {
        // 原版在这里记录 lastFoodLevel, 供原版饥饿条的变化动画使用
        this.lastFoodLevel = this.foodLevel;

        // 临时诊断: 每 10 秒采样一次原版 tick 行为是否在跑
        if (++TICK_COUNT % 200 == 0) {
            DIAG.info("[DIAG] FoodData.tick ALIVE #{} player={} food=({}|{}) exc={}", TICK_COUNT,
                    player.getScoreboardName(), this.foodLevel, this.saturationLevel, this.exhaustionLevel);
        }

        // 消耗/饥饿衰减 (沿用原版, maxExhaustion 可配置; 原版判定是严格大于 4.0F)
        float maxExhaustion = HungerConfig.maxExhaustion();
        if (this.exhaustionLevel > maxExhaustion) {
            this.exhaustionLevel -= maxExhaustion;
            if (this.saturationLevel > 0.0F) {
                this.saturationLevel = Math.max(0.0F, this.saturationLevel - 1.0F);
            } else if (player.level().getDifficulty() != Difficulty.PEACEFUL) {
                this.foodLevel = Math.max(0, this.foodLevel - 1);
            }
        }

        boolean regen = player.level().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION);
        boolean hurt = player.isHurt();
        float healCost = HungerConfig.regenExhaustionPerHealth();
        // 共同钳制: 两轨共用同一个饥饿值门槛 (默认 60%)。低于门槛时即使饱和度充足也不回血。
        boolean hungerOk = this.foodLevel >= StatFactors.maxHunger() * HungerConfig.slowRegenHungerRatio();

        // ---- 快轨: 饱和度驱动 (与慢轨并行, 使用独立计时器) ----
        if (regen && hurt && hungerOk && this.saturationLevel > 0.0F) {
            if (++this.nhsFastRegenTimer >= HungerConfig.fastRegenIntervalTicks()) {
                this.applyRegenHeal(player, healCost);
                this.nhsFastRegenTimer = 0;
            }
        } else {
            this.nhsFastRegenTimer = 0;
        }

        // ---- 慢轨: 饥饿值驱动 ----
        // 不再写 saturationLevel <= 0, 饱和度还有时会与快轨同时回血; 门槛与快轨共用 hungerOk。
        if (regen && hurt && hungerOk) {
            if (++this.nhsSlowRegenTimer >= HungerConfig.slowRegenIntervalTicks()) {
                this.applyRegenHeal(player, healCost);
                this.nhsSlowRegenTimer = 0;
            }
        } else {
            this.nhsSlowRegenTimer = 0;
        }

        // ---- 饥饿掉血: 独立第三分支, 不受回血计时器影响 ----
        // 注意: 与原版分支 C 不同, 这里**故意不做难度判定** —— 原版会保证
        // Normal 至少留 1 点血、Easy 至少留 10 点血、Peaceful 不受饿死伤害,
        // 本模组按 starveDamageIntervalTicks / starveDamageAmount 无条件扣血
        // (由整合包用配置控制强度, 而不是被难度悄悄兜底)。
        if (this.foodLevel <= 0) {
            this.tickTimer++;
            if (this.tickTimer >= HungerConfig.starveDamageIntervalTicks()) {
                player.hurt(player.damageSources().starve(), HungerConfig.starveDamageAmount());
                this.tickTimer = 0;
            }
        } else {
            // 三个分支都没命中时归零计时器, 避免跨状态累积造成瞬间掉血。
            this.tickTimer = 0;
        }
    }

    /**
     * 执行一次回血并计价。两轨共用。
     * <p>
     * 钳制检查：
     * <ol>
     *   <li>{@link StatFactors#foodRegenPercent(int)} 已把饥饿值比例 clamp 到 [0,1]，
     *       所以即使 API 调低 maxHunger 导致 foodLevel &gt; maxHunger 也不会超量回血；</li>
     *   <li>{@code digRegenFactor} 来自自定义属性（范围 0~1024），其它模组可能写出 NaN/负值，
     *       必须先做有限性与正值判断，否则 NaN 会污染血量与消耗值；</li>
     *   <li>按 {@code player.heal()} 实际回的血量计价，而不是按请求的 heal 计价 ——
     *       满血附近 player.heal 会自行截短，按请求值收费会白扣消耗值。</li>
     * </ol>
     */
    private void applyRegenHeal(Player player, float healCost) {
        float heal = StatFactors.foodRegenPercent(this.foodLevel) / 100.0F * digRegenFactor(player);
        if (!Float.isFinite(heal) || heal <= 0.0F) {
            return;
        }
        float before = player.getHealth();
        player.heal(heal);
        float actual = player.getHealth() - before;
        if (actual > 0.0F) {
            this.addExhaustion(actual * healCost);
        }
    }

    /**
     * alwaysEdible 为 true 时实现 AlwaysEat 效果 (任何时候都能进食, 即使饥饿/消化已满);
     * 为 false 时退回原版语义 (按可配置上限: foodLevel &lt; maxHunger), 便于与同类模组共存。
     *
     * @author THEREDK
     * @reason 本模组"食物 -> 消化值"的设计要求可随时进食; 是否启用交给配置
     */
    @Overwrite
    public boolean needsFood() {
        return HungerConfig.alwaysEdible() || this.foodLevel < StatFactors.maxHunger();
    }

    /**
     * 原版 eat 把饥饿值/饱和度 clamp 到 20, 提高上限后无效。改为 clamp 到可配置上限。
     *
     * @author THEREDK
     * @reason 让 raised cap (maxHunger/maxSaturation) 真正生效, 不再被原版硬编码 20 截断
     */
    @Overwrite
    public void eat(int foodLevelIn, float saturationLevelIn) {
        float maxHunger = StatFactors.maxHunger();
        float satBefore = this.saturationLevel;
        int foodBefore = this.foodLevel;
        this.foodLevel = (int) Math.min((double) foodLevelIn + this.foodLevel, (double) maxHunger);
        this.saturationLevel = Math.min(this.saturationLevel + saturationLevelIn, this.foodLevel);
        DIAG.info("[DIAG] FoodData.eat +{} / +{} : ({}, {}) -> ({}, {})", foodLevelIn, saturationLevelIn,
                foodBefore, satBefore, this.foodLevel, this.saturationLevel);
    }

    /**
     * 消化值 (>100%) 对回血的减速因子 (仅影响自然/食物回血, 不影响药水等)。
     * 读取自定义属性 digestion_efficiency, 因此其它模组对该属性加的修饰器同样影响回血。
     */
    private static float digRegenFactor(Player player) {
        if (ModAttributes.available(player)) {
            return (float) ModAttributes.digestionEfficiency(player);
        }
        Digestion digestion = player.getCapability(DigestionCapability.DIGESTION).orElse(null);
        return digestion == null ? 1.0F : (float) StatFactors.digAttrFactor(digestion.getDigestion());
    }
}
