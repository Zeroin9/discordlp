package ru.z3r0ing.discordlp.service;

import org.junit.jupiter.api.Test;
import ru.z3r0ing.discordlp.entity.TransactionReason;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VoiceTimeTest {

    @Test
    void oneAwardOfEachReasonIsOneInterval() {
        Duration interval = Duration.ofSeconds(VoiceTime.AWARD_INTERVAL_SECONDS);

        assertThat(VoiceTime.of(TransactionReason.VOICE_STANDARD, VoiceTime.POINTS_STANDARD)).isEqualTo(interval);
        assertThat(VoiceTime.of(TransactionReason.VOICE_VIEWER, VoiceTime.POINTS_VIEWER)).isEqualTo(interval);
        assertThat(VoiceTime.of(TransactionReason.VOICE_STREAMER, VoiceTime.POINTS_STREAMER)).isEqualTo(interval);
    }

    @Test
    void scalesLinearlyWithPoints() {
        assertThat(VoiceTime.of(TransactionReason.VOICE_STANDARD, 1_200L)).isEqualTo(Duration.ofMinutes(60));
        assertThat(VoiceTime.of(TransactionReason.VOICE_STREAMER, 1_200L)).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void nonVoiceReasonsDoNotCountAsTimeInVoice() {
        assertThat(VoiceTime.of(TransactionReason.ADMIN_MANUAL, 1_000_000L)).isZero();
        assertThat(VoiceTime.of(TransactionReason.BET_WIN, 1_000_000L)).isZero();
    }

    @Test
    void negativeAndZeroPointsGiveZeroTime() {
        assertThat(VoiceTime.of(TransactionReason.VOICE_STANDARD, 0L)).isZero();
        assertThat(VoiceTime.of(TransactionReason.VOICE_STANDARD, -500L)).isZero();
    }

    @Test
    void totalSumsEveryReason() {
        Duration total = VoiceTime.total(Map.of(
                TransactionReason.VOICE_STANDARD, 600L,
                TransactionReason.VOICE_VIEWER, 300L,
                TransactionReason.VOICE_STREAMER, 2_400L
        ));

        assertThat(total).isEqualTo(Duration.ofMinutes(100));
    }

    @Test
    void totalOfNothingIsZero() {
        assertThat(VoiceTime.total(Map.of())).isZero();
    }

    @Test
    void formatsHoursAndMinutes() {
        assertThat(VoiceTime.format(Duration.ZERO)).isEqualTo("0 мин");
        assertThat(VoiceTime.format(Duration.ofMinutes(35))).isEqualTo("35 мин");
        assertThat(VoiceTime.format(Duration.ofMinutes(60))).isEqualTo("1 ч 0 мин");
        assertThat(VoiceTime.format(Duration.ofMinutes(755))).isEqualTo("12 ч 35 мин");
    }

    @Test
    void voiceReasonsCoverEveryVoiceTransactionReason() {
        assertThat(VoiceTime.VOICE_REASONS).containsExactlyInAnyOrder(
                TransactionReason.VOICE_STANDARD,
                TransactionReason.VOICE_VIEWER,
                TransactionReason.VOICE_STREAMER);
    }
}
