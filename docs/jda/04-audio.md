# Аудио и Голосовые каналы (Audio & Voice)

### Get Voice Channel by Name

Source: https://github.com/discord-jda/jda/wiki/4)-Making-a-Music-Bot

Retrieves a VoiceChannel from a Guild by its name. It gets the first match.

```Java
VoiceChannel myChannel = guild.getVoiceChannelsByName(CHANNEL_NAME, true).get(0);
```

--------------------------------

### Basic Music Bot Implementation

Source: https://github.com/discord-jda/jda/wiki/4)-Making-a-Music-Bot

A basic JDA bot that connects to a voice channel named 'music' and starts playing audio when a user types '!play'. Requires a custom MySendHandler implementation.

```Java
public class MusicBot extends ListenerAdapter 
{
    public static void main(String[] args)
    throws IllegalArgumentException, LoginException, RateLimitedException
    {
        JDABuilder.createDefault(args[0]) // Use token provided as JVM argument
            .addEventListeners(new MusicBot()) // Register new MusicBot instance as EventListener
            .build(); // Build JDA - connect to discord
    }

    // Note that we are using GuildMessageReceivedEvent to only include messages from a Guild!
    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) 
    {
        // This makes sure we only execute our code when someone sends a message with "!play"
        if (!event.getMessage().getContentRaw().startsWith("!play")) return;
        // Now we want to exclude messages from bots since we want to avoid command loops in chat!
        // this will include own messages as well for bot accounts
        // if this is not a bot make sure to check if this message is sent by yourself!
        if (event.getAuthor().isBot()) return;
        Guild guild = event.getGuild();
        // This will get the first voice channel with the name "music"
        // matching by voiceChannel.getName().equalsIgnoreCase("music")
        VoiceChannel channel = guild.getVoiceChannelsByName("music", true).get(0);
        AudioManager manager = guild.getAudioManager();

        // MySendHandler should be your AudioSendHandler implementation
        manager.setSendingHandler(new MySendHandler());
        // Here we finally connect to the target voice channel 
        // and it will automatically start pulling the audio from the MySendHandler instance
        manager.openAudioConnection(channel);
    }
}
```

--------------------------------

### Get AudioManager

Source: https://github.com/discord-jda/jda/wiki/4)-Making-a-Music-Bot

Retrieves the AudioManager for a Guild, which is used to manage audio connections.

```Java
AudioManager audioManager = guild.getAudioManager();
```

--------------------------------

### Get Voice Channel from Member

Source: https://github.com/discord-jda/jda/wiki/4)-Making-a-Music-Bot

Retrieves the VoiceChannel a specific member is currently in.

```Java
VoiceChannel myChannel = member.getVoiceState().getChannel();
```

--------------------------------

### Get Voice Channel by ID

Source: https://github.com/discord-jda/jda/wiki/4)-Making-a-Music-Bot

Retrieves a VoiceChannel from a Guild using its ID.

```Java
VoiceChannel myChannel = guild.getVoiceChannelById(CHANNEL_ID);
```

--------------------------------

### Open Audio Connection

Source: https://github.com/discord-jda/jda/wiki/4)-Making-a-Music-Bot

Establishes an audio connection to a specified VoiceChannel.

```Java
audioManager.openAudioConnection(myChannel);
```

--------------------------------

### Configure JDA with NativeAudioSendFactory

Source: https://github.com/discord-jda/jda/blob/master/README.md

Use NativeAudioSendFactory to integrate udpqueue with JDA, avoiding GC pauses for continuous audio playback. Note that this creates an extra UDP-Client which may affect audio receive functionality.

```java
JDABuilder builder = JDABuilder.createDefault(BOT_TOKEN)
    .setAudioSendFactory(new NativeAudioSendFactory());
```

--------------------------------

### State Accessor for Connected Members Map

Source: https://github.com/discord-jda/jda/blob/master/src/main/java/net/dv8tion/jda/internal/entities/channel/mixin/README.md

Illustrates exposing an internal map via a state accessor to manage connected members in audio channels.

```java
TLongObjectMap<Member> getConnectedMembersMap();
```

--------------------------------

### Gradle Dependency for JDA

Source: https://github.com/discord-jda/jda/blob/master/README.md

Add the JDA library to your Gradle project. Replace $version with the latest version. Optionally exclude opus-java and tink if audio processing is not required.

```gradle
repositories {
    mavenCentral()
}

dependencies {
    implementation("net.dv8tion:JDA:$version") { // replace $version with the latest version
      // Optionally disable audio natives to reduce jar size by excluding `opus-java` and `tink`
      // Gradle DSL:
      // exclude module: 'opus-java' // required for encoding audio into opus, not needed if audio is already provided in opus encoding
      // exclude module: 'tink' // required for encrypting and decrypting audio
      // Kotlin DSL:
      // exclude(module="opus-java") // required for encoding audio into opus, not needed if audio is already provided in opus encoding
      // exclude(module="tink") // required for encrypting and decrypting audio
    }
}
```

--------------------------------

### Set Audio Send Handler

Source: https://github.com/discord-jda/jda/wiki/4)-Making-a-Music-Bot

Registers an AudioSendHandler with the AudioManager to send audio data to the connected voice channel. Only one handler can be used per Guild.

```Java
manager.setSendingHandler(myAudioSendHandler);
```

--------------------------------

### Maven Dependency for JDA

Source: https://github.com/discord-jda/jda/blob/master/README.md

Include the JDA library in your Maven project. Replace $version with the latest version. Optional exclusions for opus-java and tink are provided to reduce JAR size if audio features are not needed.

```xml
<dependency>
    <groupId>net.dv8tion</groupId>
    <artifactId>JDA</artifactId>
    <version>$version</version> <!-- replace $version with the latest version -->
    <!-- Optionally disable audio natives to reduce jar size by excluding `opus-java` and `tink` -->
    <exclusions>
        <!-- required for encoding audio into opus, not needed if audio is already provided in opus encoding
        <exclusion>
            <groupId>club.minnced</groupId>
            <artifactId>opus-java</artifactId>
        </exclusion> -->
        <!-- required for encrypting and decrypting audio
        <exclusion>
            <groupId>com.google.crypto.tink</groupId>
            <artifactId>tink</artifactId>
        </exclusion> -->
    </exclusions>
</dependency>
```

--------------------------------

### Exclude Opus Natives with Gradle

Source: https://github.com/discord-jda/jda/wiki/10)-FAQ

Exclude the opus-java module when using Gradle to reduce jar size if audio encoding/decoding is not needed.

```gradle
implementation("net.dv8tion:JDA:$VERSION") {
    exclude module: "opus-java"
}
```

--------------------------------

### Exclude Opus Natives with Maven

Source: https://github.com/discord-jda/jda/wiki/10)-FAQ

Exclude the opus-java artifact from the JDA dependency in Maven to reduce jar size if audio encoding/decoding is not required.

```xml
<dependency>
    <groupId>net.dv8tion</groupId>
    <artifactId>JDA</artifactId>
    <version>$VERSION</version>
    <exclusions>
        <exclusion>
            <groupId>club.minnced</groupId>
            <artifactId>opus-java</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```