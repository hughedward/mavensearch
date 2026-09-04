package com.mavensearch.eclipse.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.mavensearch.eclipse.model.Artifact;
import com.mavensearch.eclipse.ui.CopyActions;

class CopyActionsTest {

    private final Artifact a = new Artifact("org.springframework.boot", "spring-boot-starter-web", 1);

    @Test
    void mavenSnippet() {
        assertEquals("""
                <dependency>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-web</artifactId>
                    <version>3.5.4</version>
                </dependency>""", CopyActions.maven(a, "3.5.4"));
    }

    @Test
    void gradleSnippets() {
        assertEquals("implementation 'org.springframework.boot:spring-boot-starter-web:3.5.4'",
                CopyActions.gradle(a, "3.5.4"));
        assertEquals("implementation(\"org.springframework.boot:spring-boot-starter-web:3.5.4\")",
                CopyActions.gradleKts(a, "3.5.4"));
    }
}
