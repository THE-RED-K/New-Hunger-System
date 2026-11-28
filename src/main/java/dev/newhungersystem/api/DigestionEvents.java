package dev.newhungersystem.api;

import dev.newhungersystem.NewHungerSystem;
import dev.newhungersystem.api.StatFactors;
import dev.newhungersystem.api.Digestion;
import dev.newhungersystem.api.DigestionCapability;
import dev.newhungersystem.api.HungerConfig;
import dev.newhungersystem.api.ModNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 消化系统事件处理:
 * 1. 给玩家附加 Digestion Capability 并处理转生/登入/维度切换同步
 * 2. 进食拦截: 吃食物获得等量消化值与消化饱和度, 而不是直接加饥饿值/饱和度
 * 3. 每 0.5s 按 baseDigestionRate 把消化值/消化饱和度转化为饥饿值/饱和度;
 *    消化值进入惩罚区(> digestionPenaltyStartPercent)后主转化按比例加速, 满值 ×1.5
 * 4. 阈值效果: 饥饿值<30% 惩罚攻/速/挖掘; 消化过载惩罚攻/速/游泳/挖掘
 *    (硬上限: 攻击力 -30%, 速度类 -60%); 到达呕吐阈值时呕吐
 * 5. 回血: 由 FoodDataMixin 接管 (保留原版 gamerule), 有饱和度时按曲线回血,
 *         饥饿值间接缩放回血量, 消化过载额外减速; 药水/金苹果等走 LivingHealEvent, 不在此列
 * <p>
 * 对外 Forge 事件 (Lib 接口, 均在本类或 Digestion 数据层广播):
 * {@link FoodDigestionEvent} (进食数值可改/可取消)、
 * {@link DigestionConversionEvent} (转化量可改/可取消)、
 * {@link DigestionVomitEvent} (呕吐可取消)、
 * {@link DigestionOverflowEvent} (溢出, 见 Digestion)。
 */
public final class DigestionEvents {

    /** 进食期间记录的 FoodData 快照 {foodLevel, saturationLevel}, 用于还原原版直接加的数值 */
    private static final Map<UUID, float[]> EAT_SNAPSHOTS = new HashMap<>();

    /** 各属性修饰器 UUID (MULTIPLY_TOTAL, value=factor-1) */
    private static final UUID UUID_ATTACK = UUID.fromString("c0a1b2c3-0001-4d00-8000-0000000000a1");
    private static final UUID UUID_ATK_SPD = UUID.fromString("c0a1b2c3-0002-4d00-8000-0000000000a2");
    private static final UUID UUID_MOVE = UUID.fromString("c0a1b2c3-0003-4d00-8000-0000000000a3");
    private static final UUID UUID_SWIM = UUID.fromString("c0a1b2c3-0004-4d00-8000-0000000000a4");

    private DigestionEvents() {
    }

