package dev.newhungersystem.mixin;

import dev.newhungersystem.api.HungerConfig;
import dev.newhungersystem.api.StatFactors;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 和平模式自动恢复的开关（合并自 PeacefulDepletion 1.0.2, MIT, MapleSugar365）。
 * <p>
 * 原版 {@code Player#aiStep} 有一段和平模式专属逻辑：
 * <pre>
 * if (level.getDifficulty() == PEACEFUL &amp;&amp; naturalRegeneration) {
 *     if (health &lt; maxHealth) heal(1.0F);                    // 每 20 tick 自动回血
 *     if (foodData.needsFood()) foodData.setFoodLevel(+1);   // 每 10 tick 自动补饥饿
 * }
 * </pre>
 * 本模组把 {@code needsFood()} 重写为 {@code alwaysEdible || foodLevel &lt; maxHunger}（默认恒真），
 * 所以原版这条补饥饿分支在没有上限保护时会让饥饿值无限增长。
 * <p>
 * 这里做两件事：
 * <ol>
 *   <li>配置开启时（默认）把该处的难度判定伪装成 {@link Difficulty#HARD}，整段和平逻辑不再执行；</li>
 *   <li>无论配置如何，都把该处的 {@code setFoodLevel(+1)} clamp 到 {@link StatFactors#maxHunger()}，
 *       避免关闭配置后重新出现无上限增长。</li>
 * </ol>
 * 与 Difficulty Overhaul 不冲突：它 mixin 的是 {@code Player.hurt} / {@code Mob} /
 * {@code DifficultyInstance} / 创建世界界面，不触碰 {@code Player.aiStep}，注入点不重叠；
 * 但它的"和平"模式描述是"饥饿不下降"，本配置开启时以本模组为准（配置注释已说明）。
 *
 * @author THEREDK
 */
@Mixin(Player.class)
public abstract class PlayerPeacefulMixin {

    /**
     * 配置开启时把 aiStep 里的难度判定当作 HARD；关闭时保留原版判定。
     * 该调用在 {@code Player#aiStep} 中只有和平分支这一处。
     */
    @Redirect(method = "aiStep",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getDifficulty()Lnet/minecraft/world/Difficulty;"))
    private Difficulty newHungerSystem$peacefulDifficulty(Level level) {
        return HungerConfig.disablePeacefulAutoRegen() ? Difficulty.HARD : level.getDifficulty();
    }

    /**
     * 安全网：和平模式的 {@code setFoodLevel(getFoodLevel() + 1)} 不允许越过最大饥饿值。
     * 原版依赖 {@code needsFood()} 返回 {@code foodLevel < 20} 来兜底，本模组重写了该方法，必须自己钳制。
     */
    @Redirect(method = "aiStep",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;setFoodLevel(I)V"))
    private void newHungerSystem$clampPeacefulFood(FoodData foodData, int foodLevel) {
        foodData.setFoodLevel(Math.min(foodLevel, (int) Math.floor(StatFactors.maxHunger())));
    }
}
