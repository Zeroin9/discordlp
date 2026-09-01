package ru.z3r0ing.discordlp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "paris")
public class Pari {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", nullable = false)
    private String guildId;

    @Column(name = "author_id", nullable = false)
    private String authorId;

    @Column(name = "author_name")
    private String authorName;

    @Column(name = "title", nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PariStatus status;

    /** Победивший вариант: true — «Да», false — «Нет», null — исход не объявлен. */
    @Column(name = "winning_option")
    private Boolean winningOption;

    /** Доля комиссии организатора, зафиксированная при создании пари (например 0.0500 — 5%). */
    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal commissionRate = BigDecimal.ZERO;

    /** Сумма всех ставок на момент объявления исхода. */
    @Column(name = "total_pool")
    private Long totalPool;

    /** Призовой фонд: общий пул за вычетом комиссии. Делится между победителями. */
    @Column(name = "prize_pool")
    private Long prizePool;

    /** Сумма ставок на победивший вариант. Ноль означает, что победителей нет. */
    @Column(name = "winning_sum")
    private Long winningSum;

    /** Итоговый коэффициент победившего варианта, {@code null} — если победителей нет. */
    @Column(name = "winning_coefficient", precision = 18, scale = 4)
    private BigDecimal winningCoefficient;

    @Column(name = "channel_id")
    private String channelId;

    @Column(name = "message_id")
    private String messageId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Момент перехода в терминальный статус (FINISHED/CANCELED). */
    @Column(name = "closed_at")
    private Instant closedAt;

    /** Момент завершения расчета по всем ставкам. Пока null — расчет не доведен до конца. */
    @Column(name = "settled_at")
    private Instant settledAt;
}
