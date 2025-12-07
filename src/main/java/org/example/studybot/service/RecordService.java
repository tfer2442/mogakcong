package org.example.studybot.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.example.studybot.model.Channel;
import org.example.studybot.model.TeamMember;
import org.example.studybot.model.Record;
import org.example.studybot.repository.ChannelRepository;
import org.example.studybot.repository.TeamMemberRepository;
import org.example.studybot.repository.RecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;

@Service
public class RecordService {

    @Autowired
    private RecordRepository recordRepository;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    public void handleJoin(Member member, VoiceChannel joined) {
        if (member == null || joined == null) {
            return;
        }

        TeamMember teamMember = teamMemberRepository.getByDiscordId(member.getId());
        if (teamMember == null) {
            // 아직 팀원으로 등록되지 않은 사용자면 기록하지 않음
            return;
        }

        Channel channel = channelRepository.getByDiscordChannelId(joined.getId());
        if (channel == null) {
            // 관리 대상 채널이 아니면 기록하지 않음
            return;
        }

        Record record = new Record();
        record.setTeamMember(teamMember);
        record.setChannel(channel);
        record.setRecordedAt(LocalDateTime.now());
        record.setDuration(null); // 아직 머문 시간 없음

        recordRepository.save(record);
    }

    public void handleLeave(Member member, VoiceChannel left) {
        TeamMember teamMember = teamMemberRepository.findByDiscordId(member.getId())
            .orElse(null);

        if (teamMember == null) return;

        Channel channel = channelRepository.findByDiscordChannelId(left.getId())
            .orElse(null);

        if (channel == null) return;

        Record record = recordRepository.findTopByTeamMemberAndChannelAndDurationIsNullOrderByRecordedAtDesc(teamMember, channel)
            .orElse(null);

        if (record == null) return;

        long duration = ChronoUnit.SECONDS.between(record.getRecordedAt(), LocalDateTime.now());
        record.setDuration(duration);

        recordRepository.save(record);
    }
}
