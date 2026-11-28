package dev.newhungersystem.mixin;

import dev.newhungersystem.api.HungerConfig;
import dev.newhungersystem.api.StatFactors;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.GameRules;
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

    /** 快轨(饱和度驱动)独立计时器 — 与慢轨并行, 互不抢占 */
    private int nhsFastRegenTimer;
    /** 慢轨(饥饿值驱动)独立计时器 — 饱和度 > 0 时同样在跑 */
    private int nhsSlowRegenTimer;
    /**
     * 快轨回血专属消耗值账本 —— <b>只</b>结算为饱和度消耗。
     * <p>
     * 回血消耗不与原版 {@link #exhaustionLevel} 混用: 共用一本账时, tick 顶部"先饱和后饥饿"
     * 的结算顺序会让慢轨的消耗优先扣饱和度, 与"快轨烧饱和、慢轨烧饥饿"的设计不符。
     */
    private float nhsFastRegenExhaustion;
    /** 慢轨回血专属消耗值账本 —— <b>只</b>结算为饥饿值消耗。 */
    private float nhsSlowRegenExhaustion;

    /**
     * 重写原版饥饿回血曲线; 全部数值来自 {@link HungerConfig} 的 regen / food 配置组。
     * <p>
     * <b>双轨并行</b>：
     * <ul>
     *   <li><b>共同钳制</b>：两轨都要求 饥饿值 ≥ {@code maxHunger × slowRegenHungerRatio}（默认 60%），
     *       跌破即两轨同时停止 —— 这是"回着回着自发暂停"的点；</li>
     *   <li>快轨 = 饱和度 &gt; 0 且受伤，节拍 {@code fastRegenIntervalTicks}（默认 10 tick），
     *       <b>只消耗饱和度</b>；</li>
     *   <li>慢轨 = 饥饿值达标且受伤，节拍 {@code slowRegenIntervalTicks}（默认 40 tick），
     *       <b>只消耗饥饿值</b>。<b>不要求饱和度耗尽</b>，所以两轨可同时回血。</li>
     * </ul>
     * 两轨各自有独立计时器，任一轨条件不满足就立刻把自己的计时器归零（防止恢复条件时瞬间连发）。
     * 回血消耗记在两本独立账本里（{@link #nhsFastRegenExhaustion} / {@link #nhsSlowRegenExhaustion}），
     * 与原版活动消耗 {@code exhaustionLevel} 互不相干 —— 不会出现"慢轨的消耗优先扣饱和度"。
     * <p>
     * 单次回血量 = {@code foodRegenPercent(foodLevel)/100}，
     * 计价 = <b>实际回血量</b> × {@code regenExhaustionPerHealth}（默认 6.0），
     * 每满 {@code maxExhaustion} 扣 1 点对应燃料。
     * 消化值<b>不再</b>影响回血量。
     * 保留原版 gamerule；饥饿掉血是独立第三分支，**故意不做原版难度判定**
     * (Normal 保 1 血 / Easy 保 10 血 / Peaceful 免伤由原版处理，本模组按配置无条件扣血)。
     *
     * @author THEREDK
     * @reason 实现饱和度与饥饿值双轨并行的回血曲线；饥饿值缩放回血量并作为慢轨门槛
     */
    @Overwrite
    public void tick(Player player) {
        // 原版在这里记录 lastFoodLevel, 供原版饥饿条的变化动画使用
        this.lastFoodLevel = this.foodLevel;

        // 消耗/饥饿衰减 (沿用原版, maxExhaustion 可配置; 原版判定是严格大于 4.0F)
        // 这本账只记录玩家活动消耗, 回血消耗走下面两本独立账本, 互不影响。
        float maxExhaustion = HungerConfig.maxExhaustion();
        if (this.exhaustionLevel > maxExhaustion) {
            this.exhaustionLevel -= maxExhaustion;
            if (this.saturationLevel > 0.0F) {
                this.saturationLevel = Math.max(0.0F, this.saturationLevel - 1.0F);
            } else if (HungerConfig.disablePeacefulAutoRegen()
                    || player.level().getDifficulty() != Difficulty.PEACEFUL) {
                // 和平模式下也照常扣饥饿 (合并自 PeacefulDepletion: 把难度判定当成 HARD)
                this.foodLevel = Math.max(0, this.foodLevel - 1);
            }
        }

        // 回血燃料结算: 两条轨道各烧各的 —— 快轨只扣饱和度, 慢轨只扣饥饿值。
        // 不看另一项燃料是否充足, 所以饱和度充足时慢轨照样消耗饥饿值 (反之亦然)。
        if (this.nhsFastRegenExhaustion > maxExhaustion) {
            this.nhsFastRegenExhaustion -= maxExhaustion;
            this.saturationLevel = Math.max(0.0F, this.saturationLevel - 1.0F);
        }
        if (this.nhsSlowRegenExhaustion > maxExhaustion) {
            this.nhsSlowRegenExhaustion -= maxExhaustion;
            this.foodLevel = Math.max(0, this.foodLevel - 1);
        }

        boolean regen = player.level().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION);
        boolean hurt = player.isHurt();
        float healCost = HungerConfig.regenExhaustionPerHealth();
        // 共同钳制: 两轨共用同一个饥饿值门槛 (默认 60%)。低于门槛时即使饱和度充足也不回血。
        boolean hungerOk = this.foodLevel >= StatFactors.maxHunger() * HungerConfig.slowRegenHungerRatio();

        // ---- 快轨: 饱和度驱动 (与慢轨并行, 使用独立计时器; 燃料只用饱和度) ----
        if (regen && hurt && hungerOk && this.saturationLevel > 0.0F) {
            if (++this.nhsFastRegenTimer >= HungerConfig.fastRegenIntervalTicks()) {
                this.applyRegenHeal(player, healCost, true);
                this.nhsFastRegenTimer = 0;
            }
        } else {
            this.nhsFastRegenTimer = 0;
        }

        // ---- 慢轨: 饥饿值驱动 (燃料只用饥饿值, 不吃饱和度) ----
        // 不再写 saturationLevel <= 0, 饱和度还有时会与快轨同时回血; 门槛与快轨共用 hungerOk。
        if (regen && hurt && hungerOk) {
            if (++this.nhsSlowRegenTimer >= HungerConfig.slowRegenIntervalTicks()) {
                this.applyRegenHeal(player, healCost, false);
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
     * 执行一次回血并记入对应轨道的燃料账本。
     * <p>
     * 钳制检查：
     * <ol>
     *   <li>{@link StatFactors#foodRegenPercent(int)} 已把饥饿值比例 clamp 到 [0,1]，
     *       所以即使 API 调低 maxHunger 导致 foodLevel &gt; maxHunger 也不会超量回血；</li>
     *   <li>按 {@code player.heal()} 实际回的血量计价，而不是按请求的 heal 计价 ——
     *       满血附近 player.heal 会自行截短，按请求值收费会白扣燃料。</li>
     * </ol>
     *
     * @param fromSaturation true = 快轨（只烧饱和度）, false = 慢轨（只烧饥饿值）
     */
    private void applyRegenHeal(Player player, float healCost, boolean fromSaturation) {
        float heal = StatFactors.foodRegenPercent(this.foodLevel) / 100.0F;
        if (!Float.isFinite(heal) || heal <= 0.0F) {
            return;
        }
        float before = player.getHealth();
        player.heal(heal);
        float actual = player.getHealth() - before;
        if (actual > 0.0F) {
            float cost = actual * healCost;
            if (fromSaturation) {
                this.nhsFastRegenExhaustion += cost;
            } else {
                this.nhsSlowRegenExhaustion += cost;
            }
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
     * <p>
     * 第二参数保持<b>原版语义</b>（saturationModifier，不是绝对饱和增量）：
     * {@code saturation += nutrition × modifier × 2}。这样其它模组/原版路径直接调用
     * {@code FoodData.eat(int,float)} 时得到的饱和换算仍正确；本模组的进食结算
     * （消化层）不依赖这个公式——{@link DigestionEvents} 会还原快照，蛋糕则由
     * {@link CakeBlockMixin} 改道。
     *
     * @author THEREDK
     * @reason 让 raised cap (maxHunger/maxSaturation) 真正生效, 不再被原版硬编码 20 截断
     */
    @Overwrite
    public void eat(int foodLevelIn, float saturationLevelIn) {
        float maxHunger = StatFactors.maxHunger();
        this.foodLevel = (int) Math.min((double) foodLevelIn + this.foodLevel, (double) maxHunger);
        this.saturationLevel = Math.min(
                this.saturationLevel + (float) foodLevelIn * saturationLevelIn * 2.0F, (float) this.foodLevel);
    }
}
