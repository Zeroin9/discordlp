package ru.z3r0ing.discordlp.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.RestAction;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.z3r0ing.discordlp.service.WeeklyActivityService;

import java.util.List;
import java.util.Objects;

/**
 * Показывает сводку активности за неделю по запросу — то же, что планировщик публикует
 * автоматически (см. {@link WeeklyActivityService}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LpWeekCommand implements SlashCommandHandler {

    public static final String COMMAND_NAME = "lpweek";

    private final WeeklyActivityService weeklyActivityService;

    @Override
    public @NotNull String getCommandName() {
        return COMMAND_NAME;
    }

    @Override
    public boolean requiresAdmin() {
        return false;
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        Guild guild = Objects.requireNonNull(event.getGuild());

        event.deferReply().queue();
        InteractionHook hook = event.getHook();

        try {
            List<String> messages = weeklyActivityService.buildSummary(guild.getId(), guild.getName());
            RestAction<Message> action = hook.sendMessage(messages.getFirst());
            for (String message : messages.subList(1, messages.size())) {
                action = action.flatMap(sent -> hook.sendMessage(message));
            }
            action.queue(
                    sent -> log.debug("Сводка за неделю отправлена, частей: {}", messages.size()),
                    failure -> log.warn("Не удалось отправить сводку за неделю: {}", failure.getMessage())
            );
        } catch (Exception e) {
            log.error("Не удалось построить сводку за неделю для гильдии {}", guild.getId(), e);
            hook.sendMessage("Не удалось построить сводку. Попробуйте еще раз.").setEphemeral(true).queue();
        }
    }
}
