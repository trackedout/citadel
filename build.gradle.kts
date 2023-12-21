plugins {
    id "com.github.johnrengelman.shadow" version "7.1.2"
    id "java"
}

group = "org.trackedout"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        name = "papermc-repo"
        url = "https://repo.papermc.io/repository/maven-public/"
    }
    maven {
        name = "sonatype"
        url = "https://oss.sonatype.org/content/groups/public/"
    }
    maven { 
        url = "https://repo.aikar.co/content/groups/aikar/" 
    }

}

dependencies {
    compileOnly "io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT"
    implementation "commons-io:commons-io:2.13.0"
    implementation "co.aikar:acf-paper:0.5.3-SNAPSHOT"
}

shadowJar {
    relocate "co.aikar.commands", "org.trackedout.citadel.acf"
    relocate "co.aikar.locales", "org.trackedout.citadel.locales"
}

compileKotlin {
    kotlinOptions.javaParameters = true
}

def targetJavaVersion = 17
java {
    def javaVersion = JavaVersion.toVersion(targetJavaVersion)
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
    if (JavaVersion.current() < javaVersion) {
        toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    }
}

tasks.withType(JavaCompile).configureEach {
    if (targetJavaVersion >= 10 || JavaVersion.current().isJava10Compatible()) {
        options.release.set(targetJavaVersion)
    }
}

processResources {
    def props = [version: version]
    inputs.properties props
    filteringCharset "UTF-8"
    filesMatching("plugin.yml") {
        expand props
    }
}

implementation group: "org.apache.commons", name: "commons-lang3", version: "3.0"
