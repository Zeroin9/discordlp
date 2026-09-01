package ru.z3r0ing.discordlp.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.z3r0ing.discordlp.entity.Pari;
import ru.z3r0ing.discordlp.service.PariException;
import ru.z3r0ing.discordlp.service.PariMessageService;
import ru.z3r0ing.discordlp.service.PariService;

import java.util.Objects;

/**
 * Создает пари и публикует сообщение-опрос с кнопками.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LpPariCommand implements SlashCommandHandler {

    public static final String COMMAND_NAME = "lp-pari";
    public static final String OPTION_TITLE = "title";

    private final PariService pariService;
    private final PariMessageService pariMessageService;

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
        OptionMapping titleOption = event.getOption(OPTION_TITLE);
        if (titleOption == null) {
            event.reply("Укажите название пари.").setEphemeral(true).queue();
            return;
        }

        Pari pari;
        try {
            pari = pariService.createPari(guild, event.getUser(), titleOption.getAsString());
        } catch (PariException e) {
            event.reply(e.getMessage()).setEphemeral(true).queue();
            return;
        }

        event.reply(pariMessageService.buildCreateData(pari)).queue(
                hook -> hook.retrieveOriginal().queue(
                        // Координаты сообщения нужны, чтобы обновлять опрос из планировщика
                        message -> pariService.attachMessage(pari.getId(), message.getChannel().getId(), message.getId()),
                        failure -> log.warn("Не удалось получить сообщение пари {}: {}", pari.getId(), failure.getMessage())
                ),
                failure -> log.error("Не удалось опубликовать пари {}", pari.getId(), failure)
        );
    }
}
