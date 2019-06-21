import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.syfo"
version = "1.0.7"

val confluentVersion = "5.0.0"
val coroutinesVersion = "1.1.1"
val jacksonVersion = "2.9.8"
val kafkaVersion = "2.1.1"
val kafkaEmbeddedVersion = "2.0.2"
val kluentVersion = "1.47"
val ktorVersion = "1.2.0"
val logstashLogbackEncoder = "5.3"
val logbackVersion = "1.2.3"
val prometheusVersion = "0.6.0"
val smCommonVersion = "1.0.20"
val spekVersion = "2.0.2"
val syfosmoppgaveSchemasVersion = "1.2-SNAPSHOT"
val junitPlatformLauncher = "1.0.0"
val navPersonv3Version = "3.2.0"
val javaxActivationVersion = "1.1.1"
val cxfVersion = "3.2.7"
val commonsTextVersion = "1.4"
val jaxbBasicAntVersion = "1.11.1"
val javaxAnnotationApiVersion = "1.3.2"
val jaxwsToolsVersion = "2.3.1"
val jaxbRuntimeVersion = "2.4.0-b180830.0438"
val javaxJaxwsApiVersion = "2.2.1"
val jaxbApiVersion = "2.4.0-b180830.0359"


plugins {
    java
    kotlin("jvm") version "1.3.31"
    id("org.jmailen.kotlinter") version "1.26.0"
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

    implementation("no.nav.syfo.sm:syfosm-common-models:$smCommonVersion")
    implementation("no.nav.syfo.sm:syfosm-common-networking:$smCommonVersion")
    implementation("no.nav.syfo.sm:syfosm-common-rest-sts:$smCommonVersion")
    implementation("no.nav.syfo.sm:syfosm-common-kafka:$smCommonVersion")
    implementation("no.nav.syfo.sm:syfosm-common-ws:$smCommonVersion")
    implementation("no.nav.syfo.sm:syfosm-common-diagnosis-codes:$smCommonVersion")

    implementation("no.nav.syfo:syfooppgave-schemas:$syfosmoppgaveSchemasVersion")
    implementation("no.nav.tjenester:nav-person-v3-tjenestespesifikasjon:$navPersonv3Version")

    implementation("org.apache.commons:commons-text:$commonsTextVersion")
    implementation("org.apache.cxf:cxf-rt-frontend-jaxws:$cxfVersion")
    implementation("org.apache.cxf:cxf-rt-features-logging:$cxfVersion")
    implementation("org.apache.cxf:cxf-rt-transports-http:$cxfVersion")
    implementation("org.apache.cxf:cxf-rt-ws-security:$cxfVersion")

    implementation("javax.xml.ws:jaxws-api:$javaxJaxwsApiVersion")
    implementation("javax.annotation:javax.annotation-api:$javaxAnnotationApiVersion")
    implementation("javax.xml.bind:jaxb-api:$jaxbApiVersion")
    implementation("org.glassfish.jaxb:jaxb-runtime:$jaxbRuntimeVersion")
    implementation("javax.activation:activation:$javaxActivationVersion")
    implementation("com.sun.xml.ws:jaxws-tools:$jaxwsToolsVersion") {
        exclude(group = "com.sun.xml.ws", module = "policy")
    }


    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:$spekVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("no.nav:kafka-embedded-env:$kafkaEmbeddedVersion")

    testImplementation("org.junit.platform:junit-platform-launcher:$junitPlatformLauncher")
    testRuntimeOnly("org.spekframework.spek2:spek-runtime-jvm:$spekVersion")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:$spekVersion")

}


tasks {

    withType<Jar> {
        manifest.attributes["Main-Class"] = "no.nav.syfo.BootstrapKt"
    }
    
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
    
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }
}
