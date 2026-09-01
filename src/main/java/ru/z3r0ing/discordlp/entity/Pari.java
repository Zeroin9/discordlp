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
