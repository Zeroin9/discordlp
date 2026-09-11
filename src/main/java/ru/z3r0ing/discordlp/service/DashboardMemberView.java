package ru.z3r0ing.discordlp.service;

import ru.z3r0ing.discordlp.entity.GuildMember;

import java.time.Duration;

/**
 * Строка таблицы участников: участник гильдии плюс рассчитанное по баллам время в конференции,
 * разложенное на время со стримом и без него.
 *
 * @param member            участник гильдии, как он хранится в БД
 * @param voiceTime         время в голосовых каналах, восстановленное по начисленным баллам
 * @param totalTimeText     суммарное время в виде текста
 * @param streamTimeText    время со стримом в виде текста
 * @param noStreamTimeText  время без стрима в виде текста
 */
public record DashboardMemberView(GuildMember member,
                                  VoiceTimeBreakdown voiceTime,
                                  String totalTimeText,
                                  String streamTimeText,
                                  String noStreamTimeText) {

    public static DashboardMemberView of(GuildMember member, VoiceTimeBreakdown voiceTime) {
        return new DashboardMemberView(member, voiceTime,
                VoiceTime.format(voiceTime.total()),
                VoiceTime.format(voiceTime.withStream()),
                VoiceTime.format(voiceTime.withoutStream()));
    }

    /** Суммарное время в голосовых каналах. */
    public Duration totalTime() {
        return voiceTime.total();
    }

    /** Время в канале, где шел стрим. */
    public Duration streamTime() {
        return voiceTime.withStream();
    }

    /** Время в канале без стрима. */
    public Duration noStreamTime() {
        return voiceTime.withoutStream();
    }

    /**
     * Значение строки в указанной колонке в готовом для вывода виде.
     * Через него и HTML-таблица, и текстовый отчет в Discord строятся по одному набору колонок,
     * так что порядок ячеек не может разойтись с порядком заголовков.
     */
    public String text(DashboardSort column) {
        return switch (column) {
            case USER_NAME -> orEmpty(member.getUserName());
            case GUILD_NAME -> orEmpty(member.getGuildName());
            case BALANCE -> String.valueOf(member.getBalance() == null ? 0L : member.getBalance());
            case TOTAL_TIME -> totalTimeText;
            case STREAM_TIME -> streamTimeText;
            case NO_STREAM_TIME -> noStreamTimeText;
        };
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
