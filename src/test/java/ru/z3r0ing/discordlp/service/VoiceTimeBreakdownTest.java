package ru.z3r0ing.discordlp.service;

import org.junit.jupiter.api.Test;
import ru.z3r0ing.discordlp.entity.TransactionReason;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class VoiceTimeBreakdownTest {

    @Test
    void viewerAndStreamerPointsLandInTheStreamPart() {
        VoiceTimeBreakdown breakdown = VoiceTimeBreakdown.ZERO
                .plus(TransactionReason.VOICE_VIEWER, 300L)
                .plus(TransactionReason.VOICE_STREAMER, 400L);

        assertThat(breakdown.withStream()).isEqualTo(Duration.ofMinutes(20));
        assertThat(breakdown.withoutStream()).isZero();
    }

    @Test
    void standardPointsLandInTheNoStreamPart() {
        VoiceTimeBreakdown breakdown = VoiceTimeBreakdown.ZERO.plus(TransactionReason.VOICE_STANDARD, 600L);

        assertThat(breakdown.withoutStream()).isEqualTo(Duration.ofMinutes(30));
        assertThat(breakdown.withStream()).isZero();
    }

    @Test
    void nonVoiceReasonsChangeNothing() {
        VoiceTimeBreakdown breakdown = VoiceTimeBreakdown.ZERO
                .plus(TransactionReason.ADMIN_MANUAL, 100_000L)
                .plus(TransactionReason.BET_WIN, 100_000L);

        assertThat(breakdown).isEqualTo(VoiceTimeBreakdown.ZERO);
        assertThat(breakdown.isZero()).isTrue();
    }

    @Test
    void totalIsTheSumOfBothParts() {
        VoiceTimeBreakdown breakdown = new VoiceTimeBreakdown(Duration.ofMinutes(35), Duration.ofMinutes(60));

        assertThat(breakdown.total()).isEqualTo(Duration.ofMinutes(95));
        assertThat(breakdown.isZero()).isFalse();
    }

    @Test
    void breakdownsAddUpPartByPart() {
        VoiceTimeBreakdown sum = new VoiceTimeBreakdown(Duration.ofMinutes(10), Duration.ofMinutes(5))
                .plus(new VoiceTimeBreakdown(Duration.ofMinutes(20), Duration.ofMinutes(1)));

        assertThat(sum.withStream()).isEqualTo(Duration.ofMinutes(30));
        assertThat(sum.withoutStream()).isEqualTo(Duration.ofMinutes(6));
    }
}
