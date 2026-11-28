package dev.newhungersystem.mixin;

import dev.newhungersystem.api.DigestionHelper;
import dev.newhungersystem.api.ExhaustionEvent;
import dev.newhungersystem.api.HungerConfig;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 活动消耗全部改为可配置（原版数值见注释）。
 * <p>
 * 覆盖 {@code Player#checkMovementStatistics}（移动/游泳/涉水/爬梯）、{@code Player#hurt}（受伤）、
 * {@code Player#attack}（近战命中）、{@code Player#jumpFromGround}（跳跃）。
 * 破坏方块的 {@code k × hardness} 在 {@link BlockBreakExhaustionMixin}。
 * <p>
 * 移动部分拆成两步，<b>不取消原方法</b>：
 * <ol>
 *   <li>{@code @Inject(HEAD)}：用宿主参数 dx/dy/dz 按当前姿势计费（与原版分支顺序一致）；</li>
 *   <li>6 个 {@code @Redirect}：把原版 6 处固定 {@code causeFoodExhaustion} 吞掉（替换为空），
 *       避免与原版数值重复计费。handler 只接收 {@code (Player, float)}，不依赖任何参数捕获。</li>
 * </ol>
 * 这样原方法体、{@code Stats.*_ONE_CM} 统计与其它模组的注入点全部保留。
 *
 * @author THEREDK
 */
@Mixin(Player.class)
public abstract class PlayerExhaustionMixin {

    /**
     * 本 tick 的移动消耗（每米配置）。
     * 原版系数：游泳/水下/水面 0.01/m，地面冲刺 0.1/m，走路/潜行/爬梯 0。
     */
    @Inject(method = "checkMovementStatistics", at = @At("HEAD"))
    private void newHungerSystem$movementExhaustion(double dx, double dy, double dz, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self.isPassenger()) {
            return;   // 骑乘移动由 checkRidingStatistics 处理, 不消耗
        }

        if (self.isSwimming()) {
            float meters = distance3d(dx, dy, dz);
            if (meters > 0.0F) {
                DigestionHelper.applyExhaustion(self, meters * HungerConfig.swimExhaustionPerMeter(),
                        ExhaustionEvent.Source.SWIM);
            }
        } else if (self.isEyeInFluid(FluidTags.WATER)) {
            float meters = distance3d(dx, dy, dz);
            if (meters > 0.0F) {
                DigestionHelper.applyExhaustion(self, meters * HungerConfig.walkUnderWaterExhaustionPerMeter(),
                        ExhaustionEvent.Source.WALK_UNDER_WATER);
            }
        } else if (self.isInWater()) {
            float meters = distance2d(dx, dz);
            if (meters > 0.0F) {
                DigestionHelper.applyExhaustion(self, meters * HungerConfig.walkOnWaterExhaustionPerMeter(),
                        ExhaustionEvent.Source.WALK_ON_WATER);
            }
        } else if (self.onClimbable()) {
            if (dy > 0.0D) {
                DigestionHelper.applyExhaustion(self, (float) dy * HungerConfig.climbExhaustionPerMeter(),
                        ExhaustionEvent.Source.CLIMB);
            }
        } else if (self.onGround()) {
            float meters = distance2d(dx, dz);
            if (meters > 0.0F) {
                if (self.isSprinting()) {
                    float amount = meters * HungerConfig.sprintExhaustionPerMeter();
                    if (HungerConfig.sprintConsumesHungerDirectly()) {
                        DigestionHelper.consumeHungerDirect(self, amount);
                    } else {
                        DigestionHelper.applyExhaustion(self, amount, ExhaustionEvent.Source.SPRINT);
                    }
                } else if (self.isCrouching()) {
                    DigestionHelper.applyExhaustion(self, meters * HungerConfig.crouchExhaustionPerMeter(),
                            ExhaustionEvent.Source.CROUCH);
                } else {
                    DigestionHelper.applyExhaustion(self, meters * HungerConfig.walkExhaustionPerMeter(),
                            ExhaustionEvent.Source.WALK);
                }
            }
        }
        // isFallFlying / 普通飞行: 原版无消耗, 这里同样不产生消耗
    }

    /**
     * ordinal 顺序 = 原版 {@code checkMovementStatistics} 中 6 个
     * {@code causeFoodExhaustion} 调用的出现顺序：
     * 0 游泳、1 头部在水里行走、2 浅水/水面行走、3 地面冲刺、4 潜行、5 普通走路。
     * <p>
     * 只做“归零”，实际计费已由上面的 HEAD 注入完成。
     */
    @Redirect(method = "checkMovementStatistics",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V",
                    ordinal = 0))
    private void newHungerSystem$suppressSwim(Player player, float amount) {
    }

    @Redirect(method = "checkMovementStatistics",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V",
                    ordinal = 1))
    private void newHungerSystem$suppressWalkUnderWater(Player player, float amount) {
    }

    @Redirect(method = "checkMovementStatistics",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V",
                    ordinal = 2))
    private void newHungerSystem$suppressWalkOnWater(Player player, float amount) {
    }

    @Redirect(method = "checkMovementStatistics",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V",
                    ordinal = 3))
    private void newHungerSystem$suppressSprint(Player player, float amount) {
    }

    @Redirect(method = "checkMovementStatistics",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V",
                    ordinal = 4))
    private void newHungerSystem$suppressCrouch(Player player, float amount) {
    }

    @Redirect(method = "checkMovementStatistics",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V",
                    ordinal = 5))
    private void newHungerSystem$suppressWalk(Player player, float amount) {
    }

    /**
     * 受伤消耗：保留原版"按伤害类型 0 / 0.1"的差异，只整体乘一个倍率。
     * 原版 {@code Player#hurt} 里只有这一处 {@code causeFoodExhaustion}。
     */
    @Redirect(method = "hurt",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V"))
    private void newHungerSystem$damageExhaustion(Player player, float amount) {
        DigestionHelper.applyExhaustion(player, amount * HungerConfig.damageExhaustionMultiplier(),
                ExhaustionEvent.Source.HURT);
    }

    /**
     * 近战命中：只在"真的造成伤害"时触发（原版结构不变），数值改为配置。
     */
    @Redirect(method = "attack",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V"))
    private void newHungerSystem$attackExhaustion(Player player, float amount) {
        float exhaustion = HungerConfig.attackExhaustion();
        if (HungerConfig.attackConsumesHungerDirectly()) {
            DigestionHelper.consumeHungerDirect(player, exhaustion);
        } else {
            DigestionHelper.applyExhaustion(player, exhaustion, ExhaustionEvent.Source.ATTACK);
        }
    }

    /** 冲刺跳（原版 0.2，是 jumpFromGround 里的第 1 处调用）。 */
    @Redirect(method = "jumpFromGround",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V",
                    ordinal = 0))
    private void newHungerSystem$sprintJumpExhaustion(Player player, float amount) {
        float exhaustion = HungerConfig.sprintJumpExhaustion();
        if (HungerConfig.sprintJumpConsumesHungerDirectly()) {
            DigestionHelper.consumeHungerDirect(player, exhaustion);
        } else {
            DigestionHelper.applyExhaustion(player, exhaustion, ExhaustionEvent.Source.SPRINT_JUMP);
        }
    }

    /** 普通跳跃（原版 0.05，是 jumpFromGround 里的第 2 处调用）。 */
    @Redirect(method = "jumpFromGround",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V",
                    ordinal = 1))
    private void newHungerSystem$jumpExhaustion(Player player, float amount) {
        DigestionHelper.applyExhaustion(player, HungerConfig.jumpExhaustion(), ExhaustionEvent.Source.JUMP);
    }

    /** 原版按 cm 取整后再换算成米，这里保持同样的取整，避免与统计漂移。 */
    private static float distance3d(double dx, double dy, double dz) {
        return Math.round((float) Math.sqrt(dx * dx + dy * dy + dz * dz) * 100.0F) * 0.01F;
    }

    private static float distance2d(double dx, double dz) {
        return Math.round((float) Math.sqrt(dx * dx + dz * dz) * 100.0F) * 0.01F;
    }
}
