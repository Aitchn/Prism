package com.aitchn.prism.plugin;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Whole-project layering rules. {@code prism-plugin} sees every module on its classpath, so this is the one place
 * that can check them all. See docs/adr/0001-module-layout.md and docs/adr/0002-platform-abstraction.md.
 */
@AnalyzeClasses(packages = "com.aitchn.prism", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String THREADED_REGIONS = "io.papermc.paper.threadedregions..";
    private static final String FOLIA_API = "dev.folia..";
    private static final String PACKET_EVENTS = "com.github.retrooper..";
    private static final String BETTER_MODEL = "io.github.toxicity188..";

    /** Core must never import Folia, Paper-implementation or Folia-implementation types. */
    @ArchTest
    static final ArchRule coreIsPlatformNeutral = noClasses()
            .that().resideInAPackage("com.aitchn.prism.core..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    THREADED_REGIONS, FOLIA_API, "com.aitchn.prism.platform.paper..", "com.aitchn.prism.platform.folia..");

    /** Only the Folia module may reference regionized-threading types. */
    @ArchTest
    static final ArchRule onlyFoliaModuleUsesThreadedRegions = noClasses()
            .that().resideOutsideOfPackage("com.aitchn.prism.platform.folia..")
            .should().dependOnClassesThat().resideInAnyPackage(THREADED_REGIONS, FOLIA_API);

    /** The SPI must not know its implementations. */
    @ArchTest
    static final ArchRule spiDoesNotKnowImplementations = noClasses()
            .that().resideInAPackage("com.aitchn.prism.platform.spi..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.aitchn.prism.platform.paper..", "com.aitchn.prism.platform.folia..", "com.aitchn.prism.core..");

    /** Mods compile against the API; it must stay free of implementation and third-party engine types. */
    @ArchTest
    static final ArchRule apiIsSelfContained = noClasses()
            .that().resideInAPackage("com.aitchn.prism.api..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    THREADED_REGIONS, FOLIA_API, PACKET_EVENTS, BETTER_MODEL,
                    "com.aitchn.prism.core..", "com.aitchn.prism.platform..", "com.aitchn.prism.client..",
                    "com.aitchn.prism.model..");

    /** Engine types stay inside their bridge module. */
    @ArchTest
    static final ArchRule packetEventsOnlyInClient = noClasses()
            .that().resideOutsideOfPackage("com.aitchn.prism.client..")
            .should().dependOnClassesThat().resideInAPackage(PACKET_EVENTS);

    @ArchTest
    static final ArchRule betterModelOnlyInModel = noClasses()
            .that().resideOutsideOfPackage("com.aitchn.prism.model..")
            .should().dependOnClassesThat().resideInAPackage(BETTER_MODEL);
}
