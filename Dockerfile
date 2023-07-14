from openjdk:11
VOLUME /tmp
ENV TZ=Asia/Shanghai
ARG JAR_FILE=target/gdal-retile-geoserver-1.0-SNAPSHOT.jar
COPY ${JAR_FILE} /jars/gdal-retile-geoserver.jar
RUN mkdir /root/gdal_test && ln -snf /usr/share/zoneinfo/$TZ  /etc/localtime && echo $TZ > /etc/timezon
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/jars/gdal-retile-geoserver.jar"]