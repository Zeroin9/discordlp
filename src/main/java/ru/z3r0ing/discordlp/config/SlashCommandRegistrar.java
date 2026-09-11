package ru.z3r0ing.discordlp.config;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.z3r0ing.discordlp.command.LpTableCommand;
import ru.z3r0ing.discordlp.command.LpWeekCommand;
import ru.z3r0ing.discordlp.service.DashboardSort;

import java.util.Arrays;
import java.util.List;

@Component
public class SlashCommandRegistrar {

    private static final Logger log = LoggerFactory.getLogger(SlashCommandRegistrar.class);

    private final JDA jda;

    public SlashCommandRegistrar(JDA jda) {
        this.jda = jda;
    }

    /** Варианты колонок для сортировки таблицы — те же, что на дашборде. */
    private static List<Command.Choice> sortChoices() {
        return Arrays.stream(DashboardSort.values())
                .map(column -> new Command.Choice(column.getTitle(), column.getKey()))
                .toList();
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
                                .addOption(OptionType.USER, "user", "Участник для отключения", true),
                        Commands.slash("lpmute", "Замьютить участника в голосовом канале за поинты (50000 LP)")
                                .addOption(OptionType.USER, "user", "Участник для мьюта", true),
                        Commands.slash("lp-pari", "Создать пари: участники ставят поинты на исход «Да» или «Нет»")
                                .addOption(OptionType.STRING, "title", "Название пари", true),
                        Commands.slash(LpTableCommand.COMMAND_NAME, "Вывести в чат таблицу участников: баланс и время в конфе")
                                .addOptions(
                                        new OptionData(OptionType.STRING, LpTableCommand.OPTION_SORT, "Колонка сортировки")
                                                .addChoices(sortChoices()),
                                        new OptionData(OptionType.STRING, LpTableCommand.OPTION_ORDER, "Направление сортировки")
                                                .addChoice("По убыванию", "desc")
                                                .addChoice("По возрастанию", LpTableCommand.ORDER_ASC)
                                ),
                        Commands.slash(LpWeekCommand.COMMAND_NAME, "Сводка за неделю: кто был в голосовых каналах и сколько")
                )
                .queue(
                        success -> log.info("Slash команды успешно зарегистрированы."),
                        error -> log.error("Ошибка при регистрации slash команд", error)
                );
    }
}