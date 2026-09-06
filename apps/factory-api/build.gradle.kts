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
    implementation("org.eclipse.paho:org.eclipse.paho.mqttv5.client:1.2.5")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.processResources {
    from("../../config/spatial/machine-layout-v1.json") {
        into("config/spatial")
    }
    from("../../contracts/observation-envelope/v2/observation-envelope.schema.json") {
        into("contracts/observation-envelope/v2")
    }
    from("../../db/migration") {
        into("db/migration")
    }
    from("../../contracts/twin/v1/twin-snapshot.schema.json") {
        into("contracts/twin/v1")
    }
    from("../../contracts/websocket/v1/twin-patch.schema.json") {
        into("contracts/websocket/v1")
    }
    from("../../contracts/replay/v1/replay-cursor.schema.json") {
        into("contracts/replay/v1")
    }
    from("../../contracts/replay/v1/replay-session.schema.json") {
        into("contracts/replay/v1")
    }
    from("../../contracts/process-analytics/v1/machining-runs.schema.json") {
        into("contracts/process-analytics/v1")
    }
    from("../../contracts/process-analytics/v1/cycle-features.schema.json") {
        into("contracts/process-analytics/v1")
    }
    from("../../contracts/process-analytics/v1/anomaly-assessments.schema.json") {
        into("contracts/process-analytics/v1")
    }
    from("../../contracts/tool-changes/v1/tool-change-timeline.schema.json") {
        into("contracts/tool-changes/v1")
    }
    from("../../contracts/toolpath/v1/observed-toolpath.schema.json") {
        into("contracts/toolpath/v1")
    }
}

tasks.processTestResources {
    from("../../tests/fixtures/canonical/v2") {
        into("fixtures/canonical/v2")
    }
    from("../../tests/fixtures/twin/v1") {
        into("fixtures/twin/v1")
    }
    from("../../tests/fixtures/replay/v1") {
        into("fixtures/replay/v1")
    }
    from("../../tests/fixtures/process-analytics/v1") {
        into("fixtures/process-analytics/v1")
    }
    from("../../tests/fixtures/tool-changes/v1") {
        into("fixtures/tool-changes/v1")
    }
    from("../../tests/fixtures/toolpath/v1") {
        into("fixtures/toolpath/v1")
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
