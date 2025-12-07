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
import java.util.Random;
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

    // ===================== 이모지 관련 설정 =====================

    // 자동 배정용 이모지 풀
    private static final List<String> EMOJI_POOL = List.of(
        "🐳", "🐰", "🐯", "🐼", "🐨", "🐧", "🦁", "🐻", "🐶", "🐱", "🦊", "🐸"
    );

    // 자동 배정된 유저 → 이모지 저장 (봇이 켜져 있는 동안 유지)
    private static final Map<String, String> AUTO_ASSIGNED = new HashMap<>();

    private final Random random = new Random();

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
            return targetDate.format(DateTimeFormatter.ofPattern("MM/dd")) + "에 기록이 없습니다.";
        }

        // 특정 날짜는 "전체" 기준으로 요약
        String label = targetDate.format(DateTimeFormatter.ofPattern("MM/dd"));
        return formatLogsSummed(logs, label, Optional.empty());
    }

    // ===================== 메인 포맷팅 로직 =====================

    /**
     * 기간(label, range) + (옵션) 사용자이름 기준으로 로그를 조회하고 예쁘게 포맷팅
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
                    // nickName 이 비어있으면 userName 사용
                    String key = Optional.ofNullable(log.getNickName())
                        .filter(s -> !s.isBlank())
                        .orElse(log.getUserName());

                    // key 가 서버별명과 동일한지 비교
                    return key.equals(targetName);
                })
                .collect(Collectors.toList());
        }

        return formatLogsSummed(logs, label, userNameOpt);
    }

    /**
     * 로그 리스트를 "유저별 총합"으로 합산해서
     * - 개인 조회: 예) 📊 **주간 내 공부 기록 요약**
     * - 전체 조회: 예) 📊 **주간 전체 공부 기록 요약**
     * 형태로 문자열 생성
     */
    private String formatLogsSummed(List<VoiceChannelLog> logs, String periodName, Optional<String> userNameOpt) {
        if (logs.isEmpty()) {
            return "⚠️ " + periodName + " 기간 동안 기록이 없습니다.";
        }

        // nickName 이 null/빈 문자열이면 userName으로 대체해서 그룹핑
        Map<String, Long> userDurations = logs.stream()
            .collect(Collectors.groupingBy(
                log -> {
                    String key = Optional.ofNullable(log.getNickName())
                        .filter(s -> !s.isBlank())
                        .orElse(log.getUserName());   // ✅ fallback
                    return key;
                },
                Collectors.summingLong(VoiceChannelLog::getDuration)
            ));

        // 개인 조회인지, 전체 조회인지에 따라 출력 형태 분기
        if (userNameOpt.isPresent() && userDurations.size() == 1) {
            // 🔹 개인 기록: 한 사람만 남아 있는 경우
            String user = userDurations.keySet().iterator().next();
            long totalSeconds = userDurations.get(user);

            String header = String.format("📊 **%s 내 공부 기록 요약**\n\n", periodName);

            String emoji = getEmojiForUser(user);
            String body = String.format(
                "• %s %s님 — %s\n",
                emoji,
                user,
                prettyDuration(totalSeconds)
            );

            return header + body;
        }

        // 🔹 전체 기록 (또는 여러 명인 경우)
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("📊 **%s 전체 공부 기록 요약**\n\n", periodName));
        sb.append("‍🤝‍사용자별 기록\n");
        sb.append("────────────────────────\n");

        userDurations.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .forEach(entry -> {
                String user = entry.getKey();
                long totalSeconds = entry.getValue();
                String emoji = getEmojiForUser(user);

                sb.append(String.format(
                    "• %s %s님 — %s\n",
                    emoji,
                    user,
                    prettyDuration(totalSeconds)
                ));
            });

        sb.append("────────────────────────");
        // 📌 "전체 합계" 줄은 넣지 않음 (요청사항 반영)

        return sb.toString();
    }

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

    // ===================== 이모지 유틸 =====================

    /**
     * 사용자 이름에 대응하는 이모지를 가져온다.
     * - 이미 배정된 이모지가 있으면 재사용
     * - 없으면 EMOJI_POOL 에서 랜덤 선택 후 AUTO_ASSIGNED 에 저장
     */
    private String getEmojiForUser(String user) {
        // 1) 자동 배정된 이모지가 이미 있는지 확인
        if (AUTO_ASSIGNED.containsKey(user)) {
            return AUTO_ASSIGNED.get(user);
        }

        // 2) 없으면 새로 랜덤 배정
        String newEmoji = EMOJI_POOL.get(random.nextInt(EMOJI_POOL.size()));
        AUTO_ASSIGNED.put(user, newEmoji);

        return newEmoji;
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
