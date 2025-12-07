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
            textChannel.sendMessage("어제의 기록이 없습니다.").queue();
            return;
        }

        // 로그 요약 생성 및 전송 (서버별명 기준)
        String summary = formatLogsSummed(logs, "어제");
        textChannel.sendMessage(summary).queue();
    }

    private TextChannel findTextChannel(String channelName) {
        return jda.getTextChannelsByName(channelName, true).stream().findFirst().orElse(null);
    }

    // 🔹 nickName(서버별명) + 채널명 기준으로 머문 시간 합산
    private String formatLogsSummed(List<VoiceChannelLog> logs, String periodName) {
        if (logs.isEmpty()) {
            return periodName + " 기간 동안 기록이 없습니다.";
        }

        Map<String, Map<String, Long>> userChannelDurations = new HashMap<>();
        for (VoiceChannelLog log : logs) {
            String serverNickName = log.getNickName();     // 서버별명
            String channelName = log.getChannelName();

            userChannelDurations
                .computeIfAbsent(serverNickName, k -> new HashMap<>())
                .merge(channelName, log.getDuration(), Long::sum);
        }

        if (userChannelDurations.isEmpty()) {
            return periodName + " 기간 동안 기록이 없습니다.";
        }

        StringBuilder response = new StringBuilder(periodName + " 기록 요약:\n");
        userChannelDurations.forEach((serverNickName, channelDurations) -> {
            channelDurations.forEach((channelName, totalDuration) -> {
                String formattedDuration = formatDuration(totalDuration);
                response.append(String.format(
                    "%s님이 `%s` 채널에서 %s 머물렀습니다.\n",
                    serverNickName, channelName, formattedDuration
                ));
            });
        });

        return response.toString();
    }

    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        return String.format("%d시간 %d분 %d초", hours, minutes, secs);
    }
}
