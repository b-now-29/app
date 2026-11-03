plugins {
	java
	pmd
	id("org.springframework.boot") version "4.1.2"
	id("io.spring.dependency-management") version "1.1.7"
	id("org.asciidoctor.jvm.convert") version "4.0.5"
	id("com.diffplug.spotless") version "8.9.0"
}

group = "com.b.now._29"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

val snippetsDir = file("build/generated-snippets")
val asciidoctorExt by configurations.creating

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
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

val integrationTest by tasks.registering(Test::class) {
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

pmd {
	toolVersion = "7.28.0"
	ruleSets = listOf("rulesets/java/quickstart.xml")
	isConsoleOutput = true
}

// ── 배포 jar ─────────────────────────────────────────────────────────────────
// static/docs/ ← REST Docs HTML (/docs/index.html)
// TODO: 프론트엔드(src/main/frontend) 설정은 나중에 이 아래에 추가

tasks.bootJar {
	from(tasks.asciidoctor) { into("BOOT-INF/classes/static/docs") }
}
