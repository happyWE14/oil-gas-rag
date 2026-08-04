# Build the JAR first with `mvn clean package`, then build this runtime image.
FROM eclipse-temurin:17-jre

LABEL description="Evidence-grounded literature analysis backend"

WORKDIR /app

# 创建非root用户
RUN groupadd -r appuser && useradd -r -g appuser appuser

COPY target/paper_collector-*.jar app.jar

RUN mkdir -p /app/logs /app/data && \
    chown -R appuser:appuser /app

USER appuser

EXPOSE 8080

# JVM 配置
ENV JAVA_OPTS="-Xmx512m -Xms256m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Duser.home=/app -Djava.security.egd=file:/dev./urandom -jar app.jar"]
