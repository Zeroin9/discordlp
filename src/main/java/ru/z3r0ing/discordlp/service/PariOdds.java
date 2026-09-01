package ru.z3r0ing.discordlp.service;

import java.math.BigDecimal;

/**
 * Коэффициенты пари, посчитанные по текущему состоянию пула.
 * <p>
 * Пока пари открыто, коэффициенты меняются с каждой новой ставкой: чем больше поставлено
 * на вариант, тем ниже выплата на единицу ставки. Итоговым считается коэффициент,
 * зафиксированный в момент объявления исхода.
 *
 * @param yesCoefficient коэффициент варианта «Да», {@code null} — если на него никто не поставил
 * @param noCoefficient  коэффициент варианта «Нет», {@code null} — если на него никто не поставил
 */
public record PariOdds(long totalPool, long prizePool, BigDecimal yesCoefficient, BigDecimal noCoefficient) {

    public static PariOdds of(PariStats stats, BigDecimal commissionRate) {
        long totalPool = stats.totalPool();
        long prizePool = PariPayoutCalculator.prizePool(totalPool, commissionRate);
        return new PariOdds(
                totalPool,
                prizePool,
                PariPayoutCalculator.coefficient(prizePool, stats.yesPool()),
                PariPayoutCalculator.coefficient(prizePool, stats.noPool())
        );
    }

    public BigDecimal coefficient(boolean option) {
        return option ? yesCoefficient : noCoefficient;
    }

    /** Комиссия организатора, удержанная из общего пула. */
    public long commission() {
        return totalPool - prizePool;
    }
}
