package ru.z3r0ing.discordlp.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.RestAction;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.z3r0ing.discordlp.service.DashboardMemberView;
import ru.z3r0ing.discordlp.service.DashboardService;
import ru.z3r0ing.discordlp.service.DashboardSort;
import ru.z3r0ing.discordlp.service.DashboardSortOrder;
import ru.z3r0ing.discordlp.service.MemberReportService;

import java.util.List;
import java.util.Objects;

/**
 * Выводит всю таблицу участников текстовым отчетом прямо в канал.
 *
 * <p>Колонки и сортировка — те же, что на дашборде: баланс, время в конфе, время со стримом
 * и без него. Отчет собирается по участникам текущей гильдии и уходит обычным (не эфемерным)
 * сообщением, чтобы его видел весь канал.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LpTableCommand implements SlashCommandHandler {

    public static final String COMMAND_NAME = "lptable";
    public static final String OPTION_SORT = "sort";
    public static final String OPTION_ORDER = "order";

    /** Значение опции {@link #OPTION_ORDER} для сортировки по возрастанию. */
    public static final String ORDER_ASC = "asc";

    private final DashboardService dashboardService;
    private final MemberReportService memberReportService;

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
        DashboardSortOrder order = requestedOrder(event);

        // Таблица собирается по всем участникам гильдии, поэтому сначала откладываем ответ.
        event.deferReply().queue();
        InteractionHook hook = event.getHook();

        try {
            List<DashboardMemberView> rows = dashboardService.getGuildMembers(guild.getId(), null, order.toParam());
            List<String> messages = memberReportService.render(
                    title(guild, order, rows.size()),
                    rows,
                    MemberReportService.TABLE_COLUMNS,
                    "Пока никто не набрал ни одного балла.");
            send(hook, messages);
        } catch (Exception e) {
            log.error("Не удалось построить отчет по участникам гильдии {}", guild.getId(), e);
            hook.sendMessage("Не удалось построить отчет. Попробуйте еще раз.").setEphemeral(true).queue();
        }
    }

    /**
     * Колонка и направление из опций команды. Если направление не указано, берется
     * предпочтительное для колонки: у чисел и времени — по убыванию, у имен — по возрастанию.
     */
    private static DashboardSortOrder requestedOrder(SlashCommandInteractionEvent event) {
        OptionMapping sortOption = event.getOption(OPTION_SORT);
        DashboardSort column = sortOption == null
                ? DashboardSortOrder.DEFAULT.column()
                : DashboardSort.byKey(sortOption.getAsString()).orElse(DashboardSortOrder.DEFAULT.column());

        OptionMapping orderOption = event.getOption(OPTION_ORDER);
        String direction = orderOption == null
                ? column.getPreferredDirection().name()
                : orderOption.getAsString();

        return DashboardSortOrder.parse(column.getKey() + "," + direction);
    }

    private static String title(Guild guild, DashboardSortOrder order, int total) {
        return "📋 **Таблица участников — " + guild.getName() + "**\n"
                + "Сортировка: " + order.column().getTitle() + order.indicator(order.column())
                + " · участников: " + total;
    }

    /**
     * Отправляет части отчета по очереди: следующее сообщение уходит только после
     * успешной отправки предыдущего, иначе Discord может переставить их местами.
     */
    private static void send(InteractionHook hook, List<String> messages) {
        RestAction<Message> action = hook.sendMessage(messages.getFirst());
        for (String message : messages.subList(1, messages.size())) {
            action = action.flatMap(sent -> hook.sendMessage(message));
        }
        action.queue(
                sent -> log.debug("Отчет по участникам отправлен, частей: {}", messages.size()),
                failure -> log.warn("Не удалось отправить отчет по участникам: {}", failure.getMessage())
        );
    }
}
