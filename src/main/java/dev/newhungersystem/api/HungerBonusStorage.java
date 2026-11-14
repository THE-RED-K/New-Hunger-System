package dev.newhungersystem.api;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 把 {@link HungerBonusData} 绑定/解绑到 {@link HungerConfig}。
 * <ul>
 *   <li>服务器启动完成（{@link ServerStartedEvent}，此时主世界已加载）：从存档读取加成并绑定，
 *       之后 {@link HungerConfig#addMaxHunger} 等 API 的调用会直接写入该存档并标脏；</li>
 *   <li>服务器停止（{@link ServerStoppingEvent}）：再标脏一次确保落盘，然后解绑，
 *       便于同一进程内切换世界时重新读取新存档。</li>
 * </ul>
 * 未绑定（如启动阶段、专用客户端）时 {@code addMaxXxx} 落到 {@link HungerConfig} 的运行期字段，
 * 不写存档 —— 这类调用由模组每次启动重新注册。
 *
 * @author THEREDK
 */
public final class HungerBonusStorage {

    private HungerBonusStorage() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        DimensionDataStorage storage = server.overworld().getDataStorage();
        // 1.20.1 签名: computeIfAbsent(Function<CompoundTag,T> load, Supplier<T> create, String name)
        HungerBonusData data = storage.computeIfAbsent(
                HungerBonusData::load, HungerBonusData::new, HungerBonusData.DATA_NAME);
        HungerConfig.bindPersisted(data);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        HungerBonusData data = HungerConfig.persistedOrNull();
        if (data != null) {
            data.setDirty();   // 确保最后一次修改一定落盘
        }
        HungerConfig.clearPersisted();
    }
}
