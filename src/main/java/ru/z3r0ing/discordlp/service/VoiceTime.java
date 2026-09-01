package ru.z3r0ing.discordlp.service;

import ru.z3r0ing.discordlp.entity.TransactionReason;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * Правила начисления баллов за голосовые каналы и обратный пересчет баллов во время,
 * проведенное в конференции.
 *
 * <p>Планировщик {@link LoyaltyPointsService} раз в {@link #AWARD_INTERVAL_SECONDS} секунд
 * начисляет участнику фиксированное количество баллов, поэтому по сумме начислений можно
 * восстановить длительность: {@code время = баллы / ставка * интервал}. Отдельного поля в БД
 * для этого не нужно — журнал {@code points_transactions} уже хранит все начисления.
 */
public final class VoiceTime {

    /** Интервал между начислениями баллов за нахождение в голосовом канале. */
    public static final long AWARD_INTERVAL_SECONDS = 300L;

    /** Начисление обычному участнику за один интервал. */
    public static final int POINTS_STANDARD = 100;

    /** Начисление зрителю (в канале есть стример) за один интервал. */
    public static final int POINTS_VIEWER = 150;

    /** Начисление стримеру (в канале есть зрители) за один интервал. */
    public static final int POINTS_STREAMER = 200;

    /** Сколько баллов соответствует одному интервалу для каждой из голосовых причин. */
    private static final Map<TransactionReason, Integer> POINTS_PER_INTERVAL = Map.of(
            TransactionReason.VOICE_STANDARD, POINTS_STANDARD,
            TransactionReason.VOICE_VIEWER, POINTS_VIEWER,
            TransactionReason.VOICE_STREAMER, POINTS_STREAMER
    );

    /** Причины транзакций, по которым считается время в конференции. */
    public static final Set<TransactionReason> VOICE_REASONS = POINTS_PER_INTERVAL.keySet();

    private VoiceTime() {
    }

    /**
     * Переводит сумму начислений по одной причине во время в голосовом канале.
     *
     * @param reason причина начисления; неголосовые причины дают нулевую длительность
     * @param points суммарное количество начисленных по этой причине баллов
     * @return длительность, округленная до секунды
     */
    public static Duration of(TransactionReason reason, long points) {
        Integer pointsPerInterval = POINTS_PER_INTERVAL.get(reason);
        if (pointsPerInterval == null || points <= 0) {
            return Duration.ZERO;
        }
        return Duration.ofSeconds(Math.round((double) points * AWARD_INTERVAL_SECONDS / pointsPerInterval));
    }

    /**
     * Суммирует время по всем голосовым причинам сразу.
     *
     * @param pointsByReason сумма начисленных баллов в разрезе причины
     * @return суммарное время в конференции
     */
    public static Duration total(Map<TransactionReason, Long> pointsByReason) {
        Duration total = Duration.ZERO;
        for (Map.Entry<TransactionReason, Long> entry : pointsByReason.entrySet()) {
            total = total.plus(of(entry.getKey(), entry.getValue()));
        }
        return total;
    }

    /**
     * Форматирует длительность для отображения: {@code "12 ч 35 мин"} либо {@code "35 мин"}.
     */
    public static String format(Duration duration) {
        long totalMinutes = duration.toMinutes();
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return hours > 0 ? hours + " ч " + minutes + " мин" : minutes + " мин";
    }
}
