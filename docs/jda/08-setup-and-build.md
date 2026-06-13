# Настройка и Сборка (Setup & Build)

### Connect Bot with JDA

Source: https://github.com/discord-jda/jda/wiki/10)-FAQ

Basic example of how to connect your bot using JDABuilder and your bot token. Ensure you have a bot account created and its token.

```java
public static void main(String[] args) throws Exception
{
    JDABuilder.createDefault(THE_TOKEN).build();
}
```

--------------------------------

### State Accessor for Permission Overrides

Source: https://github.com/discord-jda/jda/blob/master/src/main/java/net/dv8tion/jda/internal/entities/channel/mixin/README.md

Example of a concrete channel type implementing a state accessor required by a mixin.

```java
TLongObjectMap<PermissionOverride> overrides = MiscUtil.newLongMap();

@Override
public TLongObjectMap<PermissionOverride> getPermissionOverrideMap() {
  return overrides;
}
```

--------------------------------

### Gradle Build Configuration for JDA

Source: https://github.com/discord-jda/jda/wiki/2)-Eclipse-Setup

This script configures a Gradle project for JDA development. It includes plugins for Java, application, and shadow JAR creation. Ensure you replace '#.#.#' with the desired JDA version and 'com.example.jda.Bot' with your main class.

```gradle
plugins {
  id("java")
  id("application")
  id("com.github.johnrengelman.shadow") version "6.0.0"
}

mainClassName = "com.example.jda.Bot"

version '1.0'

sourceCompatibility = 1.8

repositories {
  mavenCentral()
  maven { // on kotlin dsl use `maven("https://m2.dv8tion.net/releases")` instead
      url "https://m2.dv8tion.net/releases"
  }
}

dependencies {
  implementation("net.dv8tion:JDA:#.#.#_###")
}

compileJava.options.encoding = "UTF-8"

```

--------------------------------

### IntelliJ IDEA Gradle Build Configuration for JDA

Source: https://github.com/discord-jda/jda/wiki/2)-IntelliJ-IDEA-Setup

This snippet configures the build.gradle file for a JDA project, including plugins, main class, JDA version, repositories, and dependencies. Ensure you replace 'JDA_VERSION_HERE' with the actual version and 'com.example.jda.Bot' with your main class path.

```gradle
plugins {
    id'application'
    id'com.github.johnrengelman.shadow' version '5.2.0'
}

mainClassName = 'com.example.jda.Bot'

version '1.0'
def jdaVersion = 'JDA_VERSION_HERE'

sourceCompatibility = targetCompatibility = 1.8

repositories {
    mavenCentral()
    maven { // on kotlin dsl use `maven("https://m2.dv8tion.net/releases")` instead
        url "https://m2.dv8tion.net/releases"
    }
}

dependencies {
    implementation("net.dv8tion:JDA:$jdaVersion")
}

compileJava.options.encoding = 'UTF-8'
```

--------------------------------

### Minimize Jar with Shade Plugin (Maven)

Source: https://github.com/discord-jda/jda/wiki/10)-FAQ

Configure the maven-shade-plugin with minimizeJar set to true to reduce the final jar size.

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.2.1</version>
    <configuration>
        <minimizeJar>true</minimizeJar>
    </configuration>
    <executions>
      <execution>
        <phase>package</phase>
        <goals>
          <goal>shade</goal>
        </goals>
      </execution>
    </executions>
</plugin>
```

--------------------------------

### Add Logback Dependency (Gradle)

Source: https://github.com/discord-jda/jda/wiki/Logging-Setup

Add the logback-classic dependency to your Gradle project's build file. Newer Gradle versions use 'implementation' instead of 'compile'.

```gradle
dependencies {
    implementation("ch.qos.logback:logback-classic:1.2.8")
}
```

--------------------------------

### Build JDA Instance with Bot Token

Source: https://github.com/discord-jda/jda/wiki/3)-Getting-Started

Instantiate and build a JDA (Java Discord API) client using your bot's token. Ensure the BOT_TOKEN is securely stored and not exposed.

```java
public static void main(String[] arguments) throws Exception
{
    JDA api = JDABuilder.createDefault(BOT_TOKEN).build();
}
```

--------------------------------

### Logback Configuration

Source: https://github.com/discord-jda/jda/wiki/Logging-Setup

Basic logback.xml configuration for JDA projects, setting up a console appender with a custom pattern including thread, shard info, logger name, level, and message. The root logger is set to 'info' level.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} %boldCyan(%-34.-34thread) %red(%10.10X{jda.shard}) %boldGreen(%-15.-15logger{0}) %highlight(%-6level) %msg%n</pattern>
        </encoder>
    </appender>

    <root level="info">
        <appender-ref ref="STDOUT" />
    </root>
</configuration>
```

--------------------------------

### Minimize Jar with Shadow Plugin (Gradle)

Source: https://github.com/discord-jda/jda/wiki/10)-FAQ

Use the shadowJar task with the minimize() option to reduce the final jar size when using Gradle.

```gradle
shadowJar {
    minimize()
}
```

--------------------------------

### Add Logback Dependency (Maven)

