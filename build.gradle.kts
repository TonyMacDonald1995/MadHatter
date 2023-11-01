import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.21"
    application
}

group = "com.tonymacdonald1995"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("net.dv8tion:JDA:5.0.0-beta.2")
    implementation("com.google.code.gson:gson:2.10.1")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.jar {
    archiveFileName.set("MadHatter.jar")
    manifest {
        attributes["Main-Class"] = "com.tonymacdonald1995.madhatter.MadHatterKt"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    val contents = configurations.runtimeClasspath.get().map {
        if (it.isDirectory)
            it
        else
            zipTree(it)
    } + sourceSets.main.get().output

    from(contents)
}

application {
    mainClass.set("com.tonymacdonald1995.madhatter.MadHatter")
}