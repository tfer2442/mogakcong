package org.example.studybot.listener;

import org.example.studybot.command.CommandHandler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.actionrow.ActionRow;

@RequiredArgsConstructor
@Slf4j
public class StudyBotDiscordListener extends ListenerAdapter {

    private CommandHandler commandHandler;

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        User user = event.getAuthor();
        Member member = event.getMember();
        TextChannel textChannel = event.getChannel().asTextChannel();
        Message message = event.getMessage();

        if (user.isBot())
            return;

        String content = message.getContentDisplay().trim();

        if (content.startsWith("!")) {
            String nickname = member != null ? member.getNickname() : null;
            String displayName = nickname != null ? nickname : user.getName();
            String cmd = content.substring(1).trim();

            if (cmd.equals("명령어")) {
                textChannel.sendMessage("명령어를 선택하거나 취소할 수 있습니다.")
                    .addComponents(
                        ActionRow.of(commandHandler.getTextCommandsDropdown()),
                        ActionRow.of(Button.danger("cancel_menu", "취소"))
                    )
                    .queue();
                return;
            }

            if (cmd.equals("기록")) {
                textChannel.sendMessage("기록 관련 명령어를 선택하세요.")
                    .addComponents(
                        ActionRow.of(commandHandler.getRecordCommandsDropdown()),
                        ActionRow.of(Button.danger("cancel_menu", "취소"))
                    )
                    .queue();
                return;
            }

            String returnMessage = commandHandler.handle(cmd, displayName, displayName);
            textChannel.sendMessage(returnMessage).queue();
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        String componentId = event.getComponentId();

        if (componentId.startsWith("command_selector")) {
            String selected = event.getValues().get(0);

            Member member = event.getMember();
            String nickname = member != null ? member.getNickname() : null;
            String displayName = nickname != null ? nickname : event.getUser().getName();

            String returnMessage = commandHandler.handle(selected, displayName, displayName);

            event.reply(returnMessage).queue();
            event.getMessage().delete().queue();
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (event.getComponentId().equals("cancel_menu")) {
            event.reply("명령어 선택이 취소되었습니다!").setEphemeral(true).queue();
            event.getMessage().delete().queue();
        }
    }

}
