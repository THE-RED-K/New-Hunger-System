package dev.newhungersystem.api;

import net.minecraft.world.entity.player.Player;

/** 消耗值上限查询入口 (对齐 AppleSkin HungerHelper，但读取本模组可配置上限)。 */
public final class HungerHelper {

    private HungerHelper() {
    }

    public static float getMaxExhaustion(Player player) {
        return HungerConfig.maxExhaustion();
    }
}
