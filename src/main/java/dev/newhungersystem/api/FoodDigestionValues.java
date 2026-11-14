package dev.newhungersystem.api;

import dev.newhungersystem.NewHungerSystem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 食物 -> 消化值的覆盖层（替代自定义 datapack JSON，沿用 Forge 生态的「配置 + 代码登记」路线）。
 * <p>
 * 取值优先级（高 -> 低）：
 * <ol>
 *   <li><b>配置层</b> {@code [food] digestionOverrides}（{@code "modid:item=value"}，绝对消化值）；</li>
 *   <li><b>代码登记层</b> {@link DigestionHelper#registerFoodValue}（绝对消化值）；</li>
 *   <li><b>内置公式</b> {@code nutrition × digestionPerNutrition}
 *       （消化饱和度再乘 {@code saturationModifier × 2}，与原版 {@code FoodData.eat()} 一致）。</li>
 * </ol>
 * 三层都算完之后仍会广播 {@link FoodDigestionEvent}，所以上游的值对模组监听器永远可见、可改。
 * <p>
 * 本类同时挂在 MOD 事件总线上处理 {@link ModConfigEvent}: 配置加载/重载时重建映射，
 * 重载时把在线玩家的消化值/缓存收敛到新上限并重新同步（否则把上限调低后客户端会显示超额数值）。
 *
 * @author THEREDK
 */
public final class FoodDigestionValues {

    private static final Logger LOGGER = LoggerFactory.getLogger(NewHungerSystem.MOD_ID);

    /** 解析失败时最多逐条打印多少条，其余聚合成一条，避免刷屏 */
    private static final int MAX_LOGGED_ENTRIES = 8;

    /** 配置层: 物品 id -> 绝对消化值 */
    private static final Map<ResourceLocation, Float> CONFIGURED = new ConcurrentHashMap<>();
    /** 代码登记层: 物品 -> 绝对消化值 */
    private static final Map<Item, Float> REGISTERED = new ConcurrentHashMap<>();

    private static volatile boolean built;

    private FoodDigestionValues() {
    }

    /**
     * 代码登记层的入口（模组用）。
     * 配置层的同名条目优先级更高，因此整合包总能覆盖模组写死的值。
     *
     * @return 是否成功登记（值为负或物品为 null 时拒绝）
     */
    public static boolean register(Item item, float digestion) {
        if (item == null || digestion < 0.0F) {
            return false;
        }
        REGISTERED.put(item, digestion);
        return true;
    }

    /** 是否已有代码登记值（不看配置层） */
    public static boolean hasRegistered(Item item) {
        return item != null && REGISTERED.containsKey(item);
    }

    /** 当前生效的配置层映射（只读快照，供命令/调试使用） */
    public static Map<ResourceLocation, Float> configured() {
        if (!built) {
            rebuild();
        }
        return Map.copyOf(CONFIGURED);
    }

    /**
     * 解析某物品的绝对消化值。
     *
     * @return 配置层或代码层给出的绝对消化值；都没有时返回 {@code null}，表示走内置公式
     */
    public static Float resolve(ItemStack stack) {
        if (!built) {
            rebuild();
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id != null) {
            Float configuredValue = CONFIGURED.get(id);
            if (configuredValue != null) {
                return configuredValue;
            }
        }
        return REGISTERED.get(stack.getItem());
    }

    /** 重新解析配置层列表；格式错逐条 warn（前 N 条）+ 聚合一条 */
    public static void rebuild() {
        List<? extends String> raw = HungerConfig.digestionOverrides();
        Map<ResourceLocation, Float> parsed = new LinkedHashMap<>();
        List<String> bad = new ArrayList<>();

        for (String entry : raw) {
            if (entry == null) {
                continue;
            }
            int split = entry.lastIndexOf('=');
            if (split <= 0 || split == entry.length() - 1) {
                bad.add(entry);
                continue;
            }
            String idPart = entry.substring(0, split).trim();
            String valuePart = entry.substring(split + 1).trim();
            ResourceLocation id = ResourceLocation.tryParse(idPart);
            if (id == null) {
                bad.add(entry);
                continue;
            }
            float value;
            try {
                value = Float.parseFloat(valuePart);
            } catch (NumberFormatException e) {
                bad.add(entry);
                continue;
            }
            if (value < 0.0F || !Float.isFinite(value)) {
                bad.add(entry);
                continue;
            }
            parsed.put(id, value);
        }

        CONFIGURED.clear();
        CONFIGURED.putAll(parsed);
        built = true;

        if (!bad.isEmpty()) {
            for (int i = 0; i < Math.min(MAX_LOGGED_ENTRIES, bad.size()); i++) {
                LOGGER.warn("Ignored malformed digestionOverrides entry '{}' (expected \"modid:item=value\")", bad.get(i));
            }
            if (bad.size() > MAX_LOGGED_ENTRIES) {
                LOGGER.warn("...and {} more malformed digestionOverrides entries ({} total)",
                        bad.size() - MAX_LOGGED_ENTRIES, bad.size());
            }
        }
        LOGGER.debug("Loaded {} digestion overrides (config layer)", CONFIGURED.size());
    }

    /**
     * MOD 总线: 配置加载/重载。加载时重建映射；重载时额外把在线玩家收敛到新上限。
     */
    @SubscribeEvent
    public static void onConfigChanged(ModConfigEvent event) {
        if (event.getConfig().getSpec() != HungerConfig.SPEC) {
            return;
        }
        rebuild();
        if (!(event instanceof ModConfigEvent.Reloading)) {
            return;   // Loading 阶段还没有玩家
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            clampToLimits(player);
        }
    }

    /** 把玩家数据收敛到当前上限（配置重载后上限可能被调低） */
    private static void clampToLimits(ServerPlayer player) {
        Digestion data = DigestionHelper.get(player);
        if (data == null) {
            return;
        }
        // 这里必须用服务端权威上限 (serverMax*), 不能用 max*():
        // 单人游戏里 max*() 会优先取客户端同步缓存, 而配置刚重载、新上限还没同步出去,
        // 用缓存会拿到旧上限导致"收敛"落空。
        float maxDigestion = (float) HungerConfig.serverMaxDigestion();
        if (data.getDigestion() > maxDigestion) {
            data.setDigestion(maxDigestion);
        }
        float maxSaturation = Math.min((float) HungerConfig.serverMaxDigestionSaturation(), data.getDigestion());
        if (data.getDigestionSaturation() > maxSaturation) {
            data.setDigestionSaturation(maxSaturation);
        }
        float cacheCap = HungerConfig.overflowCacheCapacity();
        if (data.getOverflowDigestionCache() > cacheCap) {
            data.setOverflowDigestionCache(cacheCap);
        }
        if (data.getOverflowSaturationCache() > cacheCap) {
            data.setOverflowSaturationCache(cacheCap);
        }
        DigestionEvents.refreshStats(player);
        ModNetwork.syncTo(player);
    }
}
