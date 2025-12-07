package org.example.studybot.command.textcommands;

import org.example.studybot.command.TextCommands;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

@Component
public class Tired implements TextCommands {

    private static final List<String> MESSAGES = List.of(
        "%s 피곤한 거 알아… 근데 딱 10분만 더 해보자.",
        "%s 지금 피곤한 건 당연해. 그래도 네가 원하는 건 움직여야 다가와.",
        "%s 잠깐 스트레칭하고 다시 시작해볼래? 너라면 할 수 있어.",
        "%s 피곤하다는 건 열심히 살고 있다는 증거야. 근데 오늘 목표는 채우고 쉬자.",
        "%s 조금만 더 버티면 훨씬 마음이 편해져. 진짜야.",
        "%s 피곤하지… 그래도 너가 포기 안 할 거란 거 알아.",
        "%s 가짜 피곤함인 것도 맞고, 진짜 피곤한 것도 맞아. 근데 지금 멈추면 더 힘들어져.",
        "%s 5분만 더 집중해보자. 그 5분이 너를 바꾼다.",
        "%s 너 오늘도 잘하고 있어. 피곤해도 여기서 멈추긴 아쉽지 않아?",
        "%s 피곤하다고 바로 쉬면 리듬 깨져… 조금만 더 해보자!"
    );

    private static final Random RANDOM = new Random();

    @Override
    public String getName() {
        return "피곤해";
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
