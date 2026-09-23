package br.com.gems.keycloak.admin;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "br.com.gems.keycloak.admin")
class KeycloakAdminArchitectureTest {

    @ArchTest
    static final ArchRule contratosPublicosNaoVazamFrameworkOuProvedor = classes()
            .that().areInterfaces()
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage( "java..", "br.com.gems.keycloak.admin.." );

}
