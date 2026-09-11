package ru.z3r0ing.discordlp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.requests.RestAction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Еженедельная сводка активности: кто за неделю был в голосовых каналах и сколько времени
 * провел со стримом и без него.
 *
 * <p>Время, как и в остальной таблице, не хранится отдельно — оно считается по журналу
 * начислений за нужный период (см. {@link VoiceTime}). В сводку попадают только те, у кого
 * за период есть голосовое время; про тех, кто в Discord не заходил, писать нечего.
 *
 * <p>Публикация включается переменной {@code WEEKLY_SUMMARY_CHANNEL_ID}: без нее планировщик
 * просто ничего не делает, а сводку по-прежнему можно запросить командой {@code /lpweek}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyActivityService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final JDA jda;
    private final DashboardService dashboardService;
    private final MemberReportService memberReportService;

    /** Канал, куда уходит еженедельная сводка. Пусто — автоматическая публикация выключена. */
    @Value("${weekly-summary.channel-id:}")
    private String channelId;

    /** Длина периода сводки в днях. */
    @Value("${weekly-summary.days:7}")
    private int days;

    /** Часовой пояс, в котором показываются границы периода. */
    @Value("${weekly-summary.zone:Europe/Moscow}")
    private String zone;

    /**
     * Собирает сводку за последние {@link #days} дней в виде готовых к отправке сообщений.
     *
     * @param guildId   идентификатор гильдии Discord
     * @param guildName название гильдии для заголовка
     */
    public List<String> buildSummary(String guildId, String guildName) {
        Instant until = Instant.now();
        return buildSummary(guildId, guildName, until.minus(Duration.ofDays(days)), until);
    }

    /**
     * Собирает сводку за произвольный период.
     *
     * @param since начало периода включительно
     * @param until конец периода; используется только в заголовке
     */
    public List<String> buildSummary(String guildId, String guildName, Instant since, Instant until) {
        List<DashboardMemberView> rows = dashboardService
                .getGuildMembers(guildId, since, DashboardSort.TOTAL_TIME.getKey() + ",desc")
                .stream()
                .filter(row -> !row.voiceTime().isZero())
                .toList();

        return memberReportService.render(
                title(guildName, since, until, rows),
                rows,
                MemberReportService.ACTIVITY_COLUMNS,
                "За этот период в голосовых каналах никого не было.");
    }

    /**
     * Публикует сводку в настроенный канал. Ошибки только логируются: сводка — информационное
     * сообщение, из-за нее не должен падать планировщик.
     */
    @Scheduled(cron = "${weekly-summary.cron:0 0 12 * * MON}", zone = "${weekly-summary.zone:Europe/Moscow}")
    public void publishWeeklySummary() {
        if (channelId == null || channelId.isBlank()) {
            log.debug("Канал для еженедельной сводки не настроен, публикация пропущена");
            return;
        }

        if (jda.getStatus() != JDA.Status.CONNECTED) {
            log.warn("JDA не в статусе CONNECTED (текущий: {}). Еженедельная сводка пропущена.", jda.getStatus());
            return;
        }

        try {
            GuildMessageChannel channel = jda.getChannelById(GuildMessageChannel.class, channelId);
            if (channel == null) {
                log.warn("Канал {} для еженедельной сводки недоступен", channelId);
                return;
            }

            List<String> messages = buildSummary(channel.getGuild().getId(), channel.getGuild().getName());
            send(channel, messages);
        } catch (Exception e) {
            log.error("Ошибка при публикации еженедельной сводки", e);
        }
    }

    private String title(String guildName, Instant since, Instant until, List<DashboardMemberView> rows) {
        ZoneId zoneId = zoneId();
        return "🗓 **Кто был в Discord — сводка за неделю: " + guildName + "**\n"
                + "Период: " + DATE_FORMAT.format(since.atZone(zoneId))
                + " — " + DATE_FORMAT.format(until.atZone(zoneId))
                + " · участников: " + rows.size();
    }

    private ZoneId zoneId() {
        try {
            return ZoneId.of(zone);
        } catch (Exception e) {
            log.warn("Неизвестный часовой пояс {} для еженедельной сводки, используется UTC", zone);
            return ZoneId.of("UTC");
        }
    }

    /** Отправляет части сводки по очереди, чтобы Discord не переставил их местами. */
    private static void send(GuildMessageChannel channel, List<String> messages) {
        RestAction<Message> action = channel.sendMessage(messages.getFirst());
        for (String message : messages.subList(1, messages.size())) {
            action = action.flatMap(sent -> channel.sendMessage(message));
        }
        action.queue(
                sent -> log.debug("Еженедельная сводка опубликована, частей: {}", messages.size()),
                failure -> log.warn("Не удалось опубликовать еженедельную сводку: {}", failure.getMessage())
        );
    }
}
