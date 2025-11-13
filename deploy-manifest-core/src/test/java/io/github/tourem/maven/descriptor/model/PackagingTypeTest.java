package io.github.tourem.maven.descriptor.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PackagingType enum.
 */
class PackagingTypeTest {
    
    @Test
    void shouldParseJarPackaging() {
        PackagingType type = PackagingType.fromString("jar");
        assertThat(type).isEqualTo(PackagingType.JAR);
    }
    
    @Test
    void shouldParseWarPackaging() {
        PackagingType type = PackagingType.fromString("war");
        assertThat(type).isEqualTo(PackagingType.WAR);
    }
    
    @Test
    void shouldParseEarPackaging() {
        PackagingType type = PackagingType.fromString("ear");
        assertThat(type).isEqualTo(PackagingType.EAR);
    }
    
    @Test
    void shouldParsePomPackaging() {
        PackagingType type = PackagingType.fromString("pom");
        assertThat(type).isEqualTo(PackagingType.POM);
    }
    
    @Test
    void shouldDefaultToJarWhenNull() {
        PackagingType type = PackagingType.fromString(null);
        assertThat(type).isEqualTo(PackagingType.JAR);
    }
    
    @Test
    void shouldDefaultToJarWhenEmpty() {
        PackagingType type = PackagingType.fromString("");
        assertThat(type).isEqualTo(PackagingType.JAR);
    }
    
    @Test
    void shouldDefaultToJarWhenUnknown() {
        PackagingType type = PackagingType.fromString("unknown");
        assertThat(type).isEqualTo(PackagingType.JAR);
    }
    
    @Test
    void shouldBeCaseInsensitive() {
        PackagingType type1 = PackagingType.fromString("WAR");
        PackagingType type2 = PackagingType.fromString("War");
        PackagingType type3 = PackagingType.fromString("war");
        
        assertThat(type1).isEqualTo(PackagingType.WAR);
        assertThat(type2).isEqualTo(PackagingType.WAR);
        assertThat(type3).isEqualTo(PackagingType.WAR);
    }
    
    @Test
    void jarShouldBeDeployable() {
        assertThat(PackagingType.JAR.isDeployable()).isTrue();
    }
    
    @Test
    void warShouldBeDeployable() {
        assertThat(PackagingType.WAR.isDeployable()).isTrue();
    }
    
    @Test
    void earShouldBeDeployable() {
        assertThat(PackagingType.EAR.isDeployable()).isTrue();
    }
    
    @Test
    void pomShouldNotBeDeployable() {
        assertThat(PackagingType.POM.isDeployable()).isFalse();
    }
    
    @Test
    void mavenPluginShouldNotBeDeployable() {
        assertThat(PackagingType.MAVEN_PLUGIN.isDeployable()).isFalse();
    }
}

