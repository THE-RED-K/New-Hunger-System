package dev.newhungersystem.api;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Locale;

/**
 * {@code /digestion} 命令：查看 / 重置 / 设定玩家的消化状态。
 * <ul>
 *   <li>{@code /digestion get [target]} —— 查看（所有玩家可用）</li>
 *   <li>{@code /digestion reset [target]} —— 完全归零，含 {@code foodCarry}（OP）</li>
 *   <li>{@code /digestion start [target]} —— 重置为配置开局数值（OP）</li>
 *   <li>{@code /digestion set <digestion> <digestionSaturation> [target]} —— 直接设定（OP）</li>
 * </ul>
 * 写操作统一走 {@link DigestionHelper}，保证数据 / 属性修饰器 / 客户端同步三者一致。
 *
 * @author THEREDK
 */
public final class DigestionCommands {

    private DigestionCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("digestion");

        root.then(Commands.literal("get")
                .executes(ctx -> get(ctx, ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> get(ctx, EntityArgument.getPlayer(ctx, "target")))));

        root.then(Commands.literal("reset").requires(src -> src.hasPermission(2))
                .executes(ctx -> reset(ctx, ctx.getSource().getPlayerOrException(), false))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> reset(ctx, EntityArgument.getPlayer(ctx, "target"), false))));

        root.then(Commands.literal("start").requires(src -> src.hasPermission(2))
                .executes(ctx -> reset(ctx, ctx.getSource().getPlayerOrException(), true))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> reset(ctx, EntityArgument.getPlayer(ctx, "target"), true))));

        root.then(Commands.literal("set").requires(src -> src.hasPermission(2))
                .then(Commands.argument("digestion", FloatArgumentType.floatArg(0.0F))
                        .then(Commands.argument("digestionSaturation", FloatArgumentType.floatArg(0.0F))
                                .executes(ctx -> set(ctx, ctx.getSource().getPlayerOrException(),
                                        FloatArgumentType.getFloat(ctx, "digestion"),
                                        FloatArgumentType.getFloat(ctx, "digestionSaturation")))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(ctx -> set(ctx, EntityArgument.getPlayer(ctx, "target"),
                                                FloatArgumentType.getFloat(ctx, "digestion"),
                                                FloatArgumentType.getFloat(ctx, "digestionSaturation")))))));

        root.then(Commands.literal("bonus").requires(src -> src.hasPermission(2))
                .then(Commands.argument("kind", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                new String[]{"hunger", "saturation", "digestion", "digestionSaturation"}, builder))
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg())
                                .executes(ctx -> bonus(ctx,
                                        StringArgumentType.getString(ctx, "kind"),
                                        DoubleArgumentType.getDouble(ctx, "amount"))))));

        dispatcher.register(root);
    }

    /** 运行时调整上限加成并立即同步所有在线玩家（服务器运行中会写入当前存档） */
    private static int bonus(CommandContext<CommandSourceStack> ctx, String kind, double amount) {
        switch (kind) {
            case "hunger" -> HungerConfig.addMaxHunger(amount);
            case "saturation" -> HungerConfig.addMaxSaturation(amount);
            case "digestion" -> HungerConfig.addMaxDigestion(amount);
            case "digestionSaturation" -> HungerConfig.addMaxDigestionSaturation(amount);
            default -> {
                ctx.getSource().sendFailure(Component.translatable(
                        "command.new_hunger_system.bonus.unknown_kind", kind));
                return 0;
            }
        }
        MinecraftServer server = ctx.getSource().getServer();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ModNetwork.syncTo(player);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.new_hunger_system.bonus.applied",
                kind,
                String.format(Locale.ROOT, "%+.2f", amount),
                fmt(HungerConfig.serverBonusMaxHunger()), fmt(HungerConfig.serverBonusMaxSaturation()),
                fmt(HungerConfig.serverBonusMaxDigestion()), fmt(HungerConfig.serverBonusMaxDigestionSaturation())), true);
        return 1;
    }

    /** 数值统一按 Locale.ROOT 格式化, 保证翻译文本里的数字始终用 '.' 作小数点 */
    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static int get(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        Digestion data = DigestionHelper.get(target);
        if (data == null) {
            ctx.getSource().sendFailure(Component.translatable("command.new_hunger_system.no_capability",
                    target.getScoreboardName()));
            return 0;
        }
        String msg = String.format(Locale.ROOT,
                "%s: digestion %.2f/%.2f, digSat %.2f/%.2f, carry %.2f, cache %.2f/%.2f (cap %.2f), "
                        + "hunger %d, saturation %.2f, exhaustion %.2f, bonus h/s/d/ds %.2f/%.2f/%.2f/%.2f",
                target.getScoreboardName(), data.getDigestion(), Digestion.maxDigestion(),
                data.getDigestionSaturation(), Digestion.maxDigestionSaturation(), data.getFoodCarry(),
                data.getOverflowDigestionCache(), data.getOverflowSaturationCache(),
                HungerConfig.overflowCacheCapacity(),
                target.getFoodData().getFoodLevel(), target.getFoodData().getSaturationLevel(),
                target.getFoodData().getExhaustionLevel(),
                HungerConfig.serverBonusMaxHunger(), HungerConfig.serverBonusMaxSaturation(),
                HungerConfig.serverBonusMaxDigestion(), HungerConfig.serverBonusMaxDigestionSaturation());
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx, ServerPlayer target, boolean toStart) {
        boolean ok = toStart ? DigestionHelper.resetToStart(target) : DigestionHelper.reset(target);
        if (!ok) {
            ctx.getSource().sendFailure(Component.translatable("command.new_hunger_system.no_capability",
                    target.getScoreboardName()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(
                toStart ? "command.new_hunger_system.reset.start" : "command.new_hunger_system.reset.done",
                target.getScoreboardName()), false);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> ctx, ServerPlayer target,
                           float digestion, float digestionSaturation) {
        boolean ok = DigestionHelper.set(target, digestion, digestionSaturation);
        if (!ok) {
            ctx.getSource().sendFailure(Component.translatable("command.new_hunger_system.no_capability",
                    target.getScoreboardName()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.new_hunger_system.set.done",
                target.getScoreboardName(), fmt(digestion), fmt(digestionSaturation)), false);
        return 1;
    }
}
