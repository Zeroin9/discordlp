package ru.z3r0ing.discordlp.service;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import org.springframework.stereotype.Service;
import ru.z3r0ing.discordlp.entity.GuildMember;
import ru.z3r0ing.discordlp.repository.GuildMemberRepository;

@Service
@RequiredArgsConstructor
public class GuildMemberService {

    private final GuildMemberRepository guildMemberRepository;

    public GuildMember getOrCreateMember(Guild guild, User user) {
        return guildMemberRepository
                .findByGuildIdAndUserId(guild.getId(), user.getId())
                .orElseGet(() -> {
                    GuildMember newMember = new GuildMember();
                    newMember.setGuildId(guild.getId());
                    newMember.setUserId(user.getId());
                    newMember.setBalance(0L);
                    newMember.setUserName(user.getName());
                    newMember.setGuildName(guild.getName());
                    return guildMemberRepository.save(newMember);
                });
    }
}
