package org.example.studybot.schedule;

import org.example.studybot.util.DailySummaryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogScheduler {
    private final DailySummaryService dailySummaryService;

    @Scheduled(cron = "*/10 * * * * *")
    // @Scheduled(cron = "0 1 0 * * *")
    public void sendDailySummary() {
        log.info(">>> [LogScheduler] sendDailySummary 호출됨");
        dailySummaryService.generateAndSendDailySummary();
    }
}
