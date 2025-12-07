package org.example.studybot.util;

import org.example.studybot.voicechannel.VoiceChannelLog;
import org.example.studybot.voicechannel.VoiceChannelLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    public void generateAndSendDailySummary() {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        // 어제 날짜의 시작과 끝 계산
        LocalDateTime startOfDay = yesterday.atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1).minusSeconds(1);

        // Discord 채널 가져오기
        TextChannel textChannel = findTextChannel(textChannelProperties.getTargetChannelName());
        if (textChannel == null) {
            System.err.println("채널을 찾을 수 없습니다.");
            return;
        }

        // 어제의 로그 가져오기
        List<VoiceChannelLog> logs = repository.findAllLogsBetween(startOfDay, endOfDay);
        if (logs.isEmpty()) {
            textChannel.sendMessage("⚠️ 어제의 기록이 없습니다.").queue();
            return;
        }

        // 로그 요약 생성 및 전송 (서버별명 기준, 전체 요약)
        String summary = formatLogsSummed(logs, "어제");
        textChannel.sendMessage(summary).queue();
    }

    private TextChannel findTextChannel(String channelName) {
        return jda.getTextChannelsByName(channelName, true).stream().findFirst().orElse(null);
    }

    /**
     * 어제 로그들을 "유저별 총합"으로 모아서
     * 📊 **어제 전체 공부 기록 요약**
     * 이런 형태의 문자열로 만들어 줌.
     */
    private String formatLogsSummed(List<VoiceChannelLog> logs, String periodName) {
        if (logs.isEmpty()) {
            return "⚠️ " + periodName + " 기록이 없습니다.";
        }

        // 닉네임 기준으로 합산 (없으면 userName으로 fallback)
        Map<String, Long> userDurations = new HashMap<>();
        for (VoiceChannelLog log : logs) {
            String name = Optional.ofNullable(log.getNickName())
                .filter(s -> !s.isBlank())
                .orElse(log.getUserName());

            userDurations.merge(name, log.getDuration(), Long::sum);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📊 **").append(periodName).append(" 전체 공부 기록 요약**\n\n");
        sb.append("🧑‍🤝‍🧑 사용자별 기록\n");
        sb.append("────────────────────────\n");

        userDurations.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .forEach(entry -> {
                String user = entry.getKey();
                long totalSeconds = entry.getValue();
                sb.append("• ")
                    .append(user)
                    .append(" — ")
                    .append(formatDuration(totalSeconds))
                    .append("\n");
            });

        sb.append("────────────────────────");
        // 📌 "전체 합계" 줄은 넣지 않음 (요청사항 반영)

        return sb.toString();
    }

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
}
