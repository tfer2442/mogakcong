package org.example.studybot.listener;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import org.example.studybot.util.TextChannelProperties;
import org.example.studybot.voicechannel.VoiceChannelLog;
import org.example.studybot.voicechannel.VoiceChannelLogRepository;
import org.example.studybot.voicechannel.VoiceChannelProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VoiceChannelTracker extends ListenerAdapter {

    @Autowired
    private VoiceChannelLogRepository repository;

    @Autowired
    private VoiceChannelProperties voiceChannelProperties;

    @Autowired
    private TextChannelProperties textChannelProperties;

    // 여러 이벤트 스레드에서 접근하니까 ConcurrentHashMap 사용
    private final Map<Long, LocalDateTime> userJoinTimes = new ConcurrentHashMap<>();

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        Member member = event.getMember();           // 길드 멤버 (null일 수도 있음)
        User user = event.getEntity().getUser();     // 유저 객체 (절대 null 아님)

        long userId = user.getIdLong();
        var joinedChannel = event.getChannelJoined();
        var leftChannel = event.getChannelLeft();

        String targetVoiceChannelName = voiceChannelProperties.getTargetChannelName();
        String targetTextChannelName = textChannelProperties.getTargetChannelName();

        // 텍스트 채널 찾기
        var textChannels = event.getGuild().getTextChannelsByName(targetTextChannelName, true);
        TextChannel textChannel = (textChannels != null && !textChannels.isEmpty()) ? textChannels.get(0) : null;

        // 👤 닉네임/이름 처리 (여기서 null 절대 안 나게)
        String displayName;
        if (member != null) {
            displayName = member.getEffectiveName();   // 닉네임 있으면 닉네임, 없으면 username
        } else {
            displayName = user.getName();
        }

        // 🎧 1) 대상 음성채널에 "입장" 했는지 체크
        if (joinedChannel != null && joinedChannel.getName().equals(targetVoiceChannelName)) {
            // 아직 기록 안 된 사용자만 처리
            if (!userJoinTimes.containsKey(userId)) {
                userJoinTimes.put(userId, LocalDateTime.now());

                if (textChannel != null) {
                    textChannel.sendMessage(
                        displayName + "님이 `" + joinedChannel.getName() + "` 채널에 입장했습니다."
                    ).queue();
                }
            }
        }

        // 🎧 2) 대상 음성채널에서 "완전히 나간" 경우만 처리
        //    - 지금 로직은: 서버의 모든 음성채널에서 완전히 나갈 때만 퇴장으로 침
        //    - 만약 다른 음성채널로 이동하는 것도 퇴장으로 치고 싶으면 joinedChannel == null 조건을 빼면 됨
        if (leftChannel != null
            && leftChannel.getName().equals(targetVoiceChannelName)
            && joinedChannel == null) {

            LocalDateTime joinTime = userJoinTimes.remove(userId);

            if (joinTime != null) {
                long duration = ChronoUnit.SECONDS.between(joinTime, LocalDateTime.now());
                long hours = duration / 3600;
                long minutes = (duration % 3600) / 60;
                long seconds = duration % 60;

                // DB 저장
                VoiceChannelLog log = new VoiceChannelLog();
                log.setUserId(userId);
                log.setNickName(displayName);                // ← nickName 대신 displayName 사용
                log.setChannelId(leftChannel.getIdLong());
                log.setChannelName(leftChannel.getName());
                log.setDuration(duration);
                log.setRecordedAt(LocalDateTime.now());
                log.setUserName(user.getName());             // 원래 디스코드 username

                repository.save(log);

                // 텍스트 채널에 메시지 출력
                if (textChannel != null) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(displayName)
                        .append("님이 `").append(leftChannel.getName()).append("` 채널에서 퇴장했습니다.\n")
                        .append("머문 시간: ");
                    if (hours > 0) sb.append(hours).append("시간 ");
                    if (minutes > 0) sb.append(minutes).append("분 ");
                    sb.append(seconds).append("초");

                    textChannel.sendMessage(sb.toString()).queue();
                }
            }
        }
    }
}
