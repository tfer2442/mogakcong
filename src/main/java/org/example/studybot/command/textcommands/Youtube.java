package org.example.studybot.command.textcommands;

import org.example.studybot.command.TextCommands;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

@Component
public class Youtube implements TextCommands {

    private static final List<String> MESSAGES = List.of(
        "%s 지금 유튜브라는 말이 입에서 나오네…? 마음 다잡자!",
        "%s 유튜브 보기엔 너무 아까운 시간이야. 목표 있었잖아!",
        "%s 유튜브? 5분만 본다는 건 모두의 거짓말이야… 공부 먼저!",
        "%s 잠깐만, 진짜 볼 거야? 그보다 조금만 더 하고 보자!",
        "%s 유튜버 할 거 아니면 일단 공부부터 하자 ㅋㅋ",
        "%s 지금 유튜브 켜면 1시간 사라지는 거 알지? 그 시간에 미래 땡겨오자!",
        "%s 유튜브는 언제든 볼 수 있어. 근데 지금의 집중력은 지금만 있어.",
        "%s 유튜브 보고 싶은 마음은 이해해… 그래도 오늘 목표는 채우자!",
        "%s 딱 10분 공부하고 다시 생각해봐. 아마 안 보고 싶어질 걸?",
        "%s 공부하다 유튜브 켜는 순간 오늘은 끝이다 ㅋㅋ 멈춰!"
    );

    private static final Random RANDOM = new Random();

    @Override
    public String getName() {
        return "유튜브볼까";
    }

    @Override
    public String getDescription() {
        return "공부하라고 다그칩니다.";
    }

    @Override
    public String execute(String displayName, String userName) {
        String msg = MESSAGES.get(RANDOM.nextInt(MESSAGES.size()));
        return String.format(msg, displayName);
    }
}
