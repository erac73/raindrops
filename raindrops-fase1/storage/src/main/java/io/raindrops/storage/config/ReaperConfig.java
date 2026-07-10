package io.raindrops.storage.config;

import io.raindrops.storage.service.DropService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
public class ReaperConfig {

    private static final Logger log = LoggerFactory.getLogger(ReaperConfig.class);

    private final DropService dropService;

    public ReaperConfig(DropService dropService) {
        this.dropService = dropService;
    }

    @Scheduled(fixedRate = 60000)
    public void reapExpiredDrops() {
        log.debug("Running TTL reaper...");
        dropService.reapExpiredDrops();
    }
}
