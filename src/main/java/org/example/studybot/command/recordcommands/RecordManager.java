package org.example.studybot.command.recordcommands;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
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

    /**
     * 기간(label, range) + (옵션) 사용자이름 기준으로 로그를 조회하고 포맷팅
     */
    private String formatLogsByRange(String label, List<LocalDateTime> range, Optional<String> userNameOpt) {
        LocalDateTime start = range.get(0);
        LocalDateTime end = range.get(1);

        // 1) 우선 해당 기간의 전체 로그를 가져온다.
        List<VoiceChannelLog> logs = repository.findAllLogsBetween(start, end);

        // 2) userNameOpt 가 들어온 경우, "서버별명" 기준으로 필터링
        if (userNameOpt.isPresent()) {
            String targetName = userNameOpt.get();

            logs = logs.stream()
                .filter(log -> {
                    String key = Optional.ofNullable(log.getNickName())
                        .filter(s -> !s.isBlank())
                        .orElse(log.getUserName());   // 서버별명 없으면 계정 이름

                    return key.equals(targetName);
                })
                .collect(Collectors.toList());
        }

        // 3) 기간 타입에 따라 다른 포맷 적용
        if ("주간".equals(label)) {
            return formatWeeklySummary(logs, label, userNameOpt, range);
        } else if ("월간".equals(label)) {
            return formatMonthlySummary(logs, label, userNameOpt, range);
        } else {
            // 일간 / 기타
            return formatDailySummary(logs, label, userNameOpt);
        }
    }

    // ===================== 일간 요약 =====================

    private String formatDailySummary(List<VoiceChannelLog> logs, String periodName, Optional<String> userNameOpt) {
        if (logs.isEmpty()) {
            return "⚠️ " + periodName + " 기간 동안 기록이 없습니다.";
        }

        Map<String, Long> userDurations = logs.stream()
            .collect(Collectors.groupingBy(
                log -> Optional.ofNullable(log.getNickName())
                    .filter(s -> !s.isBlank())
                    .orElse(log.getUserName()),
                Collectors.summingLong(VoiceChannelLog::getDuration)
            ));

        // 개인 조회 + 1명만 있는 경우
        if (userNameOpt.isPresent() && userDurations.size() == 1) {
            String user = userDurations.keySet().iterator().next();
            long totalSeconds = userDurations.get(user);

            String header = String.format("📊 **%s 내 공부 기록 요약**\n\n", periodName);
            String body = String.format(
                "%s님 — %s",
                user,
                prettyDuration(totalSeconds)
            );
            return header + body;
        }

        // 전체 조회: 사용자별 기록
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("📊 **%s 전체 공부 기록 요약**\n\n", periodName));
        sb.append("────────────────────────\n");

        userDurations.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .forEach(entry -> {
                String user = entry.getKey();
                long totalSeconds = entry.getValue();

                sb.append(String.format(
                    "• %s님 — %s\n",
                    user,
                    prettyDuration(totalSeconds)
                ));
            });

        sb.append("────────────────────────");
        return sb.toString();
    }

    // ===================== 주간 요약 (요일별 + 합계) =====================

    private String formatWeeklySummary(List<VoiceChannelLog> logs, String periodName, Optional<String> userNameOpt,
        List<LocalDateTime> range) {
        if (logs.isEmpty()) {
            return "⚠️ " + periodName + " 기간 동안 기록이 없습니다.";
        }

        // user → (DayOfWeek → duration)
        Map<String, Map<DayOfWeek, Long>> userDayDurations = new HashMap<>();

        for (VoiceChannelLog log : logs) {
            String user = Optional.ofNullable(log.getNickName())
                .filter(s -> !s.isBlank())
                .orElse(log.getUserName());

            LocalDate date = log.getRecordedAt().toLocalDate();
            DayOfWeek dow = date.getDayOfWeek();

            userDayDurations
                .computeIfAbsent(user, k -> new HashMap<>())
                .merge(dow, log.getDuration(), Long::sum);
        }

        if (userDayDurations.isEmpty()) {
            return "⚠️ " + periodName + " 기간 동안 기록이 없습니다.";
        }

        // 사용자별 총합
        Map<String, Long> userTotals = new HashMap<>();
        long grandTotal = 0L;
        for (Map.Entry<String, Map<DayOfWeek, Long>> entry : userDayDurations.entrySet()) {
            long sum = entry.getValue().values().stream()
                .mapToLong(Long::longValue)
                .sum();
            userTotals.put(entry.getKey(), sum);
            grandTotal += sum;
        }

        boolean personal = userNameOpt.isPresent() && userTotals.size() == 1;

        // 기준 날짜 표시 (range 기반)
        LocalDate startDate = range.get(0).toLocalDate();
        LocalDate endDate = range.get(1).toLocalDate();
        String dateRange = String.format("기준: %s ~ %s",
            startDate.format(DATE_FMT), endDate.format(DATE_FMT));

        String title = personal
            ? "📊 **주간 내 공부 기록 요약**\n" + dateRange + "\n\n"
            : "📊 **주간 전체 공부 기록 요약**\n" + dateRange + "\n\n";

        StringBuilder sb = new StringBuilder(title);

        // 사용자 정렬 (총합 내림차순)
        userTotals.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .forEach(entry -> {
                String user = entry.getKey();
                long total = entry.getValue();
                Map<DayOfWeek, Long> days = userDayDurations.get(user);

                sb.append(String.format("%s님\n", user));

                // 월~일 순서대로 출력 (해당 요일 기록 있는 경우만)
                for (DayOfWeek dow : WEEK_ORDER) {
                    Long sec = days.get(dow);
                    if (sec == null || sec == 0L) continue;

                    sb.append(String.format(
                        "  - %s: %s\n",
                        dayLabel(dow),
                        prettyDuration(sec)
                    ));
                }

                sb.append(String.format("  합계: %s\n\n", prettyDuration(total)));
            });

        sb.append(String.format("📌 전체 합계: %s", prettyDuration(grandTotal)));

        return sb.toString();
    }

    // ===================== 월간 요약 (주차별 + 합계) =====================

    private String formatMonthlySummary(List<VoiceChannelLog> logs, String periodName,
        Optional<String> userNameOpt, List<LocalDateTime> range) {
        if (logs.isEmpty()) {
            return "⚠️ " + periodName + " 기간 동안 기록이 없습니다.";
        }

        // user → (weekIndex → duration)
        Map<String, Map<Integer, Long>> userWeekDurations = new HashMap<>();

        for (VoiceChannelLog log : logs) {
            String user = Optional.ofNullable(log.getNickName())
                .filter(s -> !s.isBlank())
                .orElse(log.getUserName());

            LocalDate date = log.getRecordedAt().toLocalDate();
            int dayOfMonth = date.getDayOfMonth();
            int weekIndex = (dayOfMonth - 1) / 7 + 1; // 1~7:1주차, 8~14:2주차 ...

            userWeekDurations
                .computeIfAbsent(user, k -> new HashMap<>())
                .merge(weekIndex, log.getDuration(), Long::sum);
        }

        if (userWeekDurations.isEmpty()) {
            return "⚠️ " + periodName + " 기간 동안 기록이 없습니다.";
        }

        // 사용자별 총합
        Map<String, Long> userTotals = new HashMap<>();
        for (Map.Entry<String, Map<Integer, Long>> entry : userWeekDurations.entrySet()) {
            long sum = entry.getValue().values().stream()
                .mapToLong(Long::longValue)
                .sum();
            userTotals.put(entry.getKey(), sum);
        }

        boolean personal = userNameOpt.isPresent() && userTotals.size() == 1;

        // 기준 날짜 표시 (월 전체)
        LocalDate startDate = range.get(0).toLocalDate();
        LocalDate endDate = range.get(1).toLocalDate();
        String dateRange = String.format("기준: %s ~ %s",
            startDate.format(DATE_FMT), endDate.format(DATE_FMT));

        String title = personal
            ? "📊 **월간 내 공부 기록 요약**\n" + dateRange + "\n\n"
            : "📊 **월간 전체 공부 기록 요약**\n" + dateRange + "\n\n";

        StringBuilder sb = new StringBuilder(title);

        // 사용자 정렬 (총합 내림차순)
        userTotals.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .forEach(entry -> {
                String user = entry.getKey();
                long total = entry.getValue();
                Map<Integer, Long> weeks = userWeekDurations.get(user);

                sb.append(String.format("%s님\n", user));

                weeks.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(weekEntry -> {
                        int weekIndex = weekEntry.getKey();
                        long sec = weekEntry.getValue();
                        sb.append(String.format(
                            "  - %d주차: %s\n",
                            weekIndex,
                            prettyDuration(sec)
                        ));
                    });

                sb.append(String.format("  합계: %s\n\n", prettyDuration(total)));
            });

        // 📌 월간에서는 전체 합계 출력 X (요청 반영)

        return sb.toString();
    }

    // ===================== 공통 유틸 =====================

    /**
     * 총 초(second)를 "X시간 Y분 Z초" 형태로 예쁘게 변환
     */
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

    // 요일 한글 라벨
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
        LocalDate start = LocalDate.now().with(DayOfWeek.MONDAY);
        return List.of(
            start.atStartOfDay(),
            start.plusDays(6).atTime(23, 59, 59)
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
