package dev.newhungersystem.api;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;

import java.util.Random;
import java.util.Vector;

/**
 * 回血预显的链式估算 (消化值 → 饥饿值 → 血量)。
 * <p>
 * 不做 AppleSkin {@code FoodHelper.getEstimatedHealthIncrement} 的原版常量循环:
 * 它按"饥饿≥18 才回血、每 6 消耗回 1 血 (原版 healCost)"估算, 而本模组把进食的
 * 原版饥饿收益还原进了消化链 (DigestionEvents.java:@Overwrite), 读原版 hunger 的
 * 估算会显示"不会发生的回血"。本估算改用本模组自己的回血曲线
 * ({@link FoodDataMixin} + {@link StatFactors#foodRegenPercent}):
 * <ol>
 *   <li>食物增益 digest/satGain 连续化等量折算到{@code 最终饥饿值 / 原版饱和度}
 *       (只算"这口食物"的收益, 不带当前消化存量; 转化速率相关的一切被忽略);</li>
 *   <li>按快回(烧饱和度)/慢回(高饥饿值)曲线与消耗值衰减的"燃料"账本离散模拟,
 *       直到燃料耗尽;</li>
 *   <li>食物自带的 Regeneration 效果按 AppleSkin 同款 {@code duration / (50 >> amplifier)}
 *       折算成额外血量, 与链式结果相加后再按可回血空间 clamp。</li>
 * </ol>
 * 由于转化无论快慢最终回复等量血量, 此链式估算只依赖上限约束与燃料换算, 不依赖速率。
 * REDIRECT_MOB_EFFECTS 只重定向 Saturation / Hunger, 不影响 Regeneration 折算。
 * <p>
 * 坐标与纹理完全对齐原版 {@code Gui.renderHearts}: 心形间距 8、大小 9,
 * u = 16 + (index*2+i)*9, v = 9 * (hardcore ? 5 : 0), 行高 max(10-(rows-2),3),
 * 顶行锚点 screenHeight - 39 (AppleSkin 亦同, 见 dessen generateHealthBarOffsets)。
 */
public final class FoodHealthHelper {

    /** 原版 HUD 图标图集 (icons.png), 供回血预显的满/半/空心底绘制 */
    public static final ResourceLocation MC_ICONS = TextureHelper.MC_ICONS;

    private static final int ICON = 9;          // 原版心形图标绘制尺寸
    private static final int ICON_OFFSET = 16;  // 心形在 icons.png 内的列起点
    private static final int STEP = 8;          // 图标间距 = 原版心形间距
    /** 满心: 16+(4+0)*9 = 52 (AppleSkin 同款 u) */
    private static final int U_HEART_FULL = 16 + 4 * ICON;
    /**
     * AppleSkin 的"半心"取 16+(4+1)*9 = 61（原版闪烁满心），不是原版真正半心 70。
     * 为与原模组绘制完全一致，这里沿用 AppleSkin 的取法。
     */
    private static final int U_HEART_HALF = 16 + 5 * ICON;
    /** 底 (CONTAINER 半格): 16+9 = 25, AppleSkin 的恒定底色 */
    private static final int U_HEART_CONTAINER = 16 + ICON;
    /** 消化过载跟随上线: 重复模拟 (录取上限), 防止配置化成死循环时吞 CPU */
    private static final int MAX_SIM_STEPS = 100_000;
    /** 心形抖动随机源 (AppleSkin generateHealthBarOffsets 同款) */
    private static final Random random = new Random();
    /** 每帧预生成的逐格心形偏移 (供外部 HUD 对齐叠加层；AppleSkin healthBarOffsets 同款) */
    public static final Vector<IntPoint> healthBarOffsets = new Vector<>();

    /**
     * 回血预估缓存 —— HUD 每个渲染帧都会请求同一次估算。
     * 以「同一客户端 tick + 同一物品 + 同一状态」为 key 复用，把每帧的
     * {@code FoodValuesEvent} 广播与 {@code simulateRegen} 降到每 tick 最多一次。
     */
    private static boolean estimateValid;
    private static Player estimatePlayer;
    private static int estimateTick;
    private static ItemStack estimateStack = ItemStack.EMPTY;
    private static int estimateFoodLevel;
    private static float estimateSaturation;
    private static float estimateHealth;
    private static float estimateMaxHealth;
    private static float estimateAbsorption;
    private static float estimateDigestion;
    private static float estimateDigestionSat;
    private static FoodValuesEvent estimateValues;
    private static float estimateRestored;