    // ---------------------------------------------------------------- capability

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player player) {
            DigestionCapability.Provider provider = new DigestionCapability.Provider(player);
            event.addCapability(ResourceLocation.fromNamespaceAndPath(NewHungerSystem.MOD_ID, "digestion"),
                    provider);
            // 注册失效回调: 死亡/跨维度克隆调用 invalidateCaps() 时会执行,
            // 让第三方持有的 LazyOptional 立即失效, 而不是继续返回旧玩家身上的数据
            event.addListener(provider::invalidate);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        event.getOriginal().reviveCaps();
        // 非死亡 Clone (维度切换 / 末地返回) 必须原样保留; 死亡复位则由配置决定。
        boolean keep = !event.isWasDeath() || HungerConfig.keepHungerOnRespawn();
        if (keep) {
            Digestion old = event.getOriginal().getCapability(DigestionCapability.DIGESTION).orElse(null);
            if (old != null) {
                event.getEntity().getCapability(DigestionCapability.DIGESTION)
                        .ifPresent(current -> current.deserializeNBT(old.serializeNBT()));
            }
            // 死亡复活且配置保留: 恢复死亡前的饥饿值/饱和度 (覆盖原版 20/5 重置)
            if (event.isWasDeath()) {
                FoodData oldFood = event.getOriginal().getFoodData();
                FoodData newFood = event.getEntity().getFoodData();
                newFood.setFoodLevel(oldFood.getFoodLevel());
                newFood.setSaturation(oldFood.getSaturationLevel());
            }
        } else {
            // keepHungerOnRespawn=false: FoodData 已由构造器重建为开局值, 消化值也必须同步重置,
            // 否则旧消化值会按 baseDigestionRate 重新转成饥饿值, 绕过"重置"语义。
            // resetToStart 同时清掉 foodCarry 与属性因子缓存; 属性由下个 10-tick 重算, 同步由 onPlayerRespawn 完成。
            event.getEntity().getCapability(DigestionCapability.DIGESTION)
                    .ifPresent(Digestion::resetToStart);
        }
        // 死亡损失 (四项各自独立, 默认全 0 = 关闭): 先按百分比扣减, 再做低饥饿保底
        applyDeathStatLoss(event);
        // 低饥饿死亡保底: 死前饥饿值低于阈值 → 复活补到阈值 (默认 30% maxHunger, 0 = 关闭)
        applyStarvationRespawnFloor(event);
        event.getOriginal().invalidateCaps();
    }

    /**
     * 死亡损失: 复活后的数值 = 进入复活流程时的数值 × (1 - pct%)。
     * <p>
     * 四项各自由独立配置控制, 默认全 0 即整个机制关闭。基准是"复活流程已确定的数值"——
     * {@code keepHungerOnRespawn=true} 时是死前值, false 时是开局值。
     * 消化值/消化饱和度都按<b>扣减前</b>的值计算百分比, 最后按 {@code sat <= digestion} 不变量 clamp。
     */
    private static void applyDeathStatLoss(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            return;
        }
        float hungerLoss = HungerConfig.deathHungerLossPercent();
        float satLoss = HungerConfig.deathSaturationLossPercent();
        float digLoss = HungerConfig.deathDigestionLossPercent();
        float digSatLoss = HungerConfig.deathDigestionSaturationLossPercent();
        if (hungerLoss <= 0.0F && satLoss <= 0.0F && digLoss <= 0.0F && digSatLoss <= 0.0F) {
            return;   // 全 0 = 关闭
        }

        FoodData food = event.getEntity().getFoodData();
        float sat = food.getSaturationLevel();
        if (hungerLoss > 0.0F) {
            food.setFoodLevel(Math.round(food.getFoodLevel() * (1.0F - hungerLoss / 100.0F)));
        }
        if (satLoss > 0.0F) {
            sat *= 1.0F - satLoss / 100.0F;
        }
        food.setSaturation(Math.min(sat, food.getFoodLevel()));

        Digestion digestion = event.getEntity().getCapability(DigestionCapability.DIGESTION).orElse(null);
        if (digestion != null) {
            float digSat = digestion.getDigestionSaturation();
            if (digLoss > 0.0F) {
                digestion.setDigestion(digestion.getDigestion() * (1.0F - digLoss / 100.0F));
            }
            if (digSatLoss > 0.0F) {
                digSat *= 1.0F - digSatLoss / 100.0F;
            }
            // setter 内部会按 sat <= digestion 与自身上限 clamp
            digestion.setDigestionSaturation(digSat);
        }
    }

    /**
     * 低饥饿死亡复活保底。
     * <p>
     * 死亡时 {@code foodLevel < maxHunger × starvationRespawnHungerPercent%} → 复活后把饥饿值补到该值。
     * <b>同一个配置值同时是判定阈值与给予值</b>，所以调整时两边不会错位；配置为 0 时整个机制关闭。
     * <p>
     * 只抬高、不压低：{@code keepHungerOnRespawn=false} 且开局值高于保底时保持开局值。
     * 补完后再把饱和度 clamp 回不超过饥饿值，维持原版 {@code sat <= foodLevel} 不变量。
     */
    private static void applyStarvationRespawnFloor(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            return;
        }
        float percent = HungerConfig.starvationRespawnHungerPercent();
        if (percent <= 0.0F) {
            return;   // 0% = 关闭 (否则阈值 0 永远不成立, 显式跳过更清楚)
        }
        float threshold = StatFactors.maxHunger() * percent / 100.0F;
        if (event.getOriginal().getFoodData().getFoodLevel() >= threshold) {
            return;   // 死亡时并未低于阈值
        }
        FoodData newFood = event.getEntity().getFoodData();
        int floor = Math.round(threshold);
        if (newFood.getFoodLevel() < floor) {
            newFood.setFoodLevel(floor);
        }
        newFood.setSaturation(Math.min(newFood.getSaturationLevel(), newFood.getFoodLevel()));
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ModNetwork.syncTo(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ModNetwork.syncTo(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ModNetwork.syncTo(player);
        }
    }

    // ---------------------------------------------------------------- 进食拦截

    @SubscribeEvent
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity().level().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        FoodProperties food = event.getItem().getItem().getFoodProperties(event.getItem(), player);
        if (food != null) {
            FoodData data = player.getFoodData();
            EAT_SNAPSHOTS.put(player.getUUID(), new float[]{data.getFoodLevel(), data.getSaturationLevel()});
        }
    }

    @SubscribeEvent
    public static void onUseItemStop(LivingEntityUseItemEvent.Stop event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EAT_SNAPSHOTS.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity().level().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        FoodData data = player.getFoodData();
        float[] snapshot = EAT_SNAPSHOTS.remove(player.getUUID());
        if (snapshot == null) {
            return;
        }
        data.setFoodLevel((int) snapshot[0]);                 // 还原原版直接加的饥饿值
        data.setSaturation(snapshot[1]);                      // 还原原版直接加的饱和度
        // 修复: 饥饿值已满时原版 eat 不产生增量 (旧逻辑据此提前 return 导致吃食物不加消化值)。
        // 改为直接取食物营养值, 只要消化值/消化饱和度还有空间就照常等量增加。
        FoodProperties food = event.getItem().getItem().getFoodProperties(event.getItem(), player);
        if (food == null) {
            return;
        }
        Digestion digestion = player.getCapability(DigestionCapability.DIGESTION).orElse(null);
        if (digestion == null) {
            return;
        }
        // 取值优先级: 配置层覆盖 > 代码登记层 > nutrition * digestionPerNutrition(默认 1:1)
        // 三层都算完后再广播 FoodDigestionEvent, 监听器拿到的就是最终默认值, 仍可继续改写/取消
        Float override = FoodDigestionValues.resolve(event.getItem());
        float gain = override != null
                ? override
                : Math.max(0.0F, food.getNutrition() * HungerConfig.digestionPerNutrition());
        // 消化饱和度沿用原版 FoodData.eat() 的换算:
        //   saturationLevel += nutrition × saturationModifier × 2.0F
        // 这里把 "nutrition" 换成上面解析出的消化值增益, 因此饱和比例跟随倍率/覆盖值一起缩放。
        float defaultSatGain = Math.max(0.0F, gain * food.getSaturationModifier() * 2.0F);
        FoodDigestionEvent foodEvent = new FoodDigestionEvent(player, event.getItem(), food, gain, defaultSatGain);
        boolean cancelled = MinecraftForge.EVENT_BUS.post(foodEvent);
        if (!cancelled) {
            float digestionGain = Math.max(0.0F, foodEvent.getDigestion());
            float satGain = Math.max(0.0F, foodEvent.getDigestionSaturation());
            if (digestionGain != 0.0F) {
                DigestionHelper.addDigestion(player, digestionGain);
            }
            if (satGain != 0.0F) {
                DigestionHelper.addDigestionSaturation(player, satGain);
            }
        }
        updateStats(player, digestion);
        ModNetwork.syncTo(player);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        EAT_SNAPSHOTS.remove(id);   // 进食中断线: 快照不再有 Finish/Stop 事件清理, 必须手动移除
    }

    /** 服务器关闭时清空全部按玩家缓存, 避免整合包反复开关世界时累积 */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        EAT_SNAPSHOTS.clear();
    }

    /**
     * 起床结算 (PlayerWakeUpEvent)。
     * <p>
     * 原版床<b>不恢复饥饿值</b>；多数饥饿模组会借这个事件给玩家补满消化值 / 饥饿值。
     * 本模组不默认这么做 —— 按"食物 → 消化值 → 饥饿值"的设计，睡眠本身不产出食物，
     * 整夜代谢<b>更应该起床后损失饥饿值</b>（把 {@code sleepHungerDelta} 设为负值即可），
     * 而不是凭空补满。因此四个 delta 默认全为 0（保持原版行为）；正数 = 补充，负数 = 损失，
     * 由整合包自行决定方向。
     * <p>
     * 注意：{@code PlayerWakeUpEvent#wakeImmediately()} 只用于起床淡出动画，
     * <b>不能</b>区分"睡完整晚"与"刚躺下就离床"，故这里不用它做条件；事件在客户端也会触发，
     * 只对 {@link ServerPlayer} 生效。若整合包要求"只有真正跳过夜晚才结算"，应改用
     * {@code SleepFinishedTimeEvent}。
     */
    @SubscribeEvent
    public static void onPlayerWakeUp(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        float hungerDelta = HungerConfig.sleepHungerDelta();
        float saturationDelta = HungerConfig.sleepSaturationDelta();
        float digestionDelta = HungerConfig.sleepDigestionDelta();
        float digestionSatDelta = HungerConfig.sleepDigestionSaturationDelta();
        if (hungerDelta == 0.0F && saturationDelta == 0.0F
                && digestionDelta == 0.0F && digestionSatDelta == 0.0F) {
            return;
        }

        FoodData food = player.getFoodData();
        int maxFood = Math.round(StatFactors.maxHunger());
        int newFood = Mth.clamp(food.getFoodLevel() + Math.round(hungerDelta), 0, maxFood);
        food.setFoodLevel(newFood);
        if (saturationDelta != 0.0F || hungerDelta != 0.0F) {
            // 饥饿值下降时同步收敛饱和度, 维持 vanilla 的 sat <= foodLevel 不变量
            food.setSaturation(Mth.clamp(food.getSaturationLevel() + saturationDelta, 0.0F, newFood));
        }

        // 走 DigestionHelper, 溢出同样会广播 DigestionOverflowEvent (可被内置缓存消费)
        if (digestionDelta != 0.0F) {
            DigestionHelper.addDigestion(player, digestionDelta);
        }
        if (digestionSatDelta != 0.0F) {
            DigestionHelper.addDigestionSaturation(player, digestionSatDelta);
        }

        refreshStats(player);
        ModNetwork.syncTo(player);
    }

    // ---------------------------------------------------------------- 转化 + 阈值效果 + 呕吐

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player) || player.isDeadOrDying()) {
            return;
        }
        Digestion digestion = player.getCapability(DigestionCapability.DIGESTION).orElse(null);
        if (digestion == null) {
            return;
        }
        FoodData data = player.getFoodData();
        boolean sync = false;

        // 每 0.5s: 转化 / 属性更新 / 呕吐检测 / 消耗值同步
        if (player.tickCount % 10 == 0) {
            // 消耗值同步: 变化 >= 0.01 才计入同步 (仿 AppleSkin SyncHandler)。
            // 检查频率与转化同频 (0.5s), 不再每 tick 查询/装箱。
            float exhaustion = data.getExhaustionLevel();
            if (Math.abs(digestion.getLastSyncedExhaustion() - exhaustion) >= 0.01F) {
                digestion.setLastSyncedExhaustion(exhaustion);
                sync = true;
            }
            // 速率受药水效果影响: 助消化每级 +20%, 消化不良每级 -20% (互相抵消, 下限 0),
            // 瞬间消化在此基础上再 ×10 (叠乘)。仍走同一条转化路径, 只是每次转得更多, 所以不会出现"一次清空"
            float rate = HungerConfig.BASE_DIGESTION_RATE.get().floatValue()
                    * ModMobEffects.rateMultiplier(player);
            // 过载加速: 消化值进入惩罚区后, 「消化值 → 饥饿值」的速率随消化值线性放大
            // (惩罚起点 ×1, 满值 ×1.5, 与惩罚区同一起点配置)。只加速主转化, 不动消化饱和度,
            // 否则饱和度会先把饥饿值填满、反把主转化堵住, 与"缓解吃撑"的意图相反。
            float overloadRate = rate * StatFactors.digConversionMultiplier(digestion.getDigestion());

            // 先转消化饱和度: 它是消化值之上的"上层", 先转出可避免随后消化值下降时被不变量直接压掉(凭空消失)
            if (digestion.getDigestionSaturation() > 0.0F
                    && data.getFoodLevel() > data.getSaturationLevel()) {
                float satRoom = data.getFoodLevel() - data.getSaturationLevel();
                float add = Math.min(Math.min(digestion.getDigestionSaturation(), rate), satRoom);
                DigestionConversionEvent conv = new DigestionConversionEvent(player, digestion,
                        DigestionConversionEvent.Type.DIGESTION_SATURATION, add);
                if (!MinecraftForge.EVENT_BUS.post(conv)) {
                    add = Mth.clamp(conv.getAmount(), 0.0F,
                            Math.min(digestion.getDigestionSaturation(),
                                    data.getFoodLevel() - data.getSaturationLevel()));
                    if (add > 0.0F) {
                        digestion.setDigestionSaturation(digestion.getDigestionSaturation() - add);
                        data.setSaturation(data.getSaturationLevel() + add);
                        sync = true;
                    }
                }
            }

            // 再转消化值 -> 饥饿值 (饥饿值是整数, 用进位缓存攒整点; 饥饿值满时暂停消耗)
            if (data.getFoodLevel() < StatFactors.maxHunger() && digestion.getDigestion() > 0.0F) {
                // 容量 = min(饥饿值还能装多少, 不能把上层消化饱和度压掉的部分);
                // 有了这个上限, 进位缓存就不会超出 [0,1] 被截断 —— 瞬间消化的"立即消化"也不会浪费
                float hungerRoom = Math.max(0.0F, StatFactors.maxHunger() - data.getFoodLevel()
                        - digestion.getFoodCarry());
                float layerRoom = Math.max(0.0F, digestion.getDigestion() - digestion.getDigestionSaturation());
                float capacity = Math.min(hungerRoom, layerRoom);
                float transfer = Math.min(Math.min(digestion.getDigestion(), overloadRate), capacity);
                DigestionConversionEvent conv = new DigestionConversionEvent(player, digestion,
                        DigestionConversionEvent.Type.DIGESTION, transfer);
                if (!MinecraftForge.EVENT_BUS.post(conv)) {
                    // 监听器可加速/减速; 但不允许超过拥有量, 也不允许超过容量(否则进位缓存溢出会被截断)
                    transfer = Mth.clamp(conv.getAmount(), 0.0F, Math.min(digestion.getDigestion(), capacity));
                    if (transfer > 0.0F) {
                        digestion.setDigestion(digestion.getDigestion() - transfer);
                        float carry = digestion.getFoodCarry() + transfer;
                        while (carry >= 1.0F && data.getFoodLevel() < StatFactors.maxHunger()) {
                            data.setFoodLevel(data.getFoodLevel() + 1);
                            carry -= 1.0F;
                        }
                        digestion.setFoodCarry(carry);
                        sync = true;
                    }
                }
            }

            // 溢出缓存: 转化腾出空间后按 baseDigestionRate 回填 (默认关闭)。
            // 瞬间消化不参与缓存: 它只加速"消化值 -> 饥饿值"这一步, 缓存仍按正常速率回填,
            // 避免出现"一次性把缓存抽干"的观感(而且缓存本身只是溢出的排队区, 不属于消化速率)
            if (OverflowCacheHandler.feed(digestion, rate)) {
                sync = true;
            }

            updateStats(player, digestion);

            // 消化值达到配置阈值 (默认 = 100% maxDigestion): 呕吐清空并给反胃。
            // 开启溢出缓存时由缓存取代呕吐, 不再触发。
            float vomitThreshold = Digestion.maxDigestion() * HungerConfig.vomitThresholdPercent() / 100.0F;
            if (!HungerConfig.enableOverflowCache() && digestion.getDigestion() >= vomitThreshold - 1e-3f) {
                DigestionVomitEvent vomit = new DigestionVomitEvent(player, digestion,
                        digestion.getDigestion(), HungerConfig.vomitDurationTicks(),
                        HungerConfig.vomitNauseaAmplifier());
                if (!MinecraftForge.EVENT_BUS.post(vomit)) {
                    digestion.setDigestion(0.0F);
                    digestion.setDigestionSaturation(0.0F);
                    digestion.setFoodCarry(0.0F);
                    if (vomit.getNauseaDuration() > 0) {
                        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION,
                                vomit.getNauseaDuration(), vomit.getNauseaAmplifier(),
                                false, true, true));
                    }
                    sync = true;
                }
            }
        }

        // 安全网: 效果(饥饿效果等)直接改动消化值时也保证客户端能刷新 (每 2s)
        if (player.tickCount % 40 == 0) {
            sync = true;
        }

        if (sync) {
            ModNetwork.syncTo(player);
        }
    }

    /**
     * 根据当前饥饿值/消化值更新属性效率。
     * <p>
     * 公式结果先写入本模组的两个自定义属性（{@link ModAttributes#HUNGER_EFFICIENCY} /
     * {@link ModAttributes#DIGESTION_EFFICIENCY}），再由它们推导原版属性上的修饰器：
     * 其它模组只要给自定义属性加修饰器就能调整惩罚，不必知道本模组内部 UUID，
     * 也不会与原版属性上的其它修饰器抢乘区，{@code /attribute} 亦可直接查询。
     * <p>
     * 攻击力用「饥饿 × 消化攻击效率」；攻速/移速/挖掘/游泳用「饥饿 × 消化速度效率」。
     * 挖掘速度在 Forge 1.20.1 无对应属性，由 BreakSpeed 事件读取同一个自定义属性（免 Mixin）。
     */
    private static void updateStats(ServerPlayer player, Digestion digestion) {
        // 创造/旁观不施加任何属性惩罚; 顺手清掉可能残留的修饰器并重置缓存,
        // 保证玩家切换游戏模式后立刻恢复 (否则旧修饰器会一直留着)
        if (isStatExempt(player)) {
            clearModifiers(player);
            digestion.setLastAttrFactor(1.0F);
            digestion.setLastAttackFactor(1.0F);
            digestion.setLastSwimFactor(1.0F);
            return;
        }

        // 1) 公式 -> 自定义属性基础值 (其它模组的修饰器会叠加在这之上);
        // 消化侧拆成攻击/速度两条曲线, 各自带不可配置的硬上限
        ModAttributes.setHungerEfficiency(player,
                StatFactors.hungerAttrFactor(player.getFoodData().getFoodLevel()));
        ModAttributes.setDigestionEfficiency(player,
                StatFactors.digSpeedFactor(digestion.getDigestion()));
        ModAttributes.setDigestionAttackEfficiency(player,
                StatFactors.digAttackFactor(digestion.getDigestion()));

        // 2) 从属性读回最终效率 (含其它模组修饰器), 再落到原版属性上
        double speedFactor = ModAttributes.combinedEfficiency(player);       // 攻速 / 移速 / 挖掘
        double attackFactor = ModAttributes.combinedAttackEfficiency(player); // 攻击力
        double swimFactor = ModAttributes.digestionEfficiency(player);        // 游泳

        if (Math.abs(attackFactor - digestion.getLastAttackFactor()) > 1e-4) {
            applyFactor(player, Attributes.ATTACK_DAMAGE, UUID_ATTACK, attackFactor);
            digestion.setLastAttackFactor((float) attackFactor);
        }
        if (Math.abs(speedFactor - digestion.getLastAttrFactor()) > 1e-4) {
            applyFactor(player, Attributes.ATTACK_SPEED, UUID_ATK_SPD, speedFactor);
            applyFactor(player, Attributes.MOVEMENT_SPEED, UUID_MOVE, speedFactor);
            digestion.setLastAttrFactor((float) speedFactor);
        }
        if (Math.abs(swimFactor - digestion.getLastSwimFactor()) > 1e-4) {
            applyFactor(player, ForgeMod.SWIM_SPEED.get(), UUID_SWIM, swimFactor);
            digestion.setLastSwimFactor((float) swimFactor);
        }
    }

    /** 对外 API: 清除本模组施加在玩家身上的全部属性修饰器, 并把三个自定义属性复位为 1.0 */
    public static void clearModifiers(Player player) {
        applyFactor(player, Attributes.ATTACK_DAMAGE, UUID_ATTACK, 1.0);
        applyFactor(player, Attributes.ATTACK_SPEED, UUID_ATK_SPD, 1.0);
        applyFactor(player, Attributes.MOVEMENT_SPEED, UUID_MOVE, 1.0);
        applyFactor(player, ForgeMod.SWIM_SPEED.get(), UUID_SWIM, 1.0);
        ModAttributes.reset(player);
    }

    /** 对外 API: 立刻按当前饥饿值/消化值重算属性惩罚 (reset/set 后调用) */
    public static void refreshStats(ServerPlayer player) {
        Digestion digestion = player.getCapability(DigestionCapability.DIGESTION).orElse(null);
        if (digestion != null) {
            updateStats(player, digestion);
        }
    }

    /** 创造/旁观模式豁免全部消化相关惩罚 */
    private static boolean isStatExempt(Player player) {
        return player.isCreative() || player.isSpectator();
    }

    private static void applyFactor(Player player, Attribute attr, UUID uuid, double factor) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) {
            return;
        }
        inst.removeModifier(uuid);
        if (factor != 1.0) {
            inst.addTransientModifier(new AttributeModifier(uuid, NewHungerSystem.MOD_ID,
                    factor - 1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    // ---------------------------------------------------------------- 挖掘速度惩罚 (Forge 原生事件, 免 Mixin)

    @SubscribeEvent
    public static void onBreakSpeed(BreakSpeed event) {
        Player player = event.getEntity();
        if (isStatExempt(player)) {
            return;
        }
        // 直接读自定义属性: 与服务端的攻/速惩罚共用同一份数值, 其它模组的修饰器也会生效,
        // 且客户端拿的是同步值 (不再依赖客户端 Capability 是否被填充)
        double factor;
        if (ModAttributes.available(player)) {
            factor = ModAttributes.combinedEfficiency(player);
        } else {
            Digestion digestion = player.getCapability(DigestionCapability.DIGESTION).orElse(null);
            if (digestion == null) {
                return;
            }
            factor = StatFactors.hungerAttrFactor(player.getFoodData().getFoodLevel())
                    * StatFactors.digSpeedFactor(digestion.getDigestion());
        }
        if (factor != 1.0) {
            event.setNewSpeed((float) (event.getNewSpeed() * factor));
        }
    }
}