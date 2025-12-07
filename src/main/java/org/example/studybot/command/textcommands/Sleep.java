package org.example.studybot.command.textcommands;

import org.example.studybot.command.TextCommands;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

@Component
public class Sleep implements TextCommands {

    private static final List<String> MESSAGES = List.of(
        "%s 평생 잠만 자고 싶어? 그러기엔 너무 아깝지 않아?",
        "%s 지금 자면 내일의 너가 또 힘들어져… 조금만 더 버텨보자.",
        "%s 그냥 잘 거면 공부는 왜 시작했어? 너 진짜 잘하고 있는데.",
        "%s 자고 싶다는 생각, 사실 공부하기 싫다는 마음이잖아… 하지만 너는 할 수 있어!",
        "%s 조금만 더 하고 자자. 진짜 딱 조금만. 너 오늘 잘했어.",
        "%s 자는 건 쉽지… 하지만 너 목표 있었잖아. 그걸 포기하긴 아깝다!",
        "%s 몸이 피곤한 건 이해해. 그런데 지금 멈추면 리듬 완전 깨진다!",
        "%s 네가 원하는 미래는 ‘조금만 더’에서 만들어지는 거야.",
        "%s 지금 10분만 더 해봐. 그럼 자도 마음이 훨씬 편해.",
        "%s 잠은 언제든 잘 수 있어. 하지만 지금의 의지는 잠들면 다시 안 와."
    );

    private static final Random RANDOM = new Random();

    @Override
    public String getName() {
        return "그냥잘까";
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