    /** 闪烁状态 (AppleSkin / DigestionOverlay 同款节拍: 每 tick ±0.125, [-0.5, 1.5] 往返) */
    private static float unclampedFlashAlpha = 0.0F;
    private static float flashAlpha = 0.0F;
    private static byte alphaDir = 1;

    private FoodHealthHelper() {
    }

    // ---------------------------------------------------------------- 估算

    /**
     * 链式估算: 吃下 {@code stack} 后 (消化层容量 clamp) 最终能回复多少血量。
     *
     * @return 距离满血还可回的血量上限 (不含金苹果等吸收心, 不含药水); 不满足回血前提时返回 0
     */
    public static float estimateHealthRestored(ItemStack stack, Player player) {
        return estimateHealthRestored(stack, player, FoodHelper.postFoodValuesEvent(player, stack));
    }

    /**
     * HUD 专用：带缓存的 {@link #estimateHealthRestored(ItemStack, Player)}。
     * <p>
     * 同一客户端 tick 内（多帧渲染）、同一物品、同一玩家状态（饥饿/饱和/血量/吸收/消化）时
     * 直接返回上次结果，不再广播 {@link FoodValuesEvent}、不再跑 {@code simulateRegen}。
     * 任何一项变化都会立即重算，所以 HUD 数值不受影响。
     */
    public static float estimateHealthRestoredCached(ItemStack stack, Player player) {
        if (!estimateCacheMatches(stack, player)) {
            recomputeEstimate(stack, player);
        }
        return estimateRestored;
    }

    /** 最近一次 {@link #estimateHealthRestoredCached} 使用的事件（非食物/缓存为空时为 null） */
    public static FoodValuesEvent cachedValuesEvent() {
        return estimateValues;
    }

    private static boolean estimateCacheMatches(ItemStack stack, Player player) {
        if (!estimateValid || player == null || estimatePlayer != player
                || estimateTick != player.tickCount
                || !ItemStack.isSameItemSameTags(estimateStack, stack)) {
            return false;
        }
        return estimateFoodLevel == player.getFoodData().getFoodLevel()
                && estimateSaturation == player.getFoodData().getSaturationLevel()
                && estimateHealth == player.getHealth()
                && estimateMaxHealth == player.getMaxHealth()
                && estimateAbsorption == player.getAbsorptionAmount()
                && estimateDigestion == ClientDigestionData.digestion
                && estimateDigestionSat == ClientDigestionData.digestionSaturation;
    }

    private static void recomputeEstimate(ItemStack stack, Player player) {
        estimateValid = true;
        estimatePlayer = player;
        estimateTick = player.tickCount;
        estimateStack = stack;
        estimateValues = FoodHelper.postFoodValuesEvent(player, stack);
        estimateRestored = estimateValues == null ? 0.0F
                : estimateHealthRestored(stack, player, estimateValues);
        estimateFoodLevel = player.getFoodData().getFoodLevel();
        estimateSaturation = player.getFoodData().getSaturationLevel();
        estimateHealth = player.getHealth();
        estimateMaxHealth = player.getMaxHealth();
        estimateAbsorption = player.getAbsorptionAmount();
        estimateDigestion = ClientDigestionData.digestion;
        estimateDigestionSat = ClientDigestionData.digestionSaturation;
    }

