package com.captainziboo.quartz4mc.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class QuartzConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("quartz4mc-config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File("config/quartz4mc.json");

    public int minPermissionLevel = 2;
    public List<CronEntry> crons = new ArrayList<>();

    public static class CronEntry {
        public String id;
        public String schedule;
        public String command;
        public boolean enabled = true;
        public String uuid = UUID.randomUUID().toString();
    }

    public static QuartzConfig load() {
        if (!CONFIG_FILE.exists()) {
            LOGGER.debug("[QuartzConfig] Configuration not found. Creating default config...");
            QuartzConfig config = createDefault();
            config.save();
            return config;
        }

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            Type type = new TypeToken<QuartzConfig>(){}.getType();
            QuartzConfig config = GSON.fromJson(reader, type);
            if (config == null) {
                LOGGER.warn("[QuartzConfig] Configuration invalid. Recreating default config...");
                config = createDefault();
                config.save();
            }
            return config;
        } catch (IOException e) {
            LOGGER.error("[QuartzConfig] Error reading config: {}", e.getMessage(), e);
            QuartzConfig fallback = createDefault();
            fallback.save();
            return fallback;
        }
    }

    public void save() {
        try {
            if (!CONFIG_FILE.getParentFile().exists()) CONFIG_FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(this, writer);
            }
            LOGGER.debug("[QuartzConfig] Configuration saved successfully to {}", CONFIG_FILE.getPath());
        } catch (IOException e) {
            LOGGER.error("[QuartzConfig] Failed to save configuration: {}", e.getMessage(), e);
        }
    }

    public void saveAsync() {
        CompletableFuture.runAsync(() -> {
            try {
                save();
            } catch (Exception e) {
                LOGGER.error("[QuartzConfig] Async save failed: {}", e.getMessage(), e);
            }
        });
    }

    private static QuartzConfig createDefault() {
        QuartzConfig config = new QuartzConfig();
        CronEntry example = new CronEntry();
        example.id = "example_broadcast";
        example.schedule = "0 * * * * ?"; // Quartz 6-fields format
        example.command = "tellraw @a [{\"text\":\"[QuartzConfig] \",\"color\":\"light_purple\"}, {\"text\":\"Edit \", \"color\": \"gray\"}, {\"text\":\"config/quartz4mc.json\",\"color\":\"white\"}, {\"text\":\" to disable this default cron.\",\"color\":\"gray\"}]";
        config.crons.add(example);
        return config;
    }

    public CronEntry getCronById(String id) {
        return crons.stream().filter(c -> c.id.equals(id)).findFirst().orElse(null);
    }
}
