package dev.newhungersystem.api;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * 客户端 HUD 配置 (选项名/默认值仿 AppleSkin 的客户端配置)。
 * 与服务端 {@link HungerConfig} 分开, 因为这些都是纯显示开关, 属于 CLIENT 类型配置。
 */
public final class ClientConfig {

    public static final ForgeConfigSpec SPEC;

    /** 消化饱和度叠加显示 (消化条同行) */
    public static final ForgeConfigSpec.BooleanValue SHOW_SATURATION_OVERLAY;
    /** 手持食物回复预显 (红/黄闪烁 popup) */
    public static final ForgeConfigSpec.BooleanValue SHOW_FOOD_VALUES_OVERLAY;
    /** 副手食物也参与预显 */
    public static final ForgeConfigSpec.BooleanValue SHOW_FOOD_VALUES_OVERLAY_WHEN_OFFHAND;
    /** 血条上显示进食 (消化链) 后预计可回复的血量 */
    public static final ForgeConfigSpec.BooleanValue SHOW_FOOD_HEALTH_HUD_OVERLAY;
    /** 物品提示栏: 按住 SHIFT 时显示食物消化值/消化饱和度条 (仿 AppleSkin) */
    public static final ForgeConfigSpec.BooleanValue SHOW_FOOD_VALUES_IN_TOOLTIP;
    /** 物品提示栏: 无需按键始终显示 */
    public static final ForgeConfigSpec.BooleanValue SHOW_FOOD_VALUES_IN_TOOLTIP_ALWAYS;
    /** 消耗值底栏 (81px, 画在饥饿条之下) */
    public static final ForgeConfigSpec.BooleanValue SHOW_FOOD_EXHAUSTION_UNDERLAY;
    /** F3 调试屏附加消化值行 */
    public static final ForgeConfigSpec.BooleanValue SHOW_FOOD_DEBUG_INFO;
    /** 图标跟随原版抖动动画 */
    public static final ForgeConfigSpec.BooleanValue SHOW_VANILLA_ANIMATION_OVERLAY;
    /** AP++ 式数值显示 ("20+10") */
    public static final ForgeConfigSpec.BooleanValue SHOW_NUMBERS_OVERLAY;
    /** 闪烁最亮时的 alpha (1.0=全不透明) */
    public static final ForgeConfigSpec.DoubleValue MAX_HUD_OVERLAY_FLASH_ALPHA;

    // ---------------- 模组联动 (compatibility) ----------------

