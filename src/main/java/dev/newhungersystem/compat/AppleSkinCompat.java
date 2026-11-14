package dev.newhungersystem.compat;

import com.mojang.logging.LogUtils;
import dev.newhungersystem.api.ClientConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import org.slf4j.Logger;

/**
 * AppleSkin 兼容：直接取消它的 HUD overlay 事件，避免与本模组的消化条/数值并存错位。
 * <p>
 * AppleSkin 在 {@code HUDOverlayHandler} 中 post 四个可取消事件后才检查 {@code isCanceled()}：
 * {@code HUDOverlayEvent$Saturation}（饱和度底图 + popup）、{@code $HungerRestored}（饥饿 popup）、
 * {@code $Exhaustion}（消耗值底栏）、{@code $HealthRestored}（血条回血预览）。
 * 我们按 {@link ClientConfig} 的分项开关决定取消哪些。
 * <p>
 * <b>只取消真正重叠的</b>：本模组的消化条画在饥饿条<b>上一行</b>，与 AppleSkin 的饱和度底图、
 * 饥饿 popup 都不重叠，所以那两项默认<b>不</b>取消（取消等于白白让玩家少一块信息）；
 * 唯一同位置的是消耗值底栏，它才按"自家底栏是否开启"联动让位。
 * <p>
 * <b>无硬依赖</b>：用 {@code Class.forName} + {@code IEventBus.addListener(priority, receiveCancelled,
 * Class<T>, Consumer<T>)} 按类名注册到四个事件的共同父类 {@code HUDOverlayEvent}，
 * 编译期完全不引用 AppleSkin 的类；未装 AppleSkin 时根本不注册（零开销）。
 *
 * @author THEREDK
 */
@OnlyIn(Dist.CLIENT)
public final class AppleSkinCompat {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String HUD_OVERLAY_EVENT = "squeek.appleskin.api.event.HUDOverlayEvent";

    private AppleSkinCompat() {
    }

    /**
     * 在 FMLClientSetupEvent 中调用；只要装了 AppleSkin 就注册监听器。
     * <p>
     * <b>不要</b>在这里判断 {@code suppressAppleSkinOverlays()} —— 否则玩家在游戏内打开开关后，
     * 监听器根本没注册过，改了配置也不生效。
     */
    public static void register() {
        if (!ModCompat.appleSkin()) {
            return;
        }
        try {
            Class<?> raw = Class.forName(HUD_OVERLAY_EVENT);
            if (!Event.class.isAssignableFrom(raw)) {
                LOGGER.warn("[New Hunger System] {} is not a Forge Event, skipping AppleSkin compat", HUD_OVERLAY_EVENT);
                return;
            }
            addListener(raw.asSubclass(Event.class));
            LOGGER.info("[New Hunger System] AppleSkin detected: HUD overlay events will be suppressed per config");
        } catch (ClassNotFoundException e) {
            LOGGER.warn("[New Hunger System] AppleSkin loaded but {} not found; overlays not suppressed",
                    HUD_OVERLAY_EVENT);
        }
    }

    /** 注册在四个事件的共同父类上，EventBus 会把父类监听器分发给所有子类事件 */
    private static <T extends Event> void addListener(Class<T> type) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, type, AppleSkinCompat::onOverlay);
    }

    private static void onOverlay(Event event) {
        if (!ClientConfig.suppressAppleSkinOverlays() || !ModCompat.appleSkin()) {
            return;
        }
        boolean cancel = switch (event.getClass().getSimpleName()) {
            case "HungerRestored" -> ClientConfig.suppressAppleSkinHungerOverlay();
            case "Saturation" -> ClientConfig.suppressAppleSkinSaturationOverlay();
            // 消耗值底栏两边画在同一条上: 只有本模组自己的底栏开着时才需要让位,
            // 否则关了我们的也关了它的 = 底栏彻底消失
            case "Exhaustion" -> ClientConfig.suppressAppleSkinExhaustionOverlay()
                    && ClientConfig.showExhaustionUnderlay();
            case "HealthRestored" -> ClientConfig.suppressAppleSkinHealthOverlay();
            default -> false;
        };
        if (cancel && event.isCancelable()) {
            event.setCanceled(true);
        }
    }
}
