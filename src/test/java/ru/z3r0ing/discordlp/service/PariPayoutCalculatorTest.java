package ru.z3r0ing.discordlp.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тотализаторная арифметика: комиссия, призовой фонд, коэффициент и выплата.
 */
class PariPayoutCalculatorTest {

    private static final BigDecimal FIVE_PERCENT = new BigDecimal("0.05");

    // --- комиссия и призовой фонд ---

    @Test
    void commissionIsShareOfTotalPool() {
        assertThat(PariPayoutCalculator.commission(1_000L, FIVE_PERCENT)).isEqualTo(50L);
        assertThat(PariPayoutCalculator.prizePool(1_000L, FIVE_PERCENT)).isEqualTo(950L);
    }

    @Test
    void commissionIsRoundedDownInFavourOfPlayers() {
        // 999 * 0.05 = 49.95 — в призовой фонд уходит на один LP больше
        assertThat(PariPayoutCalculator.commission(999L, FIVE_PERCENT)).isEqualTo(49L);
        assertThat(PariPayoutCalculator.prizePool(999L, FIVE_PERCENT)).isEqualTo(950L);
    }

    @Test
    void zeroAndMissingRateLeaveWholePoolInPlay() {
        assertThat(PariPayoutCalculator.commission(1_000L, BigDecimal.ZERO)).isZero();
        assertThat(PariPayoutCalculator.commission(1_000L, null)).isZero();
        assertThat(PariPayoutCalculator.prizePool(1_000L, BigDecimal.ZERO)).isEqualTo(1_000L);
        assertThat(PariPayoutCalculator.prizePool(1_000L, null)).isEqualTo(1_000L);
    }

    @Test
    void emptyPoolYieldsNothing() {
        assertThat(PariPayoutCalculator.commission(0L, FIVE_PERCENT)).isZero();
        assertThat(PariPayoutCalculator.prizePool(0L, FIVE_PERCENT)).isZero();
    }

    // --- коэффициент ---

    @Test
    void coefficientIsPrizePoolPerUnitOfStake() {
        assertThat(PariPayoutCalculator.coefficient(950L, 500L)).isEqualByComparingTo("1.9");
        assertThat(PariPayoutCalculator.coefficient(950L, 100L)).isEqualByComparingTo("9.5");
    }

    @Test
    void coefficientIsTruncatedNotRounded() {
        // 950 / 300 = 3.16666... — коэффициент не должен «подрасти» при округлении
        assertThat(PariPayoutCalculator.coefficient(950L, 300L)).isEqualByComparingTo("3.1666");
    }

    @Test
    void favouriteSideCanHaveCoefficientBelowOne() {
        // почти все поставили на один вариант: комиссия делает выплату меньше ставки
        assertThat(PariPayoutCalculator.coefficient(950L, 1_000L)).isEqualByComparingTo("0.95");
    }

    @Test
    void coefficientIsUndefinedWithoutBetsOnTheOption() {
        assertThat(PariPayoutCalculator.coefficient(950L, 0L)).isNull();
        assertThat(PariPayoutCalculator.coefficient(0L, 500L)).isNull();
    }

    // --- выплата ---

    @Test
    void payoutIsProportionalShareOfPrizePool() {
        // пул 1000, комиссия 5%, на победивший вариант поставлено 500
        assertThat(PariPayoutCalculator.payout(300L, 950L, 500L)).isEqualTo(570L);
        assertThat(PariPayoutCalculator.payout(200L, 950L, 500L)).isEqualTo(380L);
    }

    @Test
    void payoutsNeverExceedThePrizePool() {
        long prizePool = 950L;
        long winningSum = 300L;

        long paid = PariPayoutCalculator.payout(100L, prizePool, winningSum)
                + PariPayoutCalculator.payout(100L, prizePool, winningSum)
                + PariPayoutCalculator.payout(100L, prizePool, winningSum);

        assertThat(paid).isLessThanOrEqualTo(prizePool);
        // остаток от округления вниз (950/3 = 316.66) остается неразделенным
        assertThat(paid).isEqualTo(948L);
    }

    @Test
    void soleWinnerTakesTheWholePrizePool() {
        assertThat(PariPayoutCalculator.payout(500L, 950L, 500L)).isEqualTo(950L);
    }

    @Test
    void payoutIsZeroWhenThereIsNothingToShare() {
        assertThat(PariPayoutCalculator.payout(100L, 0L, 500L)).isZero();
        assertThat(PariPayoutCalculator.payout(100L, 950L, 0L)).isZero();
        assertThat(PariPayoutCalculator.payout(0L, 950L, 500L)).isZero();
    }

    @Test
    void largePoolsDoNotOverflow() {
        long bet = 100_000_000L;
        long prizePool = 1_000_000_000_000L;
        long winningSum = 200_000_000L;

        assertThat(PariPayoutCalculator.payout(bet, prizePool, winningSum)).isEqualTo(500_000_000_000L);
    }

    // --- связка коэффициента и выплаты ---

    @Test
    void payoutMatchesTheAdvertisedCoefficient() {
        long prizePool = PariPayoutCalculator.prizePool(1_000L, FIVE_PERCENT);
        BigDecimal coefficient = PariPayoutCalculator.coefficient(prizePool, 500L);

        long expected = coefficient.multiply(BigDecimal.valueOf(300L)).longValue();

        assertThat(PariPayoutCalculator.payout(300L, prizePool, 500L)).isEqualTo(expected);
    }
}
