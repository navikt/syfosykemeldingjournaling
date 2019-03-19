import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer

group = "no.nav.syfo"
version = "1.0.0"

val confluentVersion = "5.0.0"
val coroutinesVersion = "1.1.1"
val jacksonVersion = "2.9.8"
val kafkaVersion = "2.1.1"
val kafkaEmbeddedVersion = "2.0.2"
val kluentVersion = "1.47"
val ktorVersion = "1.1.3"
val logstashLogbackEncoder = "5.3"
val logbackVersion = "1.2.3"
val prometheusVersion = "0.6.0"
val spekVersion = "2.0.0"
val syfosmoppgaveSchemasVersion = "1.2-SNAPSHOT"
val junitPlatformLauncher = "1.0.0"

tasks.withType<Jar> {
    manifest.attributes["Main-Class"] = "no.nav.syfo.BootstrapKt"
}


plugins {
    java
    kotlin("jvm") version "1.3.21"
    id("org.jmailen.kotlinter") version "1.21.0"
    id("com.diffplug.gradle.spotless") version "3.18.0"
    id("com.github.johnrengelman.shadow") version "4.0.4"
}



repositories {
    maven (url = "https://repo.adeo.no/repository/maven-snapshots/")
    maven (url = "https://repo.adeo.no/repository/maven-releases/")
    maven (url = "https://dl.bintray.com/kotlin/ktor")
    maven (url = "https://dl.bintray.com/spekframework/spek-dev")
    maven (url = "http://packages.confluent.io/maven/")
    maven (url = "https://kotlin.bintray.com/kotlinx")
    mavenCentral()
    jcenter()
}


dependencies {
    implementation(kotlin("stdlib"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")

    implementation("io.ktor:ktor-server-netty:$ktorVersion")

    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-json-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-auth-basic:$ktorVersion")
    implementation("io.ktor:ktor-client-jackson:$ktorVersion")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashLogbackEncoder")

    implementation("org.apache.kafka:kafka_2.12:$kafkaVersion")
    implementation("io.confluent:kafka-avro-serializer:$confluentVersion")

    implementation("no.nav.syfo:syfooppgave-schemas:$syfosmoppgaveSchemasVersion")

    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:$spekVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("no.nav:kafka-embedded-env:$kafkaEmbeddedVersion")

    testImplementation("org.junit.platform:junit-platform-launcher:$junitPlatformLauncher")
    testRuntimeOnly("org.spekframework.spek2:spek-runtime-jvm:$spekVersion")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:$spekVersion")

}


tasks {
    create("printVersion") {
        println(project.version)
    }

    withType<ShadowJar> {
        transform(ServiceFileTransformer::class.java) {
            setPath("META-INF/cxf")
            include("bus-extensions.txt")
        }
    }

    withType<Test> {
        useJUnitPlatform {
            includeEngines("spek2")
        }
        testLogging.showStandardStreams = true
    }
}
