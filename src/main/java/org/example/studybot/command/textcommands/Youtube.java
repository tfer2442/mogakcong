package org.example.studybot.command.textcommands;

import org.example.studybot.command.TextCommands;
import org.springframework.stereotype.Component;

@Component
public class Youtube implements TextCommands {
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
        return displayName + " 지금 유튜브라는 말이 입에서 나오니? \n"
            + "너는 공부해야 할 때야 유튜브 볼 때가 아니라고\n"
            + "취업을 위해서 공부나 해야지 유튜브가 말이되니?\n"
            + "유튜버 될거 아니면 오버워치말고 공부나해!!";
    }
}
