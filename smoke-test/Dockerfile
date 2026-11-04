# Runs one server jar plus the plugins staged in ./plugins. Built by run.sh, never by hand.
FROM eclipse-temurin:25-jre
WORKDIR /server
COPY server.jar /server/server.jar
COPY plugins/ /server/plugins/
RUN echo "eula=true" > eula.txt \
    && printf 'online-mode=false\nlevel-type=flat\ngenerate-structures=false\nview-distance=4\nsimulation-distance=4\nspawn-protection=0\nmax-players=1\n' > server.properties
EXPOSE 25565
ENTRYPOINT ["java", "-Xms512M", "-Xmx1G", "-Dprism.selftest=examplemod:ruby_block", "-Dprism.selftest.model=examplemod:turret", "-Dprism.selftest.native=examplemod:ruby_block", "-jar", "server.jar", "--nogui"]
