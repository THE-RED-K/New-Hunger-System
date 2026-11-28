package dev.newhungersystem.api;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C: 同步消化值/消化饱和度/消耗值到客户端 HUD。
 * 消耗值原版不同步到客户端 (AppleSkin 为此专门加了 MessageExhaustionSync), 这里并入同一包。
 */
public class DigestionSyncPacket {

    private final float digestion;
    private final float digestionSaturation;
    private final float exhaustion;
    /** 直接饥饿账本 (0..1), HUD 在饱和度为 0 时用它画第二条消耗条 */
    private final float directHungerCarry;
    private final double maxHunger;
    private final double maxSaturation;
    private final double maxDigestion;
    private final double maxDigestionSaturation;

    public DigestionSyncPacket(float digestion, float digestionSaturation, float exhaustion,
                               float directHungerCarry,
                               double maxHunger, double maxSaturation,
                               double maxDigestion, double maxDigestionSaturation) {
        this.digestion = digestion;
        this.digestionSaturation = digestionSaturation;
        this.exhaustion = exhaustion;
        this.directHungerCarry = directHungerCarry;
        this.maxHunger = maxHunger;
        this.maxSaturation = maxSaturation;
        this.maxDigestion = maxDigestion;
        this.maxDigestionSaturation = maxDigestionSaturation;
    }

    public static void encode(DigestionSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeFloat(packet.digestion);
        buf.writeFloat(packet.digestionSaturation);
        buf.writeFloat(packet.exhaustion);
        buf.writeFloat(packet.directHungerCarry);
        buf.writeDouble(packet.maxHunger);
        buf.writeDouble(packet.maxSaturation);
        buf.writeDouble(packet.maxDigestion);
        buf.writeDouble(packet.maxDigestionSaturation);
    }

    public static DigestionSyncPacket decode(FriendlyByteBuf buf) {
        return new DigestionSyncPacket(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    ClientDigestionData.digestion = this.digestion;
                    ClientDigestionData.digestionSaturation = this.digestionSaturation;
                    ClientDigestionData.exhaustion = this.exhaustion;
                    ClientDigestionData.directHungerCarry = this.directHungerCarry;
                    ClientDigestionData.serverMaxHunger = this.maxHunger;
                    ClientDigestionData.serverMaxSaturation = this.maxSaturation;
                    ClientDigestionData.serverMaxDigestion = this.maxDigestion;
                    ClientDigestionData.serverMaxDigestionSaturation = this.maxDigestionSaturation;
                    ClientDigestionData.hasServerCaps = true;
                }));
        ctx.get().setPacketHandled(true);
    }
}
