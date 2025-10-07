package com.captainziboo.quartz4mc;

import com.captainziboo.quartz4mc.command.QuartzCommands;
import com.captainziboo.quartz4mc.config.QuartzConfig;
import com.captainziboo.quartz4mc.manager.QuartzManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Quartz4MC implements ModInitializer {
    public static final String MOD_ID = "quartz4mc";
    public static final String MOD_NAME = "Quartz4MC";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static Quartz4MC instance;
    private QuartzConfig config;
    private QuartzManager quartzManager;

    public Quartz4MC() {
        instance = this;
    }

    public static Quartz4MC getInstance() {
        return instance;
    }

    @Override
    public void onInitialize() {
        LOGGER.debug("[{}] Initialization started...", MOD_NAME);

        // Load config
        config = QuartzConfig.load();

        // Initialize QuartzManager
        quartzManager = QuartzManager.getInstance();

        // Register commands
        QuartzCommands.register(config);

        // Server lifecycle events
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            LOGGER.debug("[{}] Server started. Initializing Quartz manager...", MOD_NAME);
            quartzManager.initialize(server);

            // Load and start enabled crons
            try {
                int loadedCrons = quartzManager.loadAndStartEnabledCrons(config);
                LOGGER.debug("[{}] Loaded {} enabled cron(s)", MOD_NAME, loadedCrons);

                for (QuartzConfig.CronEntry entry : config.crons) {
                    String status = entry.enabled ? "ENABLED" : "DISABLED";
                    String scheduled = quartzManager.isCronScheduled(entry.id) ? "SCHEDULED" : "NOT_SCHEDULED";
                    LOGGER.debug(" - [{}|{}] {} | Pattern: {} | Command: {}", 
                            status, scheduled, entry.id, entry.schedule, entry.command);
                }
            } catch (Exception e) {
                LOGGER.error("[{}] Error initializing crons: {}", MOD_NAME, e.getMessage(), e);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LOGGER.debug("[{}] Server stopping. Shutting down cron manager...", MOD_NAME);
            quartzManager.shutdown();
        });

        LOGGER.debug("[{}] Initialization complete.", MOD_NAME);
    }

    public QuartzConfig getConfig() { return config; }
    public QuartzManager getQuartzManager() { return quartzManager; }
}
