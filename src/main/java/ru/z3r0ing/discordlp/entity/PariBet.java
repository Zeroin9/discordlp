package ru.z3r0ing.discordlp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(name = "pari_bets", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"pari_id", "member_id"})
})
public class PariBet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pari_id", nullable = false)
    private Pari pari;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private GuildMember member;

    /** Выбранный вариант: true — «Да», false — «Нет». */
    @Column(name = "bet_option", nullable = false)
    private Boolean option;

    @Column(name = "amount", nullable = false)
    private Long amount;

    /** Признак того, что по ставке уже проведен расчет. Обеспечивает идемпотентность выплат. */
    @Column(name = "settled", nullable = false)
    private boolean settled = false;

    /** Фактически начисленная сумма: 2x при выигрыше, 1x при возврате, 0 при проигрыше. */
    @Column(name = "payout")
    private Long payout;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "settled_at")
    private Instant settledAt;
}
