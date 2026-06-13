package ru.z3r0ing.discordlp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.z3r0ing.discordlp.entity.PointsTransaction;

import java.util.List;

@Repository
public interface PointsTransactionRepository extends JpaRepository<PointsTransaction, Long> {
    
    List<PointsTransaction> findByMemberIdOrderByCreatedAtDesc(Long memberId);
}