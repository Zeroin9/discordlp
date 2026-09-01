package ru.z3r0ing.discordlp.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.z3r0ing.discordlp.entity.GuildMember;

import java.util.List;
import java.util.Optional;

@Repository
public interface GuildMemberRepository extends JpaRepository<GuildMember, Long> {
    
    Optional<GuildMember> findByGuildIdAndUserId(String guildId, String userId);
    
    List<GuildMember> findByGuildId(String guildId);

    /**
     * Блокирует строку баланса на время транзакции (SELECT ... FOR UPDATE).
     * Защищает от двойных трат при одновременных операциях со ставками.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM GuildMember m WHERE m.id = :id")
    Optional<GuildMember> findByIdForUpdate(@Param("id") Long id);
}