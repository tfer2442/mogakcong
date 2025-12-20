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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class WeekSummaryService {

	@Autowired
	private VoiceChannelLogRepository repository;

	@Autowired
	private JDA jda;

	@Autowired
	private TextChannelProperties textChannelProperties;

	// RecordManager와 동일한 요일 순서(월~일)
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

	/**
	 * LogScheduler 에서 매주 월요일 00:01에 호출
	 * "지난 주(월~일)" 기록을 집계해서 RecordManager의 "주간 전체 공부 기록 요약"과 동일한 스타일로 전송
	 */
	public void generateAndSendWeeklySummary() {
		// 오늘 기준 "이번 주 월요일" -> 거기서 1주 빼서 "지난 주 월요일"
		LocalDate today = LocalDate.now();
		LocalDate thisWeekMonday = today.minusDays(today.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());

		LocalDate lastWeekMonday = thisWeekMonday.minusWeeks(1);
		LocalDate lastWeekSunday = lastWeekMonday.plusDays(6);

		LocalDateTime start = lastWeekMonday.atStartOfDay();
		LocalDateTime end = lastWeekSunday.atTime(23, 59, 59);

		// 1) 요약 채널 이름이 설정돼 있으면 그걸 우선 사용, 없으면 기존 target 사용 (DailySummaryService와 동일)
		String channelName = Optional.ofNullable(textChannelProperties.getSummaryChannelName())
			.filter(s -> !s.isBlank())
			.orElse(textChannelProperties.getTargetChannelName());

		// 2) 선택된 이름/ID로 텍스트 채널 찾기
		TextChannel textChannel = findTextChannel(channelName);
		if (textChannel == null) {
			System.err.println("[WeekSummaryService] 채널을 찾을 수 없습니다. name=" + channelName);
			return;
		}

		List<VoiceChannelLog> logs = repository.findAllLogsBetween(start, end);

		String message = buildWeeklySummaryMessage(logs, start.toLocalDate(), end.toLocalDate());

		textChannel.sendMessage(message)
			.queue(
				success -> System.out.println("[WeekSummaryService] 지난 주 주간 요약 전송 완료"),
				error -> System.err.println("[WeekSummaryService] 지난 주 주간 요약 전송 실패: " + error.getMessage())
			);
	}

	/**
	 * RecordManager.formatWeeklySummary(...)의 "전체 조회" 스타일과 동일하게 메시지 생성
	 */
	private String buildWeeklySummaryMessage(List<VoiceChannelLog> logs, LocalDate startDate, LocalDate endDate) {
		if (logs == null || logs.isEmpty()) {
			// RecordManager와 동일한 안내 문구 스타일
			return "⚠️ 주간 기간 동안 기록이 없습니다.";
		}

		// user → (DayOfWeek → duration)
		Map<String, Map<DayOfWeek, Long>> userDayDurations = new HashMap<>();

		for (VoiceChannelLog log : logs) {
			String user = resolveUserName(log);

			LocalDate date = log.getRecordedAt().toLocalDate();
			DayOfWeek dow = date.getDayOfWeek();

			long duration = Optional.ofNullable(log.getDuration()).orElse(0L);

			userDayDurations
				.computeIfAbsent(user, k -> new HashMap<>())
				.merge(dow, duration, Long::sum);
		}

		if (userDayDurations.isEmpty()) {
			return "⚠️ 주간 기간 동안 기록이 없습니다.";
		}

		// userTotals
		Map<String, Long> userTotals = new HashMap<>();
		for (Map.Entry<String, Map<DayOfWeek, Long>> entry : userDayDurations.entrySet()) {
			long sum = entry.getValue().values().stream()
				.mapToLong(Long::longValue)
				.sum();
			userTotals.put(entry.getKey(), sum);
		}

		String dateRange = String.format("기준: %s ~ %s", startDate.format(DATE_FMT), endDate.format(DATE_FMT));

		StringBuilder sb = new StringBuilder();
		sb.append("📊 **주간 전체 공부 기록 요약**\n")
			.append(dateRange)
			.append("\n\n");

		userTotals.entrySet().stream()
			.sorted(Map.Entry.<String, Long>comparingByValue().reversed())
			.forEach(entry -> {
				String user = entry.getKey();
				long total = entry.getValue();
				Map<DayOfWeek, Long> days = userDayDurations.get(user);

				sb.append("────────────────────────\n");
				sb.append("**").append(user).append("**\n\n");

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
