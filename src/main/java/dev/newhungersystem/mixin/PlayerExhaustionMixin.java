package dev.newhungersystem.mixin;

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
 * <p>
 * 为什么不用 {@code @ModifyArg}：Mixin 0.8.5 的 handler 只能拿到被替换的实参，无法捕获宿主参数
 * （实测 {@code targets a method with an invalid signature (F), expected (FDDD)}）。
 * 为什么不让 {@code @Redirect} 直接捕获宿主参数：该特性在不同 Mixin 版本/组合下不稳定，
 * 而“HEAD 计费 + Redirect 归零”只依赖最基础的 {@code @Inject} 参数捕获，风险最低。
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
                self.causeFoodExhaustion(meters * HungerConfig.swimExhaustionPerMeter());
            }
        } else if (self.isEyeInFluid(FluidTags.WATER)) {
            float meters = distance3d(dx, dy, dz);
            if (meters > 0.0F) {
                self.causeFoodExhaustion(meters * HungerConfig.walkUnderWaterExhaustionPerMeter());
            }
        } else if (self.isInWater()) {
            float meters = distance2d(dx, dz);
            if (meters > 0.0F) {
                self.causeFoodExhaustion(meters * HungerConfig.walkOnWaterExhaustionPerMeter());
            }
        } else if (self.onClimbable()) {
            if (dy > 0.0D) {
                self.causeFoodExhaustion((float) dy * HungerConfig.climbExhaustionPerMeter());
            }
        } else if (self.onGround()) {
            float meters = distance2d(dx, dz);
            if (meters > 0.0F) {
                if (self.isSprinting()) {
                    self.causeFoodExhaustion(meters * HungerConfig.sprintExhaustionPerMeter());
                } else if (self.isCrouching()) {
                    self.causeFoodExhaustion(meters * HungerConfig.crouchExhaustionPerMeter());
                } else {
                    self.causeFoodExhaustion(meters * HungerConfig.walkExhaustionPerMeter());
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
        player.causeFoodExhaustion(amount * HungerConfig.damageExhaustionMultiplier());
    }

    /**
     * 近战命中：只在"真的造成伤害"时触发（原版结构不变），数值改为配置。
     */
    @Redirect(method = "attack",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V"))
    private void newHungerSystem$attackExhaustion(Player player, float amount) {
        player.causeFoodExhaustion(HungerConfig.attackExhaustion());
    }

    /** 冲刺跳（原版 0.2，是 jumpFromGround 里的第 1 处调用）。 */
    @Redirect(method = "jumpFromGround",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V",
                    ordinal = 0))
    private void newHungerSystem$sprintJumpExhaustion(Player player, float amount) {
        player.causeFoodExhaustion(HungerConfig.sprintJumpExhaustion());
    }

    /** 普通跳跃（原版 0.05，是 jumpFromGround 里的第 2 处调用）。 */
    @Redirect(method = "jumpFromGround",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V",
                    ordinal = 1))
    private void newHungerSystem$jumpExhaustion(Player player, float amount) {
        player.causeFoodExhaustion(HungerConfig.jumpExhaustion());
    }

    /** 原版按 cm 取整后再换算成米，这里保持同样的取整，避免与统计漂移。 */
    private static float distance3d(double dx, double dy, double dz) {
        return Math.round((float) Math.sqrt(dx * dx + dy * dy + dz * dz) * 100.0F) * 0.01F;
    }

    private static float distance2d(double dx, double dz) {
        return Math.round((float) Math.sqrt(dx * dx + dz * dz) * 100.0F) * 0.01F;
    }
}
