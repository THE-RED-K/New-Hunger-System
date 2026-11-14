package dev.newhungersystem.compat;

import dev.newhungersystem.api.ClientConfig;
import net.minecraftforge.fml.ModList;

/**
 * 相关模组的自动检测（整合包联动的单一入口）。
 * <p>
 * 所有联动都以 {@link ClientConfig#autoDetectMods()} 为总闸；检测只依赖 modId 字符串，
 * 不产生硬依赖，也不在编译期引用其它模组的类。
 *
 * @author THEREDK
 */
public final class ModCompat {

    public static final String APPLESKIN = "appleskin";
    public static final String ARMORPOINTSPP = "armorpointspp";
    public static final String NEW_ARMOR_SYSTEM = "new_armor_system";
    public static final String ALWAYS_EAT = "salwayseat";

    private ModCompat() {
    }

    /** modId 是否已加载（早期调用时 ModList 可能尚未就绪） */
    public static boolean isLoaded(String modId) {
        ModList list = ModList.get();
        return list != null && list.isLoaded(modId);
    }

    public static boolean autoDetect() {
        return ClientConfig.autoDetectMods();
    }

    public static boolean appleSkin() {
        return autoDetect() && isLoaded(APPLESKIN);
    }

    public static boolean armorPointsPP() {
        return autoDetect() && isLoaded(ARMORPOINTSPP);
    }

    public static boolean newArmorSystem() {
        return autoDetect() && isLoaded(NEW_ARMOR_SYSTEM);
    }

    public static boolean alwaysEat() {
        return autoDetect() && isLoaded(ALWAYS_EAT);
    }

    /** 联动开关: 装了指定 HUD 模组时隐藏本模组的数值显示 */
    public static boolean hideNumbers() {
        if (!autoDetect()) {
            return false;
        }
        return (ClientConfig.hideNumbersWithAppleSkin() && isLoaded(APPLESKIN))
                || (ClientConfig.hideNumbersWithArmorPointsPP() && isLoaded(ARMORPOINTSPP));
    }

    /** F3 调试行: 联动状态一目了然 */
    public static String statusLine() {
        return "compat: appleskin=" + isLoaded(APPLESKIN)
                + ", ap++=" + isLoaded(ARMORPOINTSPP)
                + ", new_armor_system=" + isLoaded(NEW_ARMOR_SYSTEM)
                + ", alwayseat=" + isLoaded(ALWAYS_EAT)
                + ", autoDetect=" + autoDetect()
                + ", suppressAppleSkin=" + (autoDetect() && ClientConfig.suppressAppleSkinOverlays()
                        && isLoaded(APPLESKIN));
    }
}