    /**
     * 与 {@link #estimateHealthRestored(ItemStack, Player)} 相同，但复用调用方已广播过的
     * {@link FoodValuesEvent}，避免 HUD 每帧为同一物品重复 post 两次。
     */
    public static float estimateHealthRestored(ItemStack stack, Player player, FoodValuesEvent valuesEvent) {
        if (player == null || stack.isEmpty()) {
            return 0.0F;
        }
        float maxHealth = player.getMaxHealth();
        float health = player.getHealth();
        // 满血 / 已死: 没有任何回血空间 (FoodDataMixin 的 isHurt 前提)
        if (health <= 0.0F || health >= maxHealth) {
            return 0.0F;
        }

        // ---- 阶段一: 食物增益的消化层容量 clamp (只算这口食物的收益) ----
        float digestion = ClientDigestionData.digestion;
        float digestionSat = ClientDigestionData.digestionSaturation;
        // valuesEvent 由调用方广播 (或旧入口在本方法开头广播)，三处显示入口共用改写入口
        if (valuesEvent == null) {
            return 0.0F;
        }
        float gain = Math.max(0.0F, valuesEvent.modifiedFoodValues.digestion);
        float satGain = Math.max(0.0F, valuesEvent.modifiedFoodValues.digestionSaturation);

        float maxDig = Digestion.maxDigestion();
        float digAfter = Math.min(digestion + gain, maxDig);
        // 呕吐检测 (DigestionEvents.onPlayerTick): 未启用溢出缓存时，达到阈值会在下一次
        // 0.5s tick 把消化值和消化饱和度全部清空，这口食物不会带来任何回血 → 预显必须为 0。
        // 启用溢出缓存时呕吐被抑制，超出的部分排队回填，这里仍按容量内的保守值估算。
        float vomitThreshold = maxDig * HungerConfig.vomitThresholdPercent() / 100.0F;
        if (!HungerConfig.enableOverflowCache() && digAfter >= vomitThreshold - 1.0E-3F) {
            return 0.0F;
        }
        float effGain = digAfter - digestion;                       // 实际进入消化值的增量
        float satCap = Math.min(Digestion.maxDigestionSaturation(), digAfter);
        float dsAfter = Math.min(digestionSat + satGain, satCap);
        float effSatGain = dsAfter - digestionSat;                  // 实际进入消化饱和度的增量
        if (effGain <= 0.0F && effSatGain <= 0.0F) {
            return 0.0F;
        }

        // ---- 阶段二: 终态折算到原版饥饿值/饱和度 (等量 1:1, 忽略转化速率) ----
        float maxHunger = StatFactors.maxHunger();
        float hungerFinal = Math.min(maxHunger, player.getFoodData().getFoodLevel() + effGain);
        // 不变量: 饱和度 ≤ 饥饿值 (原版 FoodData); 消化饱和度先转、消化值后转, 终态结果同此式
        float satFinal = Math.min(player.getFoodData().getSaturationLevel() + effSatGain, hungerFinal);

        // ---- 阶段三: 按回血曲线离散模拟 (燃料账本) + 食物自带 Regeneration 折算 ----
        float chainHeal = simulateRegen(player, hungerFinal, satFinal);
        float regenHeal = estimateFoodRegeneration(stack, player);
        return Math.min(maxHealth - health, chainHeal + regenHeal);
    }

    /**
     * AppleSkin {@code FoodHelper.getEstimatedHealthIncrement} 同款: 食物自带的 Regeneration
     * 效果按 {@code duration / max(50 >> amplifier, 1)}（整数除法）折算为额外血量，取第一个
     * 匹配效果。REDIRECT_MOB_EFFECTS 只改 Saturation/Hunger，不干扰这里。
     */
    private static float estimateFoodRegeneration(ItemStack stack, Player player) {
        FoodProperties food = stack.getItem().getFoodProperties(stack, player);
        if (food == null) {
            return 0.0F;
        }
        for (Pair<MobEffectInstance, Float> pair : food.getEffects()) {
            MobEffectInstance effect = pair.getFirst();
            if (effect != null && effect.getEffect() == MobEffects.REGENERATION) {
                int ticksPerHeart = Math.max(50 >> effect.getAmplifier(), 1);
                return (float) (effect.getDuration() / ticksPerHeart);
            }
        }
        return 0.0F;
    }

    /**
     * 1:1 镜像 {@link FoodDataMixin#tick} 的回血分支（双轨独立燃料账本）:
     * 快轨每 fastRegenIntervalTicks 一次、<b>只烧饱和度</b>; 慢轨每 slowRegenIntervalTicks 一次、
     * <b>只烧饥饿值</b>; 两本账各自累计满 maxExhaustion 扣 1 点对应燃料;
     * 共同钳制: hunger ≥ maxHunger×ratio (默认 0.6), 低于门槛两轨都停;
     * 快回: sat &gt; 0 &amp;&amp; isHurt; 慢回: hunger ≥ 门槛 &amp;&amp; isHurt (不要求 sat ≤ 0, 两轨并行);
     * 单次回血 = foodRegenPercent(hunger)/100 (消化值不影响回血), 每次回血消耗 heal×healCost 消耗值。
     * <p>
     * 两条轨道燃料不同, 所以不能像旧实现那样"按事件总数简并"——快轨吃满饱和度、慢轨吃满饥饿值,
     * 必须按时序交替推演才能知道哪条轨先耗尽。间隔本身仍只影响"多久回一次", 不影响总消耗/总回复比。
     */
    private static float simulateRegen(Player player, float hunger, float sat) {
        float maxHealth = player.getMaxHealth();
        float health = player.getHealth();
        float recoverable = maxHealth - health;
        if (recoverable <= 0.0F) {
            return 0.0F;
        }
        if (!player.level().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION)) {
            return 0.0F;
        }
        float maxHunger = StatFactors.maxHunger();
        float threshold = maxHunger * HungerConfig.slowRegenHungerRatio();
        float healCost = HungerConfig.regenExhaustionPerHealth();
        float maxExhaustion = HungerConfig.maxExhaustion();
        int fastInterval = Math.max(1, HungerConfig.fastRegenIntervalTicks());
        int slowInterval = HungerConfig.slowRegenIntervalTicks();

