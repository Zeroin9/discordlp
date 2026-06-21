package ru.z3r0ing.discordlp.config;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class SlashCommandRegistrar {

    private static final Logger log = LoggerFactory.getLogger(SlashCommandRegistrar.class);

    private final JDA jda;

    public SlashCommandRegistrar(JDA jda) {
        this.jda = jda;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerCommands() {
        jda.updateCommands()
                .addCommands(
                        Commands.slash("lp", "Показать ваш баланс поинтов"),
                        Commands.slash("lpuser", "Посмотреть баланс участника (только для администраторов)")
                                .addOption(OptionType.USER, "user", "Участник, чей баланс нужно проверить", true),
                        Commands.slash("lpadd", "Начислить поинты участнику (только для администраторов)")
                                .addOption(OptionType.USER, "user", "Участник для начисления", true)
                                .addOption(OptionType.INTEGER, "amount", "Количество поинтов", true),
                        Commands.slash("lpremove", "Списать поинты у участника (только для администраторов)")
                                .addOption(OptionType.USER, "user", "Участник для списания", true)
                                .addOption(OptionType.INTEGER, "amount", "Количество поинтов", true),
                        Commands.slash("lpkick", "Отключить участника от голосового канала за поинты (10000 LP)")
                                .addOption(OptionType.USER, "user", "Участник для отключения", true)
                )
                .queue(
                        success -> log.info("Slash команды успешно зарегистрированы."),
                        error -> log.error("Ошибка при регистрации slash команд", error)
                );
    }
}