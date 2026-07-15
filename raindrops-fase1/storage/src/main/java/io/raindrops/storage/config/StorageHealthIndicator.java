package io.raindrops.storage.config;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.nio.file.FileStore;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class StorageHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(StorageHealthIndicator.class);

    @Override
    public Health health() {
        try {
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
            FileStore store = Paths.get(".").toRealPath().getFileSystem().getFileStores().iterator().next();

            long usedMemory = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
            long maxMemory = memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024);
            long uptimeHours = runtimeBean.getUptime() / (1000 * 60 * 60);
            long freeDiskMB = store.getUsableSpace() / (1024 * 1024);
            long totalDiskMB = store.getTotalSpace() / (1024 * 1024);

            double memoryUsagePercent = maxMemory > 0 ? (double) usedMemory / maxMemory * 100 : 0;
            double diskUsagePercent = totalDiskMB > 0 ? (double) (totalDiskMB - freeDiskMB) / totalDiskMB * 100 : 0;

            Health.Builder builder = Health.up()
                .withDetail("node", System.getenv().getOrDefault("NODE_NAME", "keeper-default"))
                .withDetail("memory", usedMemory + "MB / " + maxMemory + "MB (" + String.format("%.1f", memoryUsagePercent) + "%)")
                .withDetail("disk", (totalDiskMB - freeDiskMB) + "MB / " + totalDiskMB + "MB (" + String.format("%.1f", diskUsagePercent) + "%)")
                .withDetail("uptime", uptimeHours + "h")
                .withDetail("pid", ManagementFactory.getRuntimeMXBean().getPid());

            // Warn if memory or disk usage is high
            if (memoryUsagePercent > 85 || diskUsagePercent > 90) {
                builder = Health.status("WARNING")
                    .withDetail("node", System.getenv().getOrDefault("NODE_NAME", "keeper-default"))
                    .withDetail("memory", usedMemory + "MB / " + maxMemory + "MB (" + String.format("%.1f", memoryUsagePercent) + "%)")
                    .withDetail("disk", (totalDiskMB - freeDiskMB) + "MB / " + totalDiskMB + "MB (" + String.format("%.1f", diskUsagePercent) + "%)")
                    .withDetail("uptime", uptimeHours + "h")
                    .withDetail("pid", ManagementFactory.getRuntimeMXBean().getPid())
                    .withDetail("warning", memoryUsagePercent > 85 ? "High memory usage" : "High disk usage");
            }

            return builder.build();
        } catch (Exception e) {
            log.error("Health check failed", e);
            return Health.down().withDetail("error", e.getMessage()).build();
        }
    }
}
