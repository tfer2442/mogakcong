package org.example.studybot.command.recordcommands;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.stream.Collectors;

import org.example.studybot.voicechannel.VoiceChannelLog;
import org.example.studybot.voicechannel.VoiceChannelLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RecordManager {

    @Autowired
    private VoiceChannelLogRepository repository;

    // 요일 출력 순서 (월~일)
    private static final DayOfWeek[] WEEK_ORDER = {
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
        DayOfWeek.SUNDAY
    };

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM/dd");

    // 월요일 기준 주차 계산용
    private static final WeekFields WEEK_FIELDS = WeekFields.of(DayOfWeek.MONDAY, 1);

    // ===================== 공개 메서드 =====================

    public String getAllMonthlyLogs() {
        return formatLogsByRange("월간", getMonthRange(), Optional.empty());
    }

    public String getAllWeeklyLogs() {
        return formatLogsByRange("주간", getWeekRange(), Optional.empty());
    }

    public String getAllDailyLogs() {
        return formatLogsByRange("일간", getDayRange(), Optional.empty());
    }

    public String getMonthlyLogs(String userName) {
        return formatLogsByRange("월간", getMonthRange(), Optional.of(userName));
    }

    public String getWeeklyLogs(String userName) {
        return formatLogsByRange("주간", getWeekRange(), Optional.of(userName));
    }

    public String getDailyLogs(String userName) {
        return formatLogsByRange("일간", getDayRange(), Optional.of(userName));
    }

    // 특정 날짜(월/일) 기록 조회
    public String getLogsForSpecificDate(String datePart) {
        LocalDate targetDate;
        try {
            String[] parts = datePart.split("/");

            int month = Integer.parseInt(parts[0]);
            int day = Integer.parseInt(parts[1]);

            int currentYear = LocalDate.now().getYear();
            targetDate = LocalDate.of(currentYear, month, day);
        } catch (Exception e) {
            return "날짜 형식이 잘못되었습니다. 올바른 형식: MM/dd 또는 M/d (예: 12/25 또는 1/3)";
        }

        LocalDateTime startOfDay = targetDate.atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1).minusSeconds(1);

        List<VoiceChannelLog> logs = repository.findAllLogsBetween(startOfDay, endOfDay);
        if (logs.isEmpty()) {
            return targetDate.format(DATE_FMT) + "에 기록이 없습니다.";
        }

        String label = targetDate.format(DATE_FMT);
        return formatDailySummary(logs, label, Optional.empty());
    }

    // ===================== 메인 포맷팅 로직 =====================

    private String formatLogsByRange(String periodLabel, List<LocalDateTime> range, Optional<String> userNameOpt) {
        LocalDateTime start = range.get(0);
        LocalDateTime end = range.get(1);

        List<VoiceChannelLog> logs = repository.findAllLogsBetween(start, end);

        if (userNameOpt.isPresent()) {
            String targetName = userNameOpt.get();

            logs = logs.stream()
                .filter(log -> resolveUserName(log).equals(targetName))
                .collect(Collectors.toList());
        }

        if ("주간".equals(periodLabel)) {
            return formatWeeklySummary(logs, periodLabel, userNameOpt, range);
        } else if ("월간".equals(periodLabel)) {
            return formatMonthlySummary(logs, periodLabel, userNameOpt, range);
        } else {
            return formatDailySummary(logs, periodLabel, userNameOpt);
        }
    }

    // ===================== 일간 요약 =====================

    private String formatDailySummary(List<VoiceChannelLog> logs, String periodLabel, Optional<String> userNameOpt) {
        if (logs.isEmpty()) {
            return "⚠️ " + periodLabel + " 기간 동안 기록이 없습니다.";
        }

        Map<String, Long> userDurations = logs.stream()
            .collect(Collectors.groupingBy(
                this::resolveUserName,
                Collectors.summingLong(VoiceChannelLog::getDuration)
            ));

        StringBuilder sb = new StringBuilder();

        // 개인 조회 + 1명만 있는 경우
        if (userNameOpt.isPresent() && userDurations.size() == 1) {
            String user = userDurations.keySet().iterator().next();
            long totalSeconds = userDurations.get(user);

            sb.append(String.format("📊 **%s 내 공부 기록 요약**\n\n", periodLabel));
            sb.append("**").append(user).append("**\n");
            sb.append("총 공부 시간: ")
                .append(prettyDuration(totalSeconds))
                .append("\n");

            return sb.toString();
        }

        // 전체 조회: 사람별 섹션
        sb.append(String.format("📊 **%s 전체 공부 기록 요약**\n\n", periodLabel));

        userDurations.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .forEach(entry -> {
                String user = entry.getKey();
                long totalSeconds = entry.getValue();

                sb.append("────────────────────────\n");
                sb.append("**").append(user).append("**\n");
                sb.append("총 공부 시간: ")
                    .append(prettyDuration(totalSeconds))
                    .append("\n\n");
            });

        sb.append("────────────────────────");
        return sb.toString();
    }

    // ===================== 주간 요약 (요일별 + 합계) =====================

    private String formatWeeklySummary(List<VoiceChannelLog> logs, String periodLabel, Optional<String> userNameOpt,
        List<LocalDateTime> range) {
        if (logs.isEmpty()) {
            return "⚠️ " + periodLabel + " 기간 동안 기록이 없습니다.";
        }

        // user → (DayOfWeek → duration)
        Map<String, Map<DayOfWeek, Long>> userDayDurations = new HashMap<>();

        for (VoiceChannelLog log : logs) {
            String user = resolveUserName(log);
            LocalDate date = log.getRecordedAt().toLocalDate();
            DayOfWeek dow = date.getDayOfWeek();

            userDayDurations
                .computeIfAbsent(user, k -> new HashMap<>())
                .merge(dow, log.getDuration(), Long::sum);
        }

        if (userDayDurations.isEmpty()) {
            return "⚠️ " + periodLabel + " 기간 동안 기록이 없습니다.";
        }

        Map<String, Long> userTotals = new HashMap<>();
        for (Map.Entry<String, Map<DayOfWeek, Long>> entry : userDayDurations.entrySet()) {
            long sum = entry.getValue().values().stream()
                .mapToLong(Long::longValue)
                .sum();
            userTotals.put(entry.getKey(), sum);
        }

        boolean personal = userNameOpt.isPresent() && userTotals.size() == 1;

        LocalDate startDate = range.get(0).toLocalDate();
        LocalDate endDate = range.get(1).toLocalDate();
        String dateRange = String.format("기준: %s ~ %s",
            startDate.format(DATE_FMT), endDate.format(DATE_FMT));

        String title = personal
            ? "📊 **주간 내 공부 기록 요약**\n" + dateRange + "\n\n"
            : "📊 **주간 전체 공부 기록 요약**\n" + dateRange + "\n\n";

        StringBuilder sb = new StringBuilder(title);

        userTotals.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .forEach(entry -> {
                String user = entry.getKey();
                long total = entry.getValue();
                Map<DayOfWeek, Long> days = userDayDurations.get(user);

                sb.append("────────────────────────\n");
                sb.append("**").append(user).append("**\n");

                for (DayOfWeek dow : WEEK_ORDER) {
                    Long sec = days.get(dow);
                    if (sec == null || sec == 0L) {
                        continue;
                    }

                    sb.append("• ")
                        .append(dayLabel(dow))
                        .append(": ")
                        .append(prettyDuration(sec))
                        .append("\n");
                }

                sb.append("\n합계: ")
                    .append(prettyDuration(total))
                    .append("\n\n");
            });

        sb.append("────────────────────────");
        return sb.toString();
    }

    // ===================== 월간 요약 (월요일 기준 주차별) =====================

    private String formatMonthlySummary(List<VoiceChannelLog> logs, String periodLabel,
        Optional<String> userNameOpt, List<LocalDateTime> range) {
        if (logs.isEmpty()) {
            return "⚠️ " + periodLabel + " 기간 동안 기록이 없습니다.";
        }

        // user → (weekIndex → duration)
        Map<String, Map<Integer, Long>> userWeekDurations = new HashMap<>();

        for (VoiceChannelLog log : logs) {
            String user = resolveUserName(log);

            LocalDate date = log.getRecordedAt().toLocalDate();
            int weekIndex = date.get(WEEK_FIELDS.weekOfMonth()); // 월요일 기준 주차

            userWeekDurations
                .computeIfAbsent(user, k -> new HashMap<>())
                .merge(weekIndex, log.getDuration(), Long::sum);
        }

        if (userWeekDurations.isEmpty()) {
            return "⚠️ " + periodLabel + " 기간 동안 기록이 없습니다.";
        }

        Map<String, Long> userTotals = new HashMap<>();
        for (Map.Entry<String, Map<Integer, Long>> entry : userWeekDurations.entrySet()) {
            long sum = entry.getValue().values().stream()
                .mapToLong(Long::longValue)
                .sum();
            userTotals.put(entry.getKey(), sum);
        }

        boolean personal = userNameOpt.isPresent() && userTotals.size() == 1;

        LocalDate startDate = range.get(0).toLocalDate();
        LocalDate endDate = range.get(1).toLocalDate();
        String dateRange = String.format("기준: %s ~ %s",
            startDate.format(DATE_FMT), endDate.format(DATE_FMT));

        String title = personal
            ? "📊 **월간 내 공부 기록 요약**\n" + dateRange + "\n\n"
            : "📊 **월간 전체 공부 기록 요약**\n" + dateRange + "\n\n";

        StringBuilder sb = new StringBuilder(title);

        userTotals.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .forEach(entry -> {
                String user = entry.getKey();
                long total = entry.getValue();
                Map<Integer, Long> weeks = userWeekDurations.get(user);

                sb.append("────────────────────────\n");
                sb.append("**").append(user).append("**\n\n");

                weeks.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(weekEntry -> {
                        int weekIndex = weekEntry.getKey();
                        long sec = weekEntry.getValue();

                        // 여기서 마크다운 리스트 대신 그냥 텍스트 bullet 사용
                        sb.append("• ")
                            .append(weekIndex)
                            .append("주차: ")
                            .append(prettyDuration(sec))
                            .append("\n");
                    });

                sb.append("\n합계: ")
                    .append(prettyDuration(total))
                    .append("\n\n");
            });

        sb.append("────────────────────────");
        return sb.toString();
    }

    // ===================== 공통 유틸 =====================

    private String resolveUserName(VoiceChannelLog log) {
        return Optional.ofNullable(log.getNickName())
            .filter(s -> !s.isBlank())
            .orElse(log.getUserName());
    }

    private String prettyDuration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%d시간 %d분 %d초", hours, minutes, seconds);
        }
        if (minutes > 0) {
            return String.format("%d분 %d초", minutes, seconds);
        }
        return String.format("%d초", seconds);
    }

    private String dayLabel(DayOfWeek dow) {
        switch (dow) {
            case MONDAY: return "월";
            case TUESDAY: return "화";
            case WEDNESDAY: return "수";
            case THURSDAY: return "목";
            case FRIDAY: return "금";
            case SATURDAY: return "토";
            case SUNDAY: return "일";
            default: return "";
        }
    }

    // ===================== 기간 구하기 유틸 =====================

    private List<LocalDateTime> getMonthRange() {
        LocalDate now = LocalDate.now();
        return List.of(
            now.with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay(),
            now.with(TemporalAdjusters.lastDayOfMonth()).atTime(23, 59, 59)
        );
    }

    private List<LocalDateTime> getWeekRange() {
        LocalDate now = LocalDate.now();
        // 월요일 기준으로 이번 주 시작일 계산
        LocalDate startOfWeek = now.minusDays(now.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
        LocalDate endOfWeek = startOfWeek.plusDays(6);

        return List.of(
            startOfWeek.atStartOfDay(),
            endOfWeek.atTime(23, 59, 59)
        );
    }

    private List<LocalDateTime> getDayRange() {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        return List.of(
            start,
            start.plusDays(1).minusSeconds(1)
        );
    }
}
