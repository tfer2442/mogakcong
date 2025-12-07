package org.example.studybot.command.textcommands;

import org.example.studybot.command.TextCommands;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

@Component
public class Jinjja implements TextCommands {

    private static final List<String> MESSAGES = List.of(
        "%s 그런 날도 있어. 그래도 조금만 해보자?",
        "%s 쉬고 싶은 마음 알지만… 아주 조금이라도 해보자.",
        "%s 일단 5분만 해보면 어때? 시작이 반이야.",
        "%s 오늘 컨디션 안 좋아도, 가볍게라도 해보자.",
        "%s 공부가 막 하기 싫을 때일수록, 가벼운 것부터 시작해봐.",
        "%s 완벽하게 하려고 하지 말고, 그냥 책 한 장이라도 넘겨보자."
    );

    private static final Random RANDOM = new Random();

    @Override
    public String getName() {
        return "진짜하기싫다";
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