    /** 自动检测整合包里的相关模组 (关闭则以下联动全部不生效) */
    public static final ForgeConfigSpec.BooleanValue AUTO_DETECT_MODS;
    /** 总开关: 取消 AppleSkin 的 HUD overlay 事件, 避免与本模组并存/错位 */
    public static final ForgeConfigSpec.BooleanValue SUPPRESS_APPLESKIN_OVERLAYS;
    /** AppleSkin 的饥饿值 popup (HUDOverlayEvent.HungerRestored) */
    public static final ForgeConfigSpec.BooleanValue SUPPRESS_APPLESKIN_HUNGER;
    /** AppleSkin 的饱和度底图 + popup (HUDOverlayEvent.Saturation) */
    public static final ForgeConfigSpec.BooleanValue SUPPRESS_APPLESKIN_SATURATION;
    /** AppleSkin 的消耗值底栏 (本模组已有同款) */
    public static final ForgeConfigSpec.BooleanValue SUPPRESS_APPLESKIN_EXHAUSTION;
    /** AppleSkin 的回血量预览 (在血条上, 本模组已用自己的消化链估算替代, 默认取消) */
    public static final ForgeConfigSpec.BooleanValue SUPPRESS_APPLESKIN_HEALTH;
    /** 装了 AppleSkin 时隐藏本模组的数值显示 */
    public static final ForgeConfigSpec.BooleanValue HIDE_NUMBERS_WITH_APPLESKIN;
    /** 装了 Armor Points ++ 时隐藏本模组的数值显示 */
    public static final ForgeConfigSpec.BooleanValue HIDE_NUMBERS_WITH_ARMORPOINTSPP;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("hud");
        SHOW_SATURATION_OVERLAY = builder
                .comment("If true, shows your current digestion saturation overlayed on the digestion bar")
                .define("showSaturationHudOverlay", true);
        SHOW_FOOD_VALUES_OVERLAY = builder
                .comment("If true, shows the digestion (and digestion saturation) that would be restored by food you are holding")
                .define("showFoodValuesHudOverlay", true);
        SHOW_FOOD_VALUES_OVERLAY_WHEN_OFFHAND = builder
                .comment("If true, enables the digestion overlays for food in your off-hand")
                .define("showFoodValuesHudOverlayWhenOffhand", true);
        SHOW_FOOD_HEALTH_HUD_OVERLAY = builder
                .comment("If true, shows an estimate on your health bar of how much health you will regain",
                        "after eating the food in hand (digestion -> hunger -> health chain estimate)")
                .define("showFoodHealthHudOverlay", true);
        SHOW_FOOD_VALUES_IN_TOOLTIP = builder
                .comment("If true, shows the digestion and digestion saturation restored by the food",
                        "in its tooltip while holding SHIFT")
                .define("showFoodValuesInTooltip", true);
        SHOW_FOOD_VALUES_IN_TOOLTIP_ALWAYS = builder
                .comment("If true, the food digest values tooltip is always shown, without requiring SHIFT")
                .define("showFoodValuesInTooltipAlways", true);
        SHOW_FOOD_EXHAUSTION_UNDERLAY = builder
                .comment("If true, shows your food exhaustion as a progress bar behind the hunger bars")
                .define("showFoodExhaustionHudUnderlay", true);
        SHOW_FOOD_DEBUG_INFO = builder
                .comment("If true, adds a line that shows your digestion, digestion saturation and exhaustion in the F3 debug overlay")
                .define("showFoodStatsInDebugOverlay", true);
        SHOW_VANILLA_ANIMATION_OVERLAY = builder
                .comment("If true, digestion overlay icons will shake to match Minecraft's icon animations")
                .define("showVanillaAnimationsOverlay", true);
        SHOW_NUMBERS_OVERLAY = builder
                .comment("If true, shows numeric readouts (\"value+saturation\", Armor Points ++ style) to the right of the bars")
                .define("showNumbersHudOverlay", true);
        MAX_HUD_OVERLAY_FLASH_ALPHA = builder
                .comment("Alpha value of the flashing icons at their most visible point (1.0 = fully opaque, 0.0 = fully transparent)")
                .defineInRange("maxHudOverlayFlashAlpha", 0.65D, 0.0D, 1.0D);
        builder.pop();

        builder.push("compatibility");
        AUTO_DETECT_MODS = builder
                .comment("Auto-detect other installed mods and enable compatibility behaviour.",
                        "Disable to turn off every compatibility switch below.")
                .define("autoDetectMods", true);
        SUPPRESS_APPLESKIN_OVERLAYS = builder
                .comment("Master switch for the AppleSkin overlay suppression below.",
                        "Only the overlays whose individual switch is ON are cancelled, so leaving the master on",
                        "no longer removes everything AppleSkin draws. Requires AppleSkin; no hard dependency.")
                .define("suppressAppleSkinOverlays", true);
        SUPPRESS_APPLESKIN_HUNGER = builder
                .comment("Cancel HUDOverlayEvent.HungerRestored (AppleSkin's hunger restore popup).",
                        "Default false - it is drawn on the hunger row and does not overlap our digestion row.",
                        "Note: the previewed amount assumes vanilla eating, while this mod routes food into",
                        "digestion first, so the hunger arrives later than the popup suggests.")
                .define("suppressAppleSkinHungerOverlay", false);
        SUPPRESS_APPLESKIN_SATURATION = builder
                .comment("Cancel HUDOverlayEvent.Saturation (AppleSkin's saturation overlay / popup).",
                        "Default false - our digestion bar lives one row ABOVE the hunger bar, so there is no",
                        "overlap, and vanilla saturation is still meaningful here (digestion saturation converts",
                        "into it and natural regeneration depends on it).",
                        "Only enable this if another HUD mod already duplicates the hunger row.")
                .define("suppressAppleSkinSaturationOverlay", false);
        SUPPRESS_APPLESKIN_EXHAUSTION = builder
                .comment("Cancel HUDOverlayEvent.Exhaustion (AppleSkin's exhaustion underlay).",
                        "Default true, but it only takes effect while this mod's own underlay is enabled",
                        "(showFoodExhaustionHudUnderlay) - the two draw into the exact same bar.")
                .define("suppressAppleSkinExhaustionOverlay", true);
        SUPPRESS_APPLESKIN_HEALTH = builder
                .comment("Cancel HUDOverlayEvent.HealthRestored (AppleSkin's health preview on the health bar).",
                        "Default true - its estimate reads vanilla hunger, but this mod routes eating through",
                        "digestion, so the vanilla-based preview shows health gain that will never happen.",
                        "This mod provides its own chain estimate (digestion -> hunger -> health) instead.")
                .define("suppressAppleSkinHealthOverlay", true);
        HIDE_NUMBERS_WITH_APPLESKIN = builder
                .comment("If true, hide this mod's numeric readouts when AppleSkin is installed.")
                .define("hideNumbersWithAppleSkin", false);
        HIDE_NUMBERS_WITH_ARMORPOINTSPP = builder
                .comment("If true, hide this mod's numeric readouts when Armor Points ++ is installed.")
                .define("hideNumbersWithArmorPointsPP", false);
        builder.pop();

