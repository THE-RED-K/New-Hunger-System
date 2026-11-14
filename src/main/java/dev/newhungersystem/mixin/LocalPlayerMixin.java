package dev.newhungersystem.mixin;

import dev.newhungersystem.api.HungerConfig;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 疾跑门槛随最大饥饿值缩放。
 * <p>
 * 原版 {@code LocalPlayer#hasEnoughFoodToStartSprinting()} 的逻辑是
 * {@code isPassenger() || (float) getFoodData().getFoodLevel() > 6.0F || getAbilities().mayfly}，
 * 其中 6.0F 写死；提高 {@code maxHunger} 后这个门槛的比例就错了（例如上限 40 时 30% 应是 12）。
 * <p>
 * 这是非核心改动，故只用 {@code @ModifyConstant} 替换那一个 float 常量、完整保留原版方法体
 * 与其余条件（乘骑 / 飞行权限），不使用 {@code @Overwrite}；本类只做注入，优先级保持默认 1000。
 * <p>
 * 客户端专属类，登记在 mixins.json 的 {@code "client"} 列表里。
 *
 * @author THEREDK
 */
@OnlyIn(Dist.CLIENT)
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

    @ModifyConstant(method = "hasEnoughFoodToStartSprinting", constant = @Constant(floatValue = 6.0F))
    private float newHungerSystem$sprintFoodThreshold(float original) {
        return HungerConfig.sprintHungerThreshold();
    }
}
