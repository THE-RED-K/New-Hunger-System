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
    @Shadow
    private int tickTimer;
    /** 原版每 tick 记录上一次饥饿值, 供原版饥饿条的变化动画读取 */
    @Shadow
    private int lastFoodLevel;
    @Shadow
    public abstract void addExhaustion(float amount);

    /**
     * 重写原版饥饿回血曲线; 全部数值来自 {@link HungerConfig} 的 regen / food 配置组:
     * - 快速回血由饱和度驱动, 慢速回血节拍 = 快回 × slowRegenSpeedDivisor, 单次回血量相同;
     * - 两者计价 = heal × regenExhaustionPerHealth (原版 1 点血 = 6.0 消耗值);
     * - 回血量由 foodRegenPercent (饥饿值缩放) 与消化过载因子决定;
     * - 保留原版 gamerule; 饥饿掉血**故意不做原版难度判定**(Normal 保 1 血 / Easy 保 10 血 /
     *   Peaceful 免伤), 强度交给 starveDamage* 配置; 和平模式的饥饿回复仍沿用原版 (Player.aiStep)。
     *
     * @author THEREDK
     * @reason 实现基于饱和度/饥饿值的自定义回血曲线: 饥饿值仅缩放回血量, 不再直接触发回血; 消化过载额外减速
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
        float healCost = HungerConfig.regenExhaustionPerHealth();

        // 新回血: 饱和度驱动 + 饥饿值缩放回血量 + 消化过载减速
        if (regen && this.saturationLevel > 0.0F && player.isHurt()) {
            this.tickTimer++;
            if (this.tickTimer >= HungerConfig.fastRegenIntervalTicks()) {
                float heal = StatFactors.foodRegenPercent(this.foodLevel) / 100.0F * digRegenFactor(player);
                if (heal > 0.0F) {
                    player.heal(heal);
                }
                this.addExhaustion(heal * healCost);
                this.tickTimer = 0;
            }
        } else if (regen && this.saturationLevel <= 0.0F
                && this.foodLevel >= StatFactors.maxHunger() * HungerConfig.slowRegenHungerRatio()
                && player.isHurt()) {
            // 慢速回血 (饥饿值达标、饱和度耗尽时): 节拍 = 快回 × 倍率, 单次回血量相同
            this.tickTimer++;
            if (this.tickTimer >= HungerConfig.slowRegenIntervalTicks()) {
                float heal = StatFactors.foodRegenPercent(this.foodLevel) / 100.0F * digRegenFactor(player);
                if (heal > 0.0F) {
                    player.heal(heal);
                }
                this.addExhaustion(heal * healCost);
                this.tickTimer = 0;
            }
        } else if (this.foodLevel <= 0) {
            // 饥饿掉血。
            // 注意: 与原版分支C 不同, 这里**故意不做难度判定** —— 原版会保证
            // Normal 至少留 1 点血、Easy 至少留 10 点血、Peaceful 不受饿死伤害,
            // 本模组按 starveDamageIntervalTicks / starveDamageAmount 无条件扣血
            // (由整合包用配置控制强度, 而不是被难度悄悄兜底)。
            this.tickTimer++;
            if (this.tickTimer >= HungerConfig.starveDamageIntervalTicks()) {
                player.hurt(player.damageSources().starve(), HungerConfig.starveDamageAmount());
                this.tickTimer = 0;
            }
        } else {
            // 原版行为: 三个分支都没命中时归零计时器。
            // 漏掉这一步会让计时跨状态累积, 条件一恢复就"瞬间回血/瞬间掉血"。
            this.tickTimer = 0;
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