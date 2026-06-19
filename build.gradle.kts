plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.0.2"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

repositories {
    mavenCentral()
    mavenLocal()

    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.23-alpha")
    compileOnly("com.stephanofer:networkplayersettings:1.0.0-SNAPSHOT")

    implementation("com.stephanofer.boostedyaml:boosted-yaml:1.3.7")
    implementation("org.incendo:cloud-paper:2.0.0-beta.15")
    implementation("org.incendo:cloud-minecraft-extras:2.0.0-beta.15")

}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {

    processResources {
        val props = mapOf("version" to version )
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }

    shadowJar {
        relocate("dev.dejvokep.boostedyaml", "com.stephanofer.lobbyHera.libs.boostedyaml")
        relocate("org.incendo.cloud", "com.stephanofer.lobbyHera.libs.cloud")
        relocate("io.leangen.geantyref", "com.stephanofer.lobbyHera.libs.geantyref")
        relocate("xyz.jpenilla.reflectionremapper", "com.stephanofer.lobbyHera.libs.reflectionremapper")
        relocate("net.fabricmc.mappingio", "com.stephanofer.lobbyHera.libs.mappingio")

        mergeServiceFiles()
        filesMatching("META-INF/services/**") {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
    }

    assemble {
        dependsOn(shadowJar)
    }
}
