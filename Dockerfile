FROM eclipse-temurin:25-jre
WORKDIR /app

# root가 아닌 사용자로 실행
RUN groupadd --system app && useradd --system --gid app app
COPY build/libs/*.jar app.jar
USER app

EXPOSE 8080
# 컨테이너 메모리의 75%까지만 힙으로 사용
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
