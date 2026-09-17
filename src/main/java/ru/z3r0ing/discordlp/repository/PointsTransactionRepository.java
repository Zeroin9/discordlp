package ru.z3r0ing.discordlp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.z3r0ing.discordlp.entity.PointsTransaction;
import ru.z3r0ing.discordlp.entity.TransactionReason;

import java.util.Collection;
import java.util.List;

@Repository
public interface PointsTransactionRepository extends JpaRepository<PointsTransaction, Long> {
    
    List<PointsTransaction> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    /**
     * Суммирует начисления указанных участников в разрезе причины.
     * Используется дашбордом для пересчета баллов за голосовые каналы во время в конференции.
     */
    @Query("""
            SELECT new ru.z3r0ing.discordlp.repository.VoicePointsSum(t.member.id, t.reason, SUM(t.amount))
            FROM PointsTransaction t
            WHERE t.member.id IN :memberIds AND t.reason IN :reasons
            GROUP BY t.member.id, t.reason
            """)
    List<VoicePointsSum> sumPointsByMemberAndReason(@Param("memberIds") Collection<Long> memberIds,
                                                    @Param("reasons") Collection<TransactionReason> reasons);
}
