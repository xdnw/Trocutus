# Trocutus
Trocutus is a discord bot for https://trounced.net

Public version invite:
<https://discord.com/api/oauth2/authorize?client_id=1110877183985061940&permissions=395606879321&scope=bot>

Useful links:
 - [Guild setup](https://github.com/xdnw/Trocutus/wiki)
 - [Commands](https://github.com/xdnw/Trocutus/wiki/commands)
 - [Arguments](https://github.com/xdnw/Trocutus/wiki/arguments)
 - [Placeholders](https://github.com/xdnw/Trocutus/wiki/kingdom_placeholders)

## Compiling
Download Java JDK 17 (or newer)
https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html

Download the source from GitHub
<https://github.com/xdnw/Trocutus/archive/refs/heads/main.zip>

Use the provided gradle wrapper to build
```bash
./gradlew build shadowJar
```
Find the compiled jar at `trocutus/build/libs` called `shadowJar-Trocutus-1.0-SNAPSHOT.jar`

## Running the compiled .jar
From terminal
`java -jar shadowJar-Trocutus-1.0-SNAPSHOT.jar`

If its your first time running, close the program and edit the generated `config/config.yaml` file