        SPEC = builder.build();
    }

    private ClientConfig() {
    }

    public static boolean showSaturationOverlay() {
        return SHOW_SATURATION_OVERLAY.get();
    }

    public static boolean showFoodValuesOverlay() {
        return SHOW_FOOD_VALUES_OVERLAY.get();
    }

    public static boolean offhand() {
        return SHOW_FOOD_VALUES_OVERLAY_WHEN_OFFHAND.get();
    }

    public static boolean showFoodHealthHudOverlay() {
        return SHOW_FOOD_HEALTH_HUD_OVERLAY.get();
    }

    public static boolean showFoodValuesInTooltip() {
        return SHOW_FOOD_VALUES_IN_TOOLTIP.get();
    }

    public static boolean showFoodValuesInTooltipAlways() {
        return SHOW_FOOD_VALUES_IN_TOOLTIP_ALWAYS.get();
    }

    public static boolean showExhaustionUnderlay() {
        return SHOW_FOOD_EXHAUSTION_UNDERLAY.get();
    }

    public static boolean showFoodDebugInfo() {
        return SHOW_FOOD_DEBUG_INFO.get();
    }

    public static boolean vanillaAnimation() {
        return SHOW_VANILLA_ANIMATION_OVERLAY.get();
    }

    public static boolean showNumbers() {
        return SHOW_NUMBERS_OVERLAY.get();
    }

    public static float maxFlashAlpha() {
        return MAX_HUD_OVERLAY_FLASH_ALPHA.get().floatValue();
    }

    public static boolean autoDetectMods() {
        return AUTO_DETECT_MODS.get();
    }

    public static boolean suppressAppleSkinOverlays() {
        return SUPPRESS_APPLESKIN_OVERLAYS.get();
    }

    public static boolean suppressAppleSkinHungerOverlay() {
        return SUPPRESS_APPLESKIN_HUNGER.get();
    }

    public static boolean suppressAppleSkinSaturationOverlay() {
        return SUPPRESS_APPLESKIN_SATURATION.get();
    }

    public static boolean suppressAppleSkinExhaustionOverlay() {
        return SUPPRESS_APPLESKIN_EXHAUSTION.get();
    }

    public static boolean suppressAppleSkinHealthOverlay() {
        return SUPPRESS_APPLESKIN_HEALTH.get();
    }

    public static boolean hideNumbersWithAppleSkin() {
        return HIDE_NUMBERS_WITH_APPLESKIN.get();
    }

    public static boolean hideNumbersWithArmorPointsPP() {
        return HIDE_NUMBERS_WITH_ARMORPOINTSPP.get();
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SPEC);
    }
}