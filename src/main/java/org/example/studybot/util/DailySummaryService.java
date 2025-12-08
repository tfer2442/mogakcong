package org.example.studybot.util;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.example.studybot.voicechannel.VoiceChannelLog;
import org.example.studybot.voicechannel.VoiceChannelLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DailySummaryService {

    @Autowired
    private VoiceChannelLogRepository repository;

    @Autowired
    private JDA jda;

    @Autowired
    private TextChannelProperties textChannelProperties;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM/dd");

    /**
     * LogScheduler 에서 매일 0시 1분에 호출
     * 어제 하루(00:00 ~ 23:59:59) 기록을 집계해서
     * RecordManager 의 "전체 일간 기록" 과 같은 형식의 문자열로 전송
     */
    public void generateAndSendDailySummary() {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        LocalDateTime startOfDay = yesterday.atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1).minusSeconds(1);

        // Discord 채널 가져오기
        TextChannel textChannel = findTextChannel(textChannelProperties.getTargetChannelName());
        if (textChannel == null) {
            System.err.println("[DailySummaryService] 채널을 찾을 수 없습니다. name=" +
                textChannelProperties.getTargetChannelName());
            return;
        }

        // 어제의 로그 가져오기
        List<VoiceChannelLog> logs = repository.findAllLogsBetween(startOfDay, endOfDay);

        // 메시지 포맷: RecordManager 의 전체 일간 기록과 동일한 스타일
        String message = buildDailySummaryMessage(logs, yesterday);

        textChannel.sendMessage(message)
            .queue(
                success -> System.out.println("[DailySummaryService] 어제 일간 요약 전송 완료"),
                error -> System.err.println("[DailySummaryService] 어제 일간 요약 전송 실패: " + error.getMessage())
            );
    }

    /**
     * 어제 날짜 기준 전체 일간 기록 메시지 생성
     * RecordManager.formatDailySummary(...) 의 "전체 조회" 스타일과 동일하게 맞춤
     */
    private String buildDailySummaryMessage(List<VoiceChannelLog> logs, LocalDate targetDate) {
        String periodLabel = targetDate.format(DATE_FMT); // 예: 12/08

        if (logs == null || logs.isEmpty()) {
            // RecordManager.getLogsForSpecificDate 와 유사한 스타일
            return periodLabel + "에 기록이 없습니다.";
        }

        // userName → totalSeconds
        Map<String, Long> userDurations = new HashMap<>();

        for (VoiceChannelLog log : logs) {
            String user = resolveUserName(log);
            long duration = Optional.ofNullable(log.getDuration()).orElse(0L);

            userDurations.merge(user, duration, Long::sum);
        }

        if (userDurations.isEmpty()) {
            return periodLabel + "에 기록이 없습니다.";
        }

        StringBuilder sb = new StringBuilder();

        // 헤더: 📊 **{MM/dd} 전체 공부 기록 요약**
        sb.append(String.format("📊 **%s 전체 공부 기록 요약**\n\n", periodLabel));

        // 총 공부 시간 기준 내림차순 정렬
        userDurations.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .forEach(entry -> {
                String user = entry.getKey();
                long totalSeconds = entry.getValue();

                sb.append("────────────────────────\n");
                sb.append("**").append(user).append("**\n");
                sb.append("총 공부 시간: ")
                    .append(formatDuration(totalSeconds))
                    .append("\n\n");
            });

        sb.append("────────────────────────");

        return sb.toString();
    }

    /**
     * 서버별명(nickName) 이 있으면 그걸 쓰고,
     * 없거나 공백이면 userName 사용
     * (RecordManager.resolveUserName 과 동일한 로직)
     */
    private String resolveUserName(VoiceChannelLog log) {
        return Optional.ofNullable(log.getNickName())
            .filter(s -> !s.isBlank())
            .orElse(log.getUserName());
    }

    /**
     * 초 → "X시간 Y분 Z초" 포맷
     * (RecordManager.prettyDuration, 기존 formatDuration 과 동일 스타일)
     */
    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format("%d시간 %d분 %d초", hours, minutes, secs);
        }
        if (minutes > 0) {
            return String.format("%d분 %d초", minutes, secs);
        }
        return String.format("%d초", secs);
    }

    private TextChannel findTextChannel(String nameOrId) {
        if (nameOrId == null || nameOrId.isBlank()) {
            return null;
        }

        // 1) 숫자로만 이루어진 경우에만 "ID" 로 시도
        if (nameOrId.chars().allMatch(Character::isDigit)) {
            TextChannel byId = jda.getTextChannelById(nameOrId);
            if (byId != null) {
                return byId;
            }
        }

        // 2) 그 외에는 "이름" 으로 검색
        return jda.getTextChannelsByName(nameOrId, true).stream()
            .findFirst()
            .orElse(null);
    }
}
