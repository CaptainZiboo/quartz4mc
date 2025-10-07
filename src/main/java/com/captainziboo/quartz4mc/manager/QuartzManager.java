package com.captainziboo.quartz4mc.manager;

import com.captainziboo.quartz4mc.Quartz4MC;
import com.captainziboo.quartz4mc.config.QuartzConfig;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class QuartzManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("cron4mc-manager");
    private static QuartzManager instance;
    private Scheduler scheduler;
    private final Map<String, JobKey> scheduledJobs;
    private MinecraftServer server;
    private volatile boolean isRunning = false;

    private QuartzManager() {
        this.scheduledJobs = new ConcurrentHashMap<>();
        try {
            this.scheduler = StdSchedulerFactory.getDefaultScheduler();
            LOGGER.debug("[QuartzManager] Quartz Scheduler initialized");
        } catch (SchedulerException e) {
            LOGGER.error("[QuartzManager] Failed to create Quartz scheduler", e);
        }
    }

    public static synchronized QuartzManager getInstance() {
        if (instance == null) instance = new QuartzManager();
        return instance;
    }

    public void initialize(MinecraftServer server) {
        this.server = server;
        if (!isRunning && scheduler != null) {
            try {
                scheduler.start();
                isRunning = true;
                LOGGER.debug("[QuartzManager] Quartz Scheduler started");
            } catch (SchedulerException e) {
                LOGGER.error("[QuartzManager] Failed to start Quartz scheduler", e);
            }
        }
    }

    public void shutdown() {
        if (isRunning && scheduler != null) {
            try {
                scheduler.shutdown(true);
                scheduledJobs.clear();
                isRunning = false;
                LOGGER.debug("[QuartzManager] Quartz Scheduler stopped");
            } catch (SchedulerException e) {
                LOGGER.error("[QuartzManager] Error stopping Quartz scheduler", e);
            }
        }
    }

    public int loadAndStartEnabledCrons(QuartzConfig config) {
        stopAllCrons();
        int loaded = 0;
        for (QuartzConfig.CronEntry entry : config.crons) {
            if (entry != null && entry.enabled) {
                try {
                    if (startCron(entry)) loaded++;
                } catch (Exception e) {
                    LOGGER.error("[QuartzManager] Failed to start cron '{}' during load: {}", entry.id, e.getMessage(), e);
                }
            }
        }
        return loaded;
    }

    private void stopAllCrons() {
        // Safe iteration over snapshot of keys
        for (String id : scheduledJobs.keySet().toArray(new String[0])) {
            stopCron(id);
        }
    }

    public boolean startCron(QuartzConfig.CronEntry entry) {
        if (!isRunning || scheduler == null) return false;

        // Stop existing job if present
        if (scheduledJobs.containsKey(entry.id)) stopCron(entry.id);

        try {
            CronExpression.validateExpression(entry.schedule);
            JobDetail job = JobBuilder.newJob(MinecraftCommandJob.class)
                    .withIdentity(entry.id, "quartz4mc")
                    .usingJobData("command", entry.command)
                    .usingJobData("cronId", entry.id)
                    .build();

            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(entry.id + "_trigger", "quartz4mc")
                    .withSchedule(CronScheduleBuilder.cronSchedule(entry.schedule)
                            .withMisfireHandlingInstructionDoNothing())
                    .build();

            scheduler.scheduleJob(job, trigger);
            scheduledJobs.put(entry.id, job.getKey());
            LOGGER.debug("[QuartzManager] Started cron '{}'", entry.id);
            return true;
        } catch (Exception e) {
            LOGGER.error("[QuartzManager] Failed to start cron '{}': {}", entry.id, e.getMessage(), e);
            return false;
        }
    }

    public boolean stopCron(String id) {
        JobKey key = scheduledJobs.remove(id);
        if (key != null && scheduler != null) {
            try {
                scheduler.deleteJob(key);
                LOGGER.debug("[QuartzManager] Stopped cron '{}'", id);
                return true;
            } catch (SchedulerException e) {
                LOGGER.error("[QuartzManager] Error stopping cron '{}': {}", id, e.getMessage(), e);
                return false;
            }
        }
        return false;
    }

    public boolean isCronScheduled(String id) {
        return scheduledJobs.containsKey(id);
    }

    public QuartzManagerStats getStats() {
        Map<String, String> keys = new ConcurrentHashMap<>();
        scheduledJobs.forEach((id, key) -> keys.put(id, key.toString()));
        return new QuartzManagerStats(isRunning, scheduledJobs.size(), keys);
    }

    public static class MinecraftCommandJob implements Job {
        private static final Logger LOGGER = LoggerFactory.getLogger("quartz4mc-job");
        private static final Map<String, Integer> failureCounts = new ConcurrentHashMap<>();
        private static final int MAX_FAILURES = 5;

        @Override
        public void execute(JobExecutionContext context) {
            JobDataMap data = context.getJobDetail().getJobDataMap();
            String command = data.getString("command");
            String cronId = data.getString("cronId");
            MinecraftServer server = QuartzManager.getInstance().server;

            if (server == null) {
                LOGGER.error("[QuartzJob] Server null for cron '{}'", cronId);
                return;
            }

            server.execute(() -> {
                try {
                    server.getCommandManager().executeWithPrefix(server.getCommandSource(), command);
                    LOGGER.debug("[QuartzJob] Executed cron '{}'", cronId);
                    failureCounts.put(cronId, 0); // reset après succès
                } catch (Exception e) {
                    int failures = failureCounts.compute(cronId, (k, v) -> v == null ? 1 : v + 1);
                    LOGGER.error("[QuartzJob] Failed cron '{}': {} (failure {}/{})", cronId, e.getMessage(), failures, MAX_FAILURES, e);

                    if (failures >= MAX_FAILURES) {
                        QuartzConfig config = Quartz4MC.getInstance().getConfig();
                        QuartzConfig.CronEntry entry = config.getCronById(cronId);
                        if (entry != null) {
                            entry.enabled = false;
                            config.saveAsync(); // async persistance
                            QuartzManager.getInstance().stopCron(cronId);
                            LOGGER.warn("[QuartzJob] Cron '{}' disabled after {} consecutive failures", cronId, MAX_FAILURES);
                        }
                    }
                }
            });
        }
    }

    public static class QuartzManagerStats {
        public final boolean isRunning;
        public final int scheduledCronsCount;
        public final Map<String, String> scheduledCrons;

        public QuartzManagerStats(boolean running, int count, Map<String, String> map) {
            this.isRunning = running;
            this.scheduledCronsCount = count;
            this.scheduledCrons = map;
        }
    }
}
