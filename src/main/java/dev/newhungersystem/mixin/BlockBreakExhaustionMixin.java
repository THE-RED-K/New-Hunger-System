package dev.newhungersystem.mixin;

import dev.newhungersystem.api.HungerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 破坏方块消耗重做：{@code exhaustion = k × hardness}（{@code k} 默认 0.01）。
 * <p>
 * 原版是固定 {@code 0.005/块}，与方块硬度无关：挖泥土和挖黑曜石一个价。改为按硬度计费后，
 * 挖石头（硬度 1.5）为 0.015、挖黑曜石（硬度 50）为 0.5，而火把/草这类硬度 0 的方块免费。
 * <p>
 * 触发条件保持原版：只有生存/冒险模式、且方块"能正常掉落"（{@code canHarvestBlock}）时
 * {@code playerDestroy} 才会被调用；创造模式走另一条分支，不经过这里。
 * 硬度通过 {@code BlockState#getDestroySpeed} 获取（会正确反映方块状态/位置的动态硬度）。
 *
 * @author THEREDK
 */
@Mixin(Block.class)
public abstract class BlockBreakExhaustionMixin {

    @Redirect(method = "playerDestroy",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V"))
    private void newHungerSystem$blockBreakExhaustion(Player player, float original,
                                                      Level level, Player hostPlayer, BlockPos pos,
                                                      BlockState state, BlockEntity blockEntity,
                                                      ItemStack tool) {
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness > 0.0F) {
            player.causeFoodExhaustion(HungerConfig.blockBreakExhaustionPerHardness() * hardness);
        }
    }
}
