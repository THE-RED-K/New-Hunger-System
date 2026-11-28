package dev.newhungersystem.api;

import dev.newhungersystem.NewHungerSystem;
import dev.newhungersystem.api.Digestion;
import dev.newhungersystem.api.DigestionCapability;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/**
 * 网络通道: 消化数据同步。
 */
public final class ModNetwork {

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(NewHungerSystem.MOD_ID, "main"),
            () -> "1.3.0",
            "1.3.0"::equals,
            "1.3.0"::equals);

    private ModNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, DigestionSyncPacket.class,
                DigestionSyncPacket::encode, DigestionSyncPacket::decode, DigestionSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    /** 把玩家的消化数据同步到其客户端 */
    public static void syncTo(ServerPlayer player) {
        Digestion digestion = player.getCapability(DigestionCapability.DIGESTION).orElse(null);
        if (digestion == null) {
            return;
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new DigestionSyncPacket(digestion.getDigestion(), digestion.getDigestionSaturation(),
                        player.getFoodData().getExhaustionLevel(), digestion.getDirectHungerCarry(),
                        HungerConfig.serverMaxHunger(), HungerConfig.serverMaxSaturation(),
                        HungerConfig.serverMaxDigestion(), HungerConfig.serverMaxDigestionSaturation()));
    }
}