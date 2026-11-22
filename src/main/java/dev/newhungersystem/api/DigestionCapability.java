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
        private LazyOptional<Digestion> optional;

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

        /**
         * 失效即重建。Forge 在实体从世界移除(含跨维度传送 removePlayerImmediately →
         * Entity.remove → invalidateCaps)时执行这里注册的失效回调; 而 revive() 只会把
         * valid 翻回 true, 不会重建 Provider 或恢复已失效的 LazyOptional。LazyOptional
         * 一但 invalidate() 便永久不可用, 因此必须先丢弃旧实例, 再立即创建一个新实例,
         * 否则维度变更后该实体的 getCapability 永远返回 null (表现为"吃东西不加消化值")。
         */
        public void invalidate() {
            optional.invalidate();
            optional = LazyOptional.of(() -> backend);
        }
    }
}