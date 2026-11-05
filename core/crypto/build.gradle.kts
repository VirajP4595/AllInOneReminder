plugins {
    alias(libs.plugins.kotlin.jvm)
    jacoco
}

group = "app.allinonereminder"
version = "0.1.0"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.tink)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    finalizedBy(tasks.named("jacocoTestReport"))
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

tasks.register("coverageSummary") {
    dependsOn(tasks.named("jacocoTestReport"))
    doLast {
        val report = layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml").get().asFile
        val line = Regex("""<counter type="LINE" missed="(\d+)" covered="(\d+)"/>""")
            .findAll(report.readText())
            .last()
        val missed = line.groupValues[1].toLong()
        val covered = line.groupValues[2].toLong()
        println("LINE_COVERAGE=%.2f%%".format(100.0 * covered / (covered + missed)))
    }
}
