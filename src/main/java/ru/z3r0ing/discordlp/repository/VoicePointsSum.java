package ru.z3r0ing.discordlp.repository;

import ru.z3r0ing.discordlp.entity.TransactionReason;

/**
 * Проекция агрегата: сколько баллов участник получил по конкретной голосовой причине.
 *
 * @param memberId идентификатор записи {@code guild_members}
 * @param reason   причина начисления
 * @param points   сумма начислений по этой причине
 */
public record VoicePointsSum(Long memberId, TransactionReason reason, Long points) {
}
