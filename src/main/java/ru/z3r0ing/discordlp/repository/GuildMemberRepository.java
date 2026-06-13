package ru.z3r0ing.discordlp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.z3r0ing.discordlp.entity.GuildMember;

import java.util.List;
import java.util.Optional;

@Repository
public interface GuildMemberRepository extends JpaRepository<GuildMember, Long> {
    
    Optional<GuildMember> findByGuildIdAndUserId(String guildId, String userId);
    
    List<GuildMember> findByGuildId(String guildId);
}