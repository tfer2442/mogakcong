package org.example.studybot.command.textcommands;

import org.example.studybot.command.TextCommands;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

@Component
public class Rest implements TextCommands {

    private static final List<String> MESSAGES = List.of(
        "%s 그럼 평생 쉬겠지… 그건 너도 싫잖아.",
        "%s 오늘만 쉬면 내일의 너는 또 ‘오늘만’이라 할걸…?",
        "%s 너가 쉬는 동안 다른 사람은 하고 있어. 너무 뒤처지진 말자!",
        "%s 하루만 쉬자는 말이 제일 위험한 말이야. 그래도 네 의지는 내가 믿어.",
        "%s 지금 쉬면 다시 시작하기 더 어려워져… 조금만 더 해보자!",
        "%s 공부 안 하고 쉬는 건 누구나 할 수 있어. 하지만 너는 그 이상을 바랐잖아.",
        "%s 물론 쉬고 싶은 마음 이해해… 그래도 딱 10분만 해볼까?",
        "%s 힘들면 속도 줄여도 돼. 완전히 멈추지만 말자!",
        "%s 오늘 조금만 더 해두면 내일의 너가 진짜 편해질 거야.",
        "%s 너 지금까지 잘 해왔어. 여기서 멈추기엔 너무 아깝지 않아?"
    );

    private static final Random RANDOM = new Random();

    @Override
    public String getName() {
        return "오늘만쉴까";
    }

    @Override
    public String getDescription() {
        return "공부하라고 다그칩니다.";
    }

    @Override
    public String execute(String displayName, String userName) {
        String message = MESSAGES.get(RANDOM.nextInt(MESSAGES.size()));
        return String.format(message, displayName);
    }
}
