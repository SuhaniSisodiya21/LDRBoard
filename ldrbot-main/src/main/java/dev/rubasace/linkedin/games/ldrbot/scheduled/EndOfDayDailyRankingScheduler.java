package dev.rubasace.linkedin.games.ldrbot.scheduled;

import dev.rubasace.linkedin.games.ldrbot.configuration.ExecutorsConfiguration;
import dev.rubasace.linkedin.games.ldrbot.ranking.DailyRankingRecalculationService;
import dev.rubasace.linkedin.games.ldrbot.util.LinkedinTimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class EndOfDayDailyRankingScheduler implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(EndOfDayDailyRankingScheduler.class);

    private final DailyRankingRecalculationService dailyRankingRecalculationService;

    EndOfDayDailyRankingScheduler(final DailyRankingRecalculationService dailyRankingRecalculationService) {
        this.dailyRankingRecalculationService = dailyRankingRecalculationService;
    }

    @Scheduled(cron = "30 0 0 * * *", zone = LinkedinTimeUtils.LINKEDIN_ZONE, scheduler = ExecutorsConfiguration.SCHEDULED_TASKS_EXECUTOR_NAME)
    public void calculateMissingRankings() {
        dailyRankingRecalculationService.calculateMissingRankings();
    }

    @Override
    public void onApplicationEvent(final ApplicationReadyEvent event) {
        try {
            this.dailyRankingRecalculationService.calculateMissingRankings();
        } catch (Exception e) {
            LOGGER.error("Failed to calculate missing rankings at startup", e);
        }
    }
}
