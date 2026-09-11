package ru.z3r0ing.discordlp.service;

import ru.z3r0ing.discordlp.entity.TransactionReason;

import java.time.Duration;

/**
 * Время в голосовых каналах, разложенное на две части: проведенное при работающем стриме
 * и проведенное без него.
 *
 * <p>Разделение идет по причине начисления: {@link TransactionReason#VOICE_VIEWER} и
 * {@link TransactionReason#VOICE_STREAMER} означают, что в канале кто-то стримил, а
 * {@link TransactionReason#VOICE_STANDARD} — что нет. Само время, как и раньше, не хранится
 * в БД, а восстанавливается по журналу начислений (см. {@link VoiceTime}).
 *
 * @param withStream    время в канале, где шел стрим (как зритель или как стример)
 * @param withoutStream время в канале без стрима
 */
public record VoiceTimeBreakdown(Duration withStream, Duration withoutStream) {

    public static final VoiceTimeBreakdown ZERO = new VoiceTimeBreakdown(Duration.ZERO, Duration.ZERO);

    /** Суммарное время в голосовых каналах. */
    public Duration total() {
        return withStream.plus(withoutStream);
    }

    /** Есть ли вообще время в конференции. */
    public boolean isZero() {
        return total().isZero();
    }

    /** Складывает две разбивки по частям. */
    public VoiceTimeBreakdown plus(VoiceTimeBreakdown other) {
        return new VoiceTimeBreakdown(withStream.plus(other.withStream), withoutStream.plus(other.withoutStream));
    }

    /**
     * Добавляет к разбивке начисления по одной причине.
     *
     * @param reason причина начисления; неголосовые причины разбивку не меняют
     * @param points сумма начисленных по этой причине баллов
     */
    public VoiceTimeBreakdown plus(TransactionReason reason, long points) {
        Duration time = VoiceTime.of(reason, points);
        if (time.isZero()) {
            return this;
        }
        return VoiceTime.isWithStream(reason)
                ? new VoiceTimeBreakdown(withStream.plus(time), withoutStream)
                : new VoiceTimeBreakdown(withStream, withoutStream.plus(time));
    }
}