Source: https://github.com/discord-jda/jda/wiki/Logging-Setup

Add the logback-classic dependency to your Maven project's pom.xml file.

```xml
<dependency>
  <groupId>ch.qos.logback</groupId>
  <artifactId>logback-classic</artifactId>
  <version>1.2.8</version>
</dependency>
```

--------------------------------

### Maven Build Plugins Configuration

Source: https://github.com/discord-jda/jda/wiki/2)-Eclipse-Setup

Configures the maven-compiler-plugin to use Java 8 and the maven-shade-plugin to package the application. Replace 'YourMainClass' with your actual main class path.

```xml
<build>
    <plugins>
        <plugin>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.8.1</version>
            <configuration>
                <source>1.8</source>
                <target>1.8</target>
            </configuration>
        </plugin>
        <plugin>
            <artifactId>maven-shade-plugin</artifactId>
            <version>3.2.4</version>
            <configuration>
                <transformers>
                    <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                        <mainClass>YourMainClass</mainClass> <!-- You have to replace this with a path to your main class like me.myname.mybotproject.Main -->
                    </transformer>
                </transformers>
                <createDependencyReducedPom>false</createDependencyReducedPom>
            </configuration>
            <executions>
                <execution>
                    <phase>package</phase>
                    <goals>
                        <goal>shade</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

--------------------------------

### Bracket Placement and Indentation

Source: https://github.com/discord-jda/jda/wiki/6)-JDA-Structure-Guide

Demonstrates JDA's convention of placing curly brackets on their own lines and using 4-space indentation for all code blocks. This applies to methods, loops, conditional statements, and class scopes.

```java
public void someMethod()
{
    this.works.well();
    for (Each element : from())
    {
        System.out.println(element);
    }
}
```

--------------------------------

### Enable Debug Logs in Logback

Source: https://github.com/discord-jda/jda/wiki/Logging-Setup

Modify the root logger level in logback.xml to 'debug' to enable debug logging.

```xml
<root level="debug">
  ...
</root>
```

--------------------------------

### Add JDA Dependency

Source: https://github.com/discord-jda/jda/wiki/2)-Eclipse-Setup

Includes the JDA library as a project dependency. Ensure 'X.X.X_XXX' is replaced with the latest version.

```xml
<dependencies>
    <dependency>
      <groupId>net.dv8tion</groupId>
      <artifactId>JDA</artifactId>
      <version>X.X.X_XXX</version>
    </dependency>
  </dependencies>
```

--------------------------------

### Maven Project Properties

Source: https://github.com/discord-jda/jda/wiki/2)-Eclipse-Setup

Configures project encoding and forces Java 8, which is required by JDA.

```xml
<properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
  </properties>
```

--------------------------------

### Calculated Getter Implementation

Source: https://github.com/discord-jda/jda/blob/master/src/main/java/net/dv8tion/jda/internal/entities/channel/mixin/README.md

Shows a default implementation for a calculated getter method using a state accessor.

```java
// ---- Default implementations of interface ----
default List<PermissionOverride> getPermissionOverrides() {
  TLongObjectMap<PermissionOverride> overrides = getPermissionOverrideMap();
  return Arrays.asList(overrides.values(new PermissionOverride[overrides.size()]));
}

// ---- State Accessors ----
TLongObjectMap<PermissionOverride> getPermissionOverrideMap();
```

--------------------------------

### JavaDoc Paragraph and Line Break Formatting

Source: https://github.com/discord-jda/jda/wiki/6)-JDA-Structure-Guide

Illustrates JDA's specific JavaDoc formatting, using '<br>' for line breaks and '<p>' for new paragraphs. Note that '<br>' and '<p>' tags are not closed.

```java
/**
 * This is my first line
 * <br>And this is my second line
 */
```

```java
/**
 * Either do this
 * 
 * <p>Or do this
 * <p>
 * You can decide.
 */
```

--------------------------------

### JDA Maven Dependency for Testing Builds

Source: https://github.com/discord-jda/jda/wiki/10)-FAQ

Specifies the JDA dependency in Maven for testing builds, using the groupId 'com.github.DV8FromTheWorld' and artifactId 'JDA'.

```xml
<dependency>
    <groupId>com.github.DV8FromTheWorld</groupId>
    <artifactId>JDA</artifactId>
    <version>VERSION</version>
</dependency>
```

--------------------------------

### Manually Shut Down HTTP/2 Threads

Source: https://github.com/discord-jda/jda/wiki/19)-Troubleshooting

When JDA shuts down, HTTP/2 connections might keep the JVM running. This snippet shows how to manually terminate those threads.

```java
OkHttpClient client = jda.getHttpClient();
client.connectionPool().evictAll();
client.dispatcher().executorService().shutdown();
```

--------------------------------

### Add JDA Dependency to Maven POM

Source: https://github.com/discord-jda/jda/wiki/2)-Netbeans-Setup

Add this dependency to your Netbeans project's pom.xml to include JDA in your project.

```xml
<dependencies>
    <dependency>
        <groupId>net.dv8tion</groupId>
        <artifactId>JDA</artifactId>
        <version>3.8.3_464</version>
    </dependency>
</dependencies>
```