package ru.z3r0ing.discordlp.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PariOddsTest {

    private static final BigDecimal FIVE_PERCENT = new BigDecimal("0.05");

    @Test
    void coefficientsAreDerivedFromCurrentPools() {
        PariOdds odds = PariOdds.of(new PariStats(2, 500L, 1, 500L), FIVE_PERCENT);

        assertThat(odds.totalPool()).isEqualTo(1_000L);
        assertThat(odds.prizePool()).isEqualTo(950L);
        assertThat(odds.commission()).isEqualTo(50L);
        assertThat(odds.yesCoefficient()).isEqualByComparingTo("1.9");
        assertThat(odds.noCoefficient()).isEqualByComparingTo("1.9");
    }

    @Test
    void underdogHasHigherCoefficient() {
        PariOdds odds = PariOdds.of(new PariStats(5, 900L, 1, 100L), FIVE_PERCENT);

        assertThat(odds.yesCoefficient()).isLessThan(odds.noCoefficient());
        assertThat(odds.coefficient(true)).isEqualTo(odds.yesCoefficient());
        assertThat(odds.coefficient(false)).isEqualTo(odds.noCoefficient());
    }

    @Test
    void optionWithoutBetsHasNoCoefficient() {
        PariOdds odds = PariOdds.of(new PariStats(1, 300L, 0, 0L), FIVE_PERCENT);

        assertThat(odds.yesCoefficient()).isNotNull();
        assertThat(odds.noCoefficient()).isNull();
    }

    @Test
    void emptyPariHasNoCoefficientsAndNoCommission() {
        PariOdds odds = PariOdds.of(new PariStats(0, 0L, 0, 0L), FIVE_PERCENT);

        assertThat(odds.totalPool()).isZero();
        assertThat(odds.prizePool()).isZero();
        assertThat(odds.commission()).isZero();
        assertThat(odds.yesCoefficient()).isNull();
        assertThat(odds.noCoefficient()).isNull();
    }

    @Test
    void withoutCommissionThePrizePoolIsTheWholePool() {
        PariOdds odds = PariOdds.of(new PariStats(1, 400L, 1, 600L), BigDecimal.ZERO);

        assertThat(odds.prizePool()).isEqualTo(1_000L);
        assertThat(odds.commission()).isZero();
        assertThat(odds.yesCoefficient()).isEqualByComparingTo("2.5");
    }
}
