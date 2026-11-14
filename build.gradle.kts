import com.github.gradle.node.npm.task.NpmTask

plugins {
    java
    pmd
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.asciidoctor.jvm.convert") version "4.0.5"
    id("com.diffplug.spotless") version "8.9.0"
    id("com.github.node-gradle.node") version "7.1.0"
}

group = "com.b.now._29"
version = "0.6.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

node {
    download = true                  // CI/서버에 Node가 없어도 됨
    version = "24.22.0"              // .nvmrc와 맞추기
    npmInstallCommand = "ci"
    nodeProjectDir = file("src/main/frontend")
}

repositories {
    mavenCentral()
}

val snippetsDir = file("build/generated-snippets")
val asciidoctorExt = configurations.create("asciidoctorExt")

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation("org.flywaydb:flyway-database-postgresql")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-restdocs")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    asciidoctorExt("org.springframework.restdocs:spring-restdocs-asciidoctor")
}

// 개발용 JWT 비밀값과 테스트 계정은 dev 프로필에만 있다 (application-dev.yaml, TestAccountInitializer)
tasks.bootRun {
    systemProperty("spring.profiles.active", "dev")
}

// ── 테스트 ───────────────────────────────────────────────────────────────────
// test            : 빠른 테스트 (@WebMvcTest 컨트롤러 테스트 → REST Docs snippet 생성)
// integrationTest : @Tag("integration") 붙은 Testcontainers 테스트 (OrbStack/Docker 필요)

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.test {
    useJUnitPlatform { excludeTags("integration") }
    outputs.dir(snippetsDir)
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Testcontainers 통합 테스트 (OrbStack/Docker 필요)"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("integration") }
    shouldRunAfter(tasks.test)
}

// ── REST Docs ────────────────────────────────────────────────────────────────
// 문서 원본: src/docs/asciidoc/index.adoc → 결과: build/docs/asciidoc/index.html
// 배포 jar 에서는 /docs/index.html 로 열린다 (bootJar 블록 참고)

// asciidoctor 플러그인 기본값(AsciidoctorJ 2.5.x)은 REST Docs 4(Spring Boot 4)와 호환되지 않음 → 3.x로 고정
asciidoctorj {
    setVersion("3.0.0")
}

tasks.asciidoctor {
    configurations(asciidoctorExt.name)
    // 스니펫이 없어도 경고만 내고 성공하므로, 경고를 빌드 실패로 처리한다
    failureLevel = org.asciidoctor.gradle.base.log.Severity.WARN
    inputs.dir(snippetsDir)
    dependsOn(tasks.test)
    attributes(mapOf("snippets" to snippetsDir))
}

// ── 포맷터 / 린터 ─────────────────────────────────────────────────────────────

spotless {
    java {
        // 나중에 src/main/frontend 가 생기면 node_modules 까지 훑지 않도록 Java 폴더만 지정
        target("src/main/java/**/*.java", "src/test/java/**/*.java")
        palantirJavaFormat()
        removeUnusedImports()
    }
}

if (System.getenv("CI") == null) {
    tasks.named("spotlessJavaCheck") { dependsOn("spotlessJavaApply") }
}

pmd {
    toolVersion = "7.28.0"
    ruleSets = listOf("rulesets/java/quickstart.xml")
    isConsoleOutput = true
}

// ── 배포 jar ─────────────────────────────────────────────────────────────────
// static/docs/ ← REST Docs HTML (/docs/index.html)
val frontendBuild = tasks.register<NpmTask>("frontendBuild") {
    dependsOn(tasks.npmInstall)
    npmCommand = listOf("run", "build")
    inputs.dir("src/main/frontend/src")
    inputs.files(
        "src/main/frontend/index.html",
        "src/main/frontend/package-lock.json",
        "src/main/frontend/vite.config.ts",
    )
    outputs.dir("src/main/frontend/dist")
}

val frontendCheck = tasks.register<NpmTask>("frontendCheck") {
    dependsOn(tasks.npmInstall)
    npmCommand = listOf("run", "check")
}

tasks.check { dependsOn(frontendCheck) }

tasks.bootJar {
    from(frontendBuild) { into("BOOT-INF/classes/static") }
    // -PwithDocs 를 줄 때만 REST Docs를 jar에 포함
    if (project.hasProperty("withDocs")) {
        from(tasks.asciidoctor) { into("BOOT-INF/classes/static/docs") }
    }
}

tasks.jar { enabled = false }
