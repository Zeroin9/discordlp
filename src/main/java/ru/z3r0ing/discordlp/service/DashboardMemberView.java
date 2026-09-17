package ru.z3r0ing.discordlp.service;

import ru.z3r0ing.discordlp.entity.GuildMember;

import java.time.Duration;

/**
 * Строка таблицы дашборда: участник гильдии плюс рассчитанное по баллам время в конференции.
 *
 * @param member        участник гильдии, как он хранится в БД
 * @param voiceTime     время в голосовых каналах, восстановленное по начисленным баллам
 * @param voiceTimeText то же время в виде текста для шаблона
 */
public record DashboardMemberView(GuildMember member, Duration voiceTime, String voiceTimeText) {

    public static DashboardMemberView of(GuildMember member, Duration voiceTime) {
        return new DashboardMemberView(member, voiceTime, VoiceTime.format(voiceTime));
    }
}
