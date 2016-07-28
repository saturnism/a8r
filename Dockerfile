FROM maven:3.3.3-jdk-7-onbuild

CMD ["java", "-Djava.net.preferIPv4Stack=true", "-Djava.net.preferIPv4Addresses", "-Djava.security.egd=file:/dev/./urandom", "-Dhttps.protocols=TLSv1,TLSv1.1,TLSv1.2", "-jar", "/usr/src/app/target/a8r-0.0.1-SNAPSHOT.jar", "--server.address=0.0.0.0", "--server.port=8080"]
