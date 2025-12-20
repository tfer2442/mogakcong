package org.example.studybot.util.summary;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import org.example.studybot.util.TextChannelProperties;
import org.example.studybot.voicechannel.VoiceChannelLog;
import org.example.studybot.voicechannel.VoiceChannelLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class MonthSummaryService {

	@Autowired
	private VoiceChannelLogRepository repository;

	@Autowired
	private JDA jda;

	@Autowired
	private TextChannelProperties textChannelProperties;

	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM/dd");

	// RecordManager와 동일: 월요일 기준 주차
	private static final WeekFields WEEK_FIELDS = WeekFields.of(DayOfWeek.MONDAY, 1);

	/**
	 * LogScheduler 에서 매월 1일 00:01에 호출
	 * "지난 달(1일~말일)" 기록을 집계해서 RecordManager의 "월간 전체 공부 기록 요약" 형식으로 전송
	 */
	public void generateAndSendMonthlySummary() {
		LocalDate today = LocalDate.now();

		// 지난 달 기준 날짜(아무 날이나 잡고 first/last로 범위 확정)
		LocalDate anyDayLastMonth = today.with(TemporalAdjusters.firstDayOfMonth()).minusDays(1);
		LocalDate startDate = anyDayLastMonth.with(TemporalAdjusters.firstDayOfMonth());
		LocalDate endDate = anyDayLastMonth.with(TemporalAdjusters.lastDayOfMonth());

		LocalDateTime start = startDate.atStartOfDay();
		LocalDateTime end = endDate.atTime(23, 59, 59);

		// 1) 요약 채널 이름이 설정돼 있으면 그걸 우선 사용, 없으면 기존 target 사용
		String channelName = Optional.ofNullable(textChannelProperties.getSummaryChannelName())
			.filter(s -> !s.isBlank())
			.orElse(textChannelProperties.getTargetChannelName());

		// 2) 선택된 이름/ID로 텍스트 채널 찾기
		TextChannel textChannel = findTextChannel(channelName);
		if (textChannel == null) {
			System.err.println("[MonthSummaryService] 채널을 찾을 수 없습니다. name=" + channelName);
			return;
		}

		List<VoiceChannelLog> logs = repository.findAllLogsBetween(start, end);

		String message = buildMonthlySummaryMessage(logs, startDate, endDate);

		textChannel.sendMessage(message)
			.queue(
				success -> System.out.println("[MonthSummaryService] 지난 달 월간 요약 전송 완료"),
				error -> System.err.println("[MonthSummaryService] 지난 달 월간 요약 전송 실패: " + error.getMessage())
			);
	}

	/**
	 * RecordManager.formatMonthlySummary(...)의 "전체 조회" 스타일과 동일하게 메시지 생성
	 * - 헤더: 📊 **월간 전체 공부 기록 요약**
	 * - 기준: MM/dd ~ MM/dd
	 * - 유저별: n주차 bullet + 합계
	 */
	private String buildMonthlySummaryMessage(List<VoiceChannelLog> logs, LocalDate startDate, LocalDate endDate) {
		String periodLabel = "월간";

		if (logs == null || logs.isEmpty()) {
			return "⚠️ " + periodLabel + " 기간 동안 기록이 없습니다.";
		}

		// user → (weekIndex → duration)
		Map<String, Map<Integer, Long>> userWeekDurations = new HashMap<>();

		for (VoiceChannelLog log : logs) {
			String user = resolveUserName(log);

			LocalDate date = log.getRecordedAt().toLocalDate();
			int weekIndex = date.get(WEEK_FIELDS.weekOfMonth()); // 월요일 기준 주차

			long duration = Optional.ofNullable(log.getDuration()).orElse(0L);

			userWeekDurations
				.computeIfAbsent(user, k -> new HashMap<>())
				.merge(weekIndex, duration, Long::sum);
		}

		if (userWeekDurations.isEmpty()) {
			return "⚠️ " + periodLabel + " 기간 동안 기록이 없습니다.";
		}

		// userTotals
		Map<String, Long> userTotals = new HashMap<>();
		for (Map.Entry<String, Map<Integer, Long>> entry : userWeekDurations.entrySet()) {
			long sum = entry.getValue().values().stream()
				.mapToLong(Long::longValue)
				.sum();
			userTotals.put(entry.getKey(), sum);
		}

		String dateRange = String.format("기준: %s ~ %s",
			startDate.format(DATE_FMT), endDate.format(DATE_FMT));

		StringBuilder sb = new StringBuilder();
		sb.append("📊 **월간 전체 공부 기록 요약**\n")
			.append(dateRange)
			.append("\n\n");

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

	private TextChannel findTextChannel(String nameOrId) {
		if (nameOrId == null || nameOrId.isBlank()) {
			return null;
		}

		// 숫자로만 이루어진 경우 ID로 시도
		if (nameOrId.chars().allMatch(Character::isDigit)) {
			TextChannel byId = jda.getTextChannelById(nameOrId);
			if (byId != null) {
				return byId;
			}
		}

		// 그 외에는 이름으로 검색
		return jda.getTextChannelsByName(nameOrId, true).stream()
			.findFirst()
			.orElse(null);
	}
}
