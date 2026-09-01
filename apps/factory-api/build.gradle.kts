plugins {
    java
    id("com.diffplug.spotless") version "8.10.1"
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.forgesync"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.networknt:json-schema-validator:2.0.4") {
        exclude(group = "com.fasterxml.jackson.dataformat", module = "jackson-dataformat-yaml")
    }
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web")

    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.processResources {
    from("../../contracts/observation-envelope/v2/observation-envelope.schema.json") {
        into("contracts/observation-envelope/v2")
    }
}

tasks.processTestResources {
    from("../../tests/fixtures/canonical/v2") {
        into("fixtures/canonical/v2")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

spotless {
    java {
        googleJavaFormat()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