        float fastLedger = 0.0F;   // 快轨账本: 只结算饱和度
        float slowLedger = 0.0F;   // 慢轨账本: 只结算饥饿值
        int fastWait = fastInterval;
        int slowWait = slowInterval;
        float recovered = 0.0F;
        int guard = 0;
        while (recovered < recoverable && guard++ < MAX_SIM_STEPS) {
            if (hunger < threshold) {
                break;               // 共同钳制: 饥饿值低于门槛 → 快/慢两轨都停, 即使还有饱和度
            }
            if (sat <= 0.0F) {
                fastWait = Integer.MAX_VALUE;   // 快轨燃料耗尽后不会再启动 (模拟中饱和度只减不增)
            }
            // 取下一个到点的事件 (快轨/慢轨按各自节拍交替)
            boolean fast = fastWait <= slowWait;
            if (fast) {
                slowWait -= fastWait;
                fastWait = fastInterval;
            } else {
                fastWait -= slowWait;
                slowWait = slowInterval;
            }
            // 单次回血量 (1:1 镜像 FoodDataMixin; 饥饿值取整层给 foodRegenPercent)
            float heal = StatFactors.foodRegenPercent((int) Math.floor(hunger)) / 100.0F;
            if (heal <= 0.0F) {
                break;               // 饥饿值太低的缩放惩罚到 0 → 回血停止
            }
            float actual = Math.min(heal, recoverable - recovered);
            if (actual <= 0.0F) {
                break;
            }
            recovered += actual;
            float cost = actual * healCost;
            if (fast) {
                fastLedger += cost;
                while (fastLedger > maxExhaustion) {
                    fastLedger -= maxExhaustion;
                    sat = Math.max(0.0F, sat - 1.0F);
                }
            } else {
                slowLedger += cost;
                while (slowLedger > maxExhaustion) {
                    slowLedger -= maxExhaustion;
                    hunger -= 1.0F;
                }
            }
        }
        return recovered;
    }

    // ---------------------------------------------------------------- 显示条件 / 动画

    /**
     * 是否显示本模组的回血预显。与 AppleSkin 不同，不按"饥饿值 ≥ 18"排除：
     * 本模组要求饥饿值达到 {@code slowRegenHungerRatio} 门槛（默认 60%）才会自然回血，
     * 且饱和度还有时快轨与慢轨并行，原版门槛不适用。
     * 和平模式有独立的被动回血，Regeneration/Wither/POISON 也会叠加或抵消血量变化，
     * 预览会误导，故与 AppleSkin shouldShowEstimatedHealth 一样跳过三种效果。
     */
    public static boolean canShowHealthEstimate(Player player) {
        return player != null
                && player.level().getDifficulty() != Difficulty.PEACEFUL
                && !player.hasEffect(MobEffects.REGENERATION)
                && !player.hasEffect(MobEffects.WITHER)
                && !player.hasEffect(MobEffects.POISON);
    }

    /** 手持食物: 走 {@link FoodHelper#getHeldFoodStack} 的 canEat 门槛与主手→副手回退 */
    public static ItemStack getHeldFoodStack(Player player) {
        return FoodHelper.getHeldFoodStack(player);
    }

    /** 每 client tick 推进闪烁 alpha (与 DigestionOverlay 同款 AppleSkin 节拍) */
    public static void clientTick() {
        unclampedFlashAlpha += alphaDir * 0.125F;
        if (unclampedFlashAlpha >= 1.5F) {
            alphaDir = -1;
        } else if (unclampedFlashAlpha <= -0.5F) {
            alphaDir = 1;
        }
        flashAlpha = Mth.clamp(unclampedFlashAlpha, 0.0F, 1.0F) * ClientConfig.maxFlashAlpha();
    }

    public static void resetFlash() {
        flashAlpha = 0.0F;
        unclampedFlashAlpha = 0.0F;
        alphaDir = 1;
    }

    public static float flashAlpha() {
        return flashAlpha;
    }

    // ---------------------------------------------------------------- 绘制

    /**
     * 反推心形首行的 Y。Forge 1.20.1 的 {@code ForgeGui.renderHealth} 用的是
     * {@code leftHeight}（不是 rightHeight）：先取 {@code top = height - leftHeight}，
     * 再按 {@code healthRows*rowHeight (+ 补差)} 累加。用渲染后的 leftHeight 减去这段增量，
     * 即可在任何行数/其它左侧叠加层下还原首行位置，等价于 AppleSkin 用 rightHeight 的写法。
     */
    public static int healthRowTop(int screenHeight, int leftHeightAfterRender, Player player) {
        float healthMax = Math.max(player.getMaxHealth(), (float) Mth.ceil(player.getHealth()));
        int absorb = Mth.ceil(player.getAbsorptionAmount());
        int healthRows = Mth.ceil((healthMax + absorb) / 2.0F / 10.0F);
        int rowHeight = Math.max(10 - (healthRows - 2), 3);
        int consumed = healthRows * rowHeight + (rowHeight != 10 ? 10 - rowHeight : 0);
        return screenHeight - (leftHeightAfterRender - consumed);
    }

    /**
     * 在血条上叠加"吃下后预计回复到"的心形，布局与 AppleSkin {@code drawHealthOverlay} 一致。
     *
     * @param left            血条行左缘 (= screenWidth/2 - 91)
     * @param top             血条首行顶部 (= screenHeight - 39, 多排心形向上堆叠)
     * @param modifiedHealth  当前血量 + 预估回复量 (调用方已 clamp 到 maxHealth)
     * @param guiTicks        当前 GUI tick，低血量抖动用 (AppleSkin 同款种子)
     */
    public static void renderHealthOverlay(GuiGraphics graphics, Player player, int left, int top,
                                           float modifiedHealth, float alpha, int guiTicks) {
        float currentHealth = player.getHealth();
        if (alpha <= 0.0F || modifiedHealth <= currentHealth) {
            return;
        }
        // AppleSkin generateHealthBarOffsets: 血量 <= 4 且开启动画时，心形预显跟随原版抖动
        boolean animate = ClientConfig.vanillaAnimation() && Mth.ceil(currentHealth) <= 4.0F;
        if (animate) {
            random.setSeed(guiTicks * 312871L);
        }
        int fixedModifiedHealth = Mth.ceil(modifiedHealth);
        boolean hardcore = player.level().getLevelData().isHardcore();
        int absorption = Mth.ceil(player.getAbsorptionAmount());
        int healthBars = Mth.ceil((player.getMaxHealth() + absorption) / 2.0F);
        if (healthBars <= 0 || healthBars > 1000) {
            return;
        }
        int healthRows = Mth.ceil(healthBars / 10.0F);
        int rowHeight = Math.max(10 - (healthRows - 2), 3);

        // 预生成全部心形偏移，外部 HUD 模组可读取 healthBarOffsets 对齐自己的叠加层
        if (healthBarOffsets.size() != healthBars) {
            healthBarOffsets.setSize(healthBars);
        }
        for (int i = 0; i < healthBars; i++) {
            IntPoint point = healthBarOffsets.get(i);
            if (point == null) {
                point = new IntPoint();
                healthBarOffsets.set(i, point);
            }
            int row = Mth.ceil((i + 1) / 10.0F) - 1;
            point.x = i % 10 * STEP;
            point.y = -row * rowHeight + (animate ? random.nextInt(2) : 0);
        }

        // AppleSkin 用 ceil(ceil(health)/2) 作为起点：半心所在的那一格也要被重绘
        int startBar = Math.max(0, Mth.ceil(Mth.ceil(currentHealth) / 2.0F));
        int endBar = Math.min(healthBars, Math.max(0, Mth.ceil(modifiedHealth / 2.0F)));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (int i = startBar; i < endBar; i++) {
            IntPoint offset = healthBarOffsets.get(i);
            int x = left + offset.x;
            int y = top + offset.y;
            int v = hardcore ? 5 * ICON : 0;
            int u = i * 2 + 1 == fixedModifiedHealth ? U_HEART_HALF : U_HEART_FULL;

            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha * 0.25F);
            graphics.blit(MC_ICONS, x, y, U_HEART_CONTAINER, v, ICON, ICON);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
            graphics.blit(MC_ICONS, x, y, u, v, ICON, ICON);
        }
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}