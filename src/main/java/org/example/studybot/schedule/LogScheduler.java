package org.example.studybot.schedule;

import org.example.studybot.util.summary.DailySummaryService;
import org.example.studybot.util.summary.MonthSummaryService;
import org.example.studybot.util.summary.WeekSummaryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogScheduler {
    private final DailySummaryService dailySummaryService;
    private final WeekSummaryService weekSummaryService;
    private final MonthSummaryService monthSummaryService;


    @Scheduled(cron = "0 1 0 * * *")
    public void sendDailySummary() {
        log.info(">>> [LogScheduler] sendDailySummary 호출됨");
        dailySummaryService.generateAndSendDailySummary();
    }

    @Scheduled(cron = "0 2 0 * * MON")
    public void sendWeeklySummary() {
        log.info(">>> [LogScheduler] sendWeeklySummary 호출됨");
        weekSummaryService.generateAndSendWeeklySummary();
    }

    // 매월 1일 00:01에 "지난 달" 통계 전송
    @Scheduled(cron = "0 3 0 1 * *")
    public void sendMonthlySummary() {
        log.info(">>> [LogScheduler] sendMonthlySummary 호출됨");
        monthSummaryService.generateAndSendMonthlySummary();
    }
}
