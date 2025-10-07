package com.captainziboo.quartz4mc.command;

import com.captainziboo.quartz4mc.config.QuartzConfig;
import com.captainziboo.quartz4mc.manager.QuartzManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class QuartzCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger("quartz4mc-commands");
    private static QuartzConfig config;

    private static final String QUARTZ_TAG = "§d[Quartz] " + Formatting.RESET; // Rose Quartz

    // ---------------- Suggestion Providers ----------------
    private static final SuggestionProvider<ServerCommandSource> EXISTING_CRON_SUGGESTIONS =
        (context, builder) -> {
            if (config != null && config.crons != null) {
                config.crons.stream().map(c -> c.id).forEach(builder::suggest);
            }
            return builder.buildFuture();
        };

    private static final SuggestionProvider<ServerCommandSource> ACTIVE_CRON_SUGGESTIONS =
        (context, builder) -> {
            if (config != null && config.crons != null) {
                config.crons.stream().filter(c -> c.enabled).map(c -> c.id).forEach(builder::suggest);
            }
            return builder.buildFuture();
        };

    private static final SuggestionProvider<ServerCommandSource> INACTIVE_CRON_SUGGESTIONS =
        (context, builder) -> {
            if (config != null && config.crons != null) {
                config.crons.stream().filter(c -> !c.enabled).map(c -> c.id).forEach(builder::suggest);
            }
            return builder.buildFuture();
        };

    private static final SuggestionProvider<ServerCommandSource> QUARTZ_PATTERN_SUGGESTIONS =
        (context, builder) -> {
            builder.suggest("\"0 */1 * * * ?\"", Text.literal("Every minute"));
            builder.suggest("\"0 */5 * * * ?\"", Text.literal("Every 5 minutes"));
            builder.suggest("\"0 0 * * * ?\"", Text.literal("Every hour"));
            builder.suggest("\"0 0 12 * * ?\"", Text.literal("Every day at noon"));
            builder.suggest("\"0 0 0 * * ?\"", Text.literal("Every day at midnight"));
            builder.suggest("\"0 0 0 * * SUN\"", Text.literal("Every Sunday at midnight"));
            builder.suggest("\"0 0 0 1 * ?\"", Text.literal("First day of every month"));
            builder.suggest("\"0 30 8 * * MON-FRI\"", Text.literal("Weekdays at 8:30 AM"));
            return builder.buildFuture();
        };

    private static final SuggestionProvider<ServerCommandSource> LIST_FILTER_SUGGESTIONS =
        (context, builder) -> {
            builder.suggest("enabled", Text.literal("Show only enabled crons"));
            builder.suggest("disabled", Text.literal("Show only disabled crons"));
            return builder.buildFuture();
        };

    // ---------------- Command Registration ----------------
    public static void register(QuartzConfig cfg) {
        config = cfg;

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var quartz4mc = CommandManager.literal("quartz4mc")
                .requires(source -> source.hasPermissionLevel(config.minPermissionLevel))

                .then(CommandManager.literal("reload")
                    .executes(QuartzCommands::reloadCommand))

                .then(CommandManager.literal("list")
                    .executes(QuartzCommands::listAllCommand)
                    .then(CommandManager.argument("filter", StringArgumentType.word())
                        .suggests(LIST_FILTER_SUGGESTIONS)
                        .executes(QuartzCommands::listFilteredCommand)))

                .then(CommandManager.literal("add")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .then(CommandManager.argument("pattern", StringArgumentType.string())
                            .suggests(QUARTZ_PATTERN_SUGGESTIONS)
                            .then(CommandManager.argument("command", StringArgumentType.greedyString())
                                .executes(QuartzCommands::addCommand)))))

                .then(CommandManager.literal("remove")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .suggests(EXISTING_CRON_SUGGESTIONS)
                        .executes(QuartzCommands::removeCommand)))

                .then(CommandManager.literal("start")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .suggests(INACTIVE_CRON_SUGGESTIONS)
                        .executes(QuartzCommands::startCommand)))

                .then(CommandManager.literal("stop")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .suggests(ACTIVE_CRON_SUGGESTIONS)
                        .executes(QuartzCommands::stopCommand)))

                .then(CommandManager.literal("details")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .suggests(EXISTING_CRON_SUGGESTIONS)
                        .executes(QuartzCommands::detailsCommand)))

                .then(CommandManager.literal("status")
                    .executes(QuartzCommands::statusCommand));

            dispatcher.register(quartz4mc);
            dispatcher.register(CommandManager.literal("quartz").redirect(quartz4mc.build()));
            dispatcher.register(CommandManager.literal("cron").redirect(quartz4mc.build()));
        });
    }

    // ---------------- Commands ----------------
    private static int reloadCommand(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        try {
            QuartzManager quartzManager = QuartzManager.getInstance();
            config = QuartzConfig.load();
            int loadedCrons = quartzManager.loadAndStartEnabledCrons(config);

            source.sendFeedback(() -> Text.literal(QUARTZ_TAG + Formatting.GRAY + "Configuration reloaded successfully."), false);
            source.sendFeedback(() -> Text.literal(QUARTZ_TAG + Formatting.WHITE + loadedCrons + Formatting.GRAY + " cron(s) loaded."), false);

            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            source.sendError(Text.literal(QUARTZ_TAG + Formatting.RED + "Failed to reload configuration: " + e.getMessage()));
            LOGGER.error("[QuartzCommand] Error reloading configuration", e);
            return 0;
        }
    }

    private static int listAllCommand(CommandContext<ServerCommandSource> context) {
        return executeListCommand(context.getSource(), null);
    }

    private static int listFilteredCommand(CommandContext<ServerCommandSource> context) {
        String filter = StringArgumentType.getString(context, "filter");
        return executeListCommand(context.getSource(), filter);
    }

    private static int executeListCommand(ServerCommandSource source, String filter) {
        List<QuartzConfig.CronEntry> cronsToShow;
        String title;

        if (filter == null) {
            cronsToShow = config.crons;
            title = "All crons";
        } else {
            switch (filter.toLowerCase()) {
                case "enabled":
                    cronsToShow = config.crons.stream().filter(c -> c.enabled).collect(Collectors.toList());
                    title = "Enabled crons";
                    break;
                case "disabled":
                    cronsToShow = config.crons.stream().filter(c -> !c.enabled).collect(Collectors.toList());
                    title = "Disabled crons";
                    break;
                default:
                    cronsToShow = config.crons;
                    title = "All crons";
                    source.sendFeedback(() -> Text.literal(QUARTZ_TAG + Formatting.GRAY + "Unknown filter '" + filter + "', showing all crons."), false);
                    break;
            }
        }

        if (cronsToShow.isEmpty()) {
            source.sendFeedback(() -> Text.literal(QUARTZ_TAG + Formatting.GRAY + "No crons found."), false);
            return Command.SINGLE_SUCCESS;
        }

        source.sendFeedback(() -> Text.literal(QUARTZ_TAG + Formatting.GRAY + title + " (" + cronsToShow.size() + "):"), false);
        for (QuartzConfig.CronEntry cron : cronsToShow) {
            if (cron == null || cron.id == null) continue;
            String status = cron.enabled ? Formatting.GREEN + "enabled" : Formatting.RED + "disabled";
            source.sendFeedback(() -> Text.literal("  " + Formatting.WHITE + cron.id + Formatting.GRAY + " [" + status + Formatting.GRAY + "]"), false);
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int addCommand(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String id = StringArgumentType.getString(context, "id");
        String patternInput = StringArgumentType.getString(context, "pattern").trim();
        String command = StringArgumentType.getString(context, "command");

        if (patternInput.startsWith("\"") && patternInput.endsWith("\"")) {
            patternInput = patternInput.substring(1, patternInput.length() - 1);
        }

        final String pattern = patternInput;

        if (config.crons.stream().anyMatch(c -> c.id.equals(id))) {
            source.sendError(Text.literal(QUARTZ_TAG + Formatting.RED + "A cron with ID " + Formatting.WHITE + id + Formatting.RED + " already exists!"));
            return 0;
        }

        if (command.trim().isEmpty()) {
            source.sendError(Text.literal(QUARTZ_TAG + Formatting.RED + "Command cannot be empty!"));
            return 0;
        }

        try {
            org.quartz.CronExpression.validateExpression(pattern);
        } catch (Exception e) {
            source.sendError(Text.literal(QUARTZ_TAG + Formatting.RED + "Invalid Quartz pattern: " + Formatting.WHITE + pattern + Formatting.RED + " (" + e.getMessage() + ")"));
            return 0;
        }

        QuartzConfig.CronEntry newCron = new QuartzConfig.CronEntry();
        newCron.id = id;
        newCron.schedule = pattern;
        newCron.command = command;
        newCron.enabled = true;

        config.crons.add(newCron);

        try {
            boolean started = QuartzManager.getInstance().startCron(newCron);
            config.saveAsync();

            source.sendFeedback(() -> Text.literal(QUARTZ_TAG + Formatting.GRAY + "Cron " + Formatting.WHITE + id + Formatting.GRAY + " added:"), true);
            source.sendFeedback(() -> Text.literal("  " + Formatting.GRAY + "Status: " + (newCron.enabled ? Formatting.GREEN + "Enabled" : Formatting.RED + "Disabled")), false);
            source.sendFeedback(() -> Text.literal("  " + Formatting.GRAY + "Quartz Pattern: " + Formatting.WHITE + newCron.schedule), false);
            source.sendFeedback(() -> Text.literal("  " + Formatting.GRAY + "Command: " + Formatting.WHITE + newCron.command), false);
        } catch (Exception e) {
            source.sendError(Text.literal(QUARTZ_TAG + Formatting.RED + "Failed to start cron: " + e.getMessage()));
            LOGGER.error("[QuartzCommand] Error starting cron " + id, e);
            return 0;
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int removeCommand(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String id = StringArgumentType.getString(context, "id");

        QuartzConfig.CronEntry target = config.crons.stream().filter(c -> c.id.equals(id)).findFirst().orElse(null);
        if (target == null) {
            source.sendError(Text.literal(QUARTZ_TAG + Formatting.RED + "Cron " + Formatting.WHITE + id + Formatting.RED + " not found!"));
            return 0;
        }

        try {
            QuartzManager.getInstance().stopCron(id);
            config.crons.remove(target);
            config.saveAsync();
            source.sendFeedback(() -> Text.literal(QUARTZ_TAG + Formatting.GRAY + "Cron " + Formatting.WHITE + id + Formatting.GRAY + " removed."), true);
        } catch (Exception e) {
            source.sendError(Text.literal(QUARTZ_TAG + Formatting.RED + "Failed to remove cron: " + e.getMessage()));
            LOGGER.error("[QuartzCommand] Error removing cron " + id, e);
            return 0;
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int startCommand(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String id = StringArgumentType.getString(context, "id");

        QuartzConfig.CronEntry target = config.crons.stream().filter(c -> c.id.equals(id)).findFirst().orElse(null);
        if (target == null) {
            source.sendError(Text.literal(QUARTZ_TAG + Formatting.RED + "Cron " + Formatting.WHITE + id + Formatting.RED + " not found!"));
            return 0;
        }

        if (target.enabled) {
            source.sendError(Text.literal(QUARTZ_TAG + Formatting.RED + "Cron " + Formatting.WHITE + id + Formatting.RED + " is already enabled!"));
            return 0;
        }

        target.enabled = true;

        try {
            boolean started = QuartzManager.getInstance().startCron(target);
            config.saveAsync();
            source.sendFeedback(() -> Text.literal(QUARTZ_TAG + Formatting.GRAY + "Cron " + Formatting.WHITE + id + Formatting.GRAY + " started."), true);
            return started ? Command.SINGLE_SUCCESS : 0;
        } catch (Exception e) {
            target.enabled = false;
            source.sendError(Text.literal(QUARTZ_TAG + Formatting.RED + "Failed to start cron: " + e.getMessage()));
            LOGGER.error("[QuartzCommand] Error starting cron " + id, e);
            return 0;
        }
    }

    private static int stopCommand(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String id = StringArgumentType.getString(context, "id");

        QuartzConfig.CronEntry target = config.crons.stream().filter(c -> c.id.equals(id)).findFirst().orElse(null);
        if (target == null) {
            source.sendError(Text.literal(QUARTZ_TAG + Formatting.RED + "Cron " + Formatting.WHITE + id + Formatting.RED + " not found!"));
            return 0;
        }

        if (!target.enabled) {
            source.sendError(Text.literal(QUARTZ_TAG + Formatting.RED + "Cron " + Formatting.WHITE + id + Formatting.RED + " is already stopped!"));
            return 0;
        }

        target.enabled = false;

        try {
            QuartzManager.getInstance().stopCron(id);
            config.saveAsync();
            source.sendFeedback(() -> Text.literal(QUARTZ_TAG + Formatting.GRAY + "Cron " + Formatting.WHITE + id + Formatting.GRAY + " stopped."), true);
        } catch (Exception e) {
            target.enabled = true;
            source.sendError(Text.literal(QUARTZ_TAG + Formatting.RED + "Failed to stop cron: " + e.getMessage()));
            LOGGER.error("[QuartzCommand] Error stopping cron " + id, e);
            return 0;
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int detailsCommand(CommandContext<ServerCommandSource> context) {
        String id = StringArgumentType.getString(context, "id");
        ServerCommandSource source = context.getSource();

        QuartzConfig.CronEntry target = config.crons.stream().filter(c -> c.id.equals(id)).findFirst().orElse(null);
        if (target == null) {
            source.sendError(Text.literal(QUARTZ_TAG + Formatting.RED + "Cron " + Formatting.WHITE + id + Formatting.RED + " not found!"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal(QUARTZ_TAG + Formatting.GRAY + id + " details:"), false);
        source.sendFeedback(() -> Text.literal("  " + Formatting.GRAY + "Status: " + (target.enabled ? Formatting.GREEN + "Enabled" : Formatting.RED + "Disabled")), false);
        source.sendFeedback(() -> Text.literal("  " + Formatting.GRAY + "Quartz Pattern: " + Formatting.WHITE + target.schedule), false);
        source.sendFeedback(() -> Text.literal("  " + Formatting.GRAY + "Command: " + Formatting.WHITE + target.command), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int statusCommand(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        QuartzManager.QuartzManagerStats stats = QuartzManager.getInstance().getStats();

        long enabledCount = config.crons.stream().filter(c -> c.enabled).count();
        long totalCount = config.crons.size();

        source.sendFeedback(() -> Text.literal(QUARTZ_TAG + Formatting.GRAY + "Status:"), false);
        source.sendFeedback(() -> Text.literal("  " + Formatting.GRAY + "Scheduler: " + (stats.isRunning ? Formatting.GREEN + "Running" : Formatting.RED + "Stopped")), false);
        source.sendFeedback(() -> Text.literal("  " + Formatting.GRAY + "Total crons: " + Formatting.WHITE + totalCount), false);
        source.sendFeedback(() -> Text.literal("  " + Formatting.GRAY + "Enabled: " + Formatting.GREEN + enabledCount + Formatting.WHITE + "/" + totalCount + " cron(s)"), false);

        return Command.SINGLE_SUCCESS;
    }
}
