package org.example.studybot.command.textcommands;

import org.example.studybot.command.TextCommands;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

@Component
public class Hello implements TextCommands {

    private static final List<String> MESSAGES = List.of(
        "%s 안녕! 오늘도 화이팅이야!",
        "%s 안녕~ 공부할 준비 됐지?",
        "%s 안녕! 인사했으니까 이제 공부하자!",
        "%s 안녕! 잠깐 쉬었으면 다시 시작해볼까?",
        "%s 안녕~ 오늘 목표 조금만 더 해보자!",
        "%s 안녕! 너무 오래 쉬면 다시 시작하기 힘들어지니까 살살 해보자!"
    );

    private static final Random RANDOM = new Random();

    @Override
    public String getName() {
        return "안녕";
    }

    @Override
    public String getDescription() {
        return "봇이 인사합니다.";
    }

    @Override
    public String execute(String displayName, String userName) {
        String msg = MESSAGES.get(RANDOM.nextInt(MESSAGES.size()));
        return String.format(msg, displayName);
    }
}
