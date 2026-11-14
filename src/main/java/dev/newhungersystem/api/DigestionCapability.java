package dev.newhungersystem.api;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nullable;

/**
 * 消化系统 Capability: 附加到 Player 上, 随玩家存档持久化。
 */
public final class DigestionCapability {

    public static final Capability<Digestion> DIGESTION = CapabilityManager.get(new CapabilityToken<>() {
    });

    private DigestionCapability() {
    }

    public static class Provider implements ICapabilitySerializable<CompoundTag> {

        private final Digestion backend;
        private final LazyOptional<Digestion> optional;

        /** 传入 owner, 使数据层 setter 的溢出能广播 DigestionOverflowEvent */
        public Provider(Player owner) {
            this.backend = new Digestion(owner);
            this.optional = LazyOptional.of(() -> backend);
        }

        @Nullable
        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
            return cap == DIGESTION ? optional.cast() : LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            return backend.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            backend.deserializeNBT(nbt);
        }

        public void invalidate() {
            optional.invalidate();
        }
    }
}
