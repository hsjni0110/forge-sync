package com.forgesync.factoryapi;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

  private static final ArchRule DOMAIN_DEPENDENCY_RULE =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework..", "..application..", "..adapter..")
          .allowEmptyShould(true);

  private static final ArchRule APPLICATION_DEPENDENCY_RULE =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework..", "..adapter..")
          .allowEmptyShould(true);

  private static final ArchRule REST_ADAPTER_PERSISTENCE_RULE =
      noClasses()
          .that()
          .resideInAPackage("..adapter.inbound.rest..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..adapter.outbound..", "org.springframework.jdbc..", "jakarta.persistence..")
          .allowEmptyShould(true);

  @Test
  void productionCodeDependsOnlyTowardTheDomain() {
    JavaClasses productionClasses =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.forgesync.factoryapi");

    DOMAIN_DEPENDENCY_RULE.check(productionClasses);
    APPLICATION_DEPENDENCY_RULE.check(productionClasses);
    REST_ADAPTER_PERSISTENCE_RULE.check(productionClasses);
  }

  @Test
  void domainRuleRejectsAnIntentionalAdapterDependency() {
    JavaClasses invalidFixture =
        new ClassFileImporter()
            .importClasses(
                architecturefixture.domain.InvalidDomainType.class,
                architecturefixture.adapter.FrameworkAdapter.class);

    assertThatThrownBy(() -> DOMAIN_DEPENDENCY_RULE.check(invalidFixture))
        .isInstanceOf(AssertionError.class);
  }
}
