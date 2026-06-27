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
    compileOnly("io.papermc.paper:paper-api:26.2.build.34-alpha")
    compileOnly("com.stephanofer:networkplayersettings:2.0.0")

    implementation("com.stephanofer.boostedyaml:boosted-yaml:1.3.7")
    implementation("org.incendo:cloud-paper:2.0.0-beta.16")
    implementation("org.incendo:cloud-minecraft-extras:2.0.0-beta.16")

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

    jar {
        enabled = false
    }

    shadowJar {
        destinationDirectory.set(layout.projectDirectory.dir("target"))
        archiveClassifier.set("")

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

    clean {
        delete(layout.projectDirectory.dir("target"))
    }

    assemble {
        dependsOn(shadowJar)
    }
}
