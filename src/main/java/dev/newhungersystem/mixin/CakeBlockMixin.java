package dev.newhungersystem.mixin;

import dev.newhungersystem.api.DigestionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 蛋糕切片改为走消化层。
 * <p>
 * 原版 {@code CakeBlock#eat} 是方块进食, 不经过 {@code LivingEntityUseItemEvent.Start/Finish},
 * 所以本模组的进食拦截与快照还原都不会生效, 它会直接调用
 * {@code pPlayer.getFoodData().eat(2, 0.1F)} 把饥饿值/饱和度写死进 FoodData。
 * <p>
 * 这里只重定向那一句写入, 切片消耗、方块状态、统计与音效仍全部保留原版逻辑:
 * 消化值 += nutrition, 消化饱和度 += nutrition × saturationModifier × 2
 * (默认 = 2 点消化值 / 0.4 点消化饱和度, 与原版蛋糕的等价换算一致)。
 * <p>
 * {@code @Redirect} 的 handler 额外参数必须按宿主方法参数顺序从头连续声明, 因此即使只用
 * {@code player}, 也要把 {@code LevelAccessor / BlockPos / BlockState} 一并写出来。
 *
 * @author THEREDK
 */
@Mixin(CakeBlock.class)
public abstract class CakeBlockMixin {

    @Redirect(method = "eat",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;eat(IF)V"))
    private static void newHungerSystem$cakeSliceToDigestion(FoodData foodData, int nutrition,
                                                             float saturationModifier,
                                                             LevelAccessor level, BlockPos pos,
                                                             BlockState state, Player player) {
        // 原版换算: 饥饿 +nutrition, 饱和度 +nutrition × satMod × 2 → 这里等量转成消化层
        DigestionHelper.add(player, nutrition, nutrition * saturationModifier * 2.0F);
    }
}
