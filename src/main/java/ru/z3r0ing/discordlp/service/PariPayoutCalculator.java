package ru.z3r0ing.discordlp.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Тотализаторная математика пари: комиссия, призовой фонд, коэффициент и выплата по ставке.
 * <p>
 * Класс не имеет состояния и не ходит в базу — вся арифметика выполняется целыми числами
 * с округлением вниз, поэтому расчет воспроизводим и сумма выплат никогда не превышает
 * призовой фонд. Остаток от округления (не более 1 LP на победителя) остается вместе
 * с комиссией и не начисляется никому.
 */
public final class PariPayoutCalculator {

    /** Знаков после запятой у коэффициента: хватает для отображения и аудита. */
    public static final int COEFFICIENT_SCALE = 4;

    private PariPayoutCalculator() {
    }

    /**
     * Комиссия организатора, удерживаемая из общего пула.
     *
     * @param totalPool сумма всех ставок пари
     * @param rate      доля комиссии, например {@code 0.05} для 5%
     */
    public static long commission(long totalPool, BigDecimal rate) {
        if (totalPool <= 0 || rate == null || rate.signum() <= 0) {
            return 0L;
        }
        return BigDecimal.valueOf(totalPool)
                .multiply(rate)
                .setScale(0, RoundingMode.FLOOR)
                .longValueExact();
    }

    /** Призовой фонд: общий пул за вычетом комиссии. Именно он делится между победителями. */
    public static long prizePool(long totalPool, BigDecimal rate) {
        if (totalPool <= 0) {
            return 0L;
        }
        return totalPool - commission(totalPool, rate);
    }

    /**
     * Коэффициент варианта: во сколько раз вырастет ставка, если победит именно он.
     *
     * @return {@code null}, если на вариант еще никто не поставил и коэффициент не определен
     */
    public static BigDecimal coefficient(long prizePool, long optionPool) {
        if (optionPool <= 0 || prizePool <= 0) {
            return null;
        }
        return BigDecimal.valueOf(prizePool)
                .divide(BigDecimal.valueOf(optionPool), COEFFICIENT_SCALE, RoundingMode.DOWN);
    }

    /**
     * Выплата по выигравшей ставке: доля призового фонда, пропорциональная размеру ставки.
     * <p>
     * Считается как {@code amount * prizePool / winningSum} без промежуточного округления
     * коэффициента, поэтому результат не «плывет» от порядка обработки ставок.
     */
    public static long payout(long betAmount, long prizePool, long winningSum) {
        if (betAmount <= 0 || prizePool <= 0 || winningSum <= 0) {
            return 0L;
        }
        return BigDecimal.valueOf(betAmount)
                .multiply(BigDecimal.valueOf(prizePool))
                .divide(BigDecimal.valueOf(winningSum), 0, RoundingMode.FLOOR)
                .longValueExact();
    }
}
