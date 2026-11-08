# Runs one server jar plus the plugins staged in ./plugins. Built by run.sh, never by hand.
# The flat world has 56 layers of stone (y -63 to -8) so the ore self test has something to replace.
FROM eclipse-temurin:25-jre
WORKDIR /server
COPY server.jar /server/server.jar
COPY plugins/ /server/plugins/
RUN echo "eula=true" > eula.txt \
    && printf 'online-mode=false\nlevel-type=flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:stone","height":56},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\ngenerate-structures=false\nview-distance=4\nsimulation-distance=4\nspawn-protection=0\nmax-players=1\n' > server.properties
EXPOSE 25565
ENTRYPOINT ["java", "-Xms512M", "-Xmx1G", "-Dprism.selftest=examplemod:ruby_block", "-Dprism.selftest.platform=examplemod:ruby_counter", "-Dprism.selftest.model=examplemod:turret", "-Dprism.selftest.ore=examplemod:ruby_ore", "-Dprism.selftest.connections=examplemod:ruby_cable,examplemod:ruby_counter", "-Dprism.selftest.passable=examplemod:ruby_flower", "-Dprism.selftest.structure=examplemod:ruby_core,examplemod:ruby_block", "-Dprism.selftest.cuboid=examplemod:ruby_tank,examplemod:ruby_block", "-Dae2.selftest=true", "-Dmekanism.selftest=true", "-jar", "server.jar", "--nogui"]
