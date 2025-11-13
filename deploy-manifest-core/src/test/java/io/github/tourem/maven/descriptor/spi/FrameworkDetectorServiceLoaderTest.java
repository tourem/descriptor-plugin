package io.github.tourem.maven.descriptor.spi;

import io.github.tourem.maven.descriptor.spi.impl.QuarkusFrameworkDetector;
import io.github.tourem.maven.descriptor.spi.impl.SpringBootFrameworkDetector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for FrameworkDetector ServiceLoader mechanism.
 */
class FrameworkDetectorServiceLoaderTest {

    @Test
    void shouldLoadFrameworkDetectorsViaServiceLoader() {
        // When: Loading framework detectors via ServiceLoader
        ServiceLoader<FrameworkDetector> serviceLoader = ServiceLoader.load(FrameworkDetector.class);
        List<FrameworkDetector> detectors = new ArrayList<>();
        serviceLoader.forEach(detectors::add);
        
        // Then: Should load at least 2 detectors (Spring Boot and Quarkus)
        assertThat(detectors).hasSizeGreaterThanOrEqualTo(2);
        
        // Verify Spring Boot detector is loaded
        assertThat(detectors)
            .anyMatch(d -> d instanceof SpringBootFrameworkDetector)
            .anyMatch(d -> d.getFrameworkName().equals("Spring Boot"));
        
        // Verify Quarkus detector is loaded
        assertThat(detectors)
            .anyMatch(d -> d instanceof QuarkusFrameworkDetector)
            .anyMatch(d -> d.getFrameworkName().equals("Quarkus"));
    }

    @Test
    void shouldLoadDetectorsInPriorityOrder() {
        // When: Loading framework detectors
        ServiceLoader<FrameworkDetector> serviceLoader = ServiceLoader.load(FrameworkDetector.class);
        List<FrameworkDetector> detectors = new ArrayList<>();
        serviceLoader.forEach(detectors::add);
        
        // Sort by priority (descending)
        detectors.sort((d1, d2) -> Integer.compare(d2.getPriority(), d1.getPriority()));
        
        // Then: Spring Boot (priority 100) should come before Quarkus (priority 90)
        assertThat(detectors).isNotEmpty();
        
        FrameworkDetector firstDetector = detectors.get(0);
        assertThat(firstDetector.getFrameworkName()).isEqualTo("Spring Boot");
        assertThat(firstDetector.getPriority()).isEqualTo(100);
        
        if (detectors.size() > 1) {
            FrameworkDetector secondDetector = detectors.get(1);
            assertThat(secondDetector.getFrameworkName()).isEqualTo("Quarkus");
            assertThat(secondDetector.getPriority()).isEqualTo(90);
        }
    }

    @Test
    void shouldHaveUniqueFrameworkNames() {
        // When: Loading framework detectors
        ServiceLoader<FrameworkDetector> serviceLoader = ServiceLoader.load(FrameworkDetector.class);
        List<String> frameworkNames = new ArrayList<>();
        serviceLoader.forEach(detector -> frameworkNames.add(detector.getFrameworkName()));
        
        // Then: All framework names should be unique
        assertThat(frameworkNames).doesNotHaveDuplicates();
    }

    @Test
    void shouldHaveValidPriorities() {
        // When: Loading framework detectors
        ServiceLoader<FrameworkDetector> serviceLoader = ServiceLoader.load(FrameworkDetector.class);
        List<FrameworkDetector> detectors = new ArrayList<>();
        serviceLoader.forEach(detectors::add);
        
        // Then: All priorities should be positive
        assertThat(detectors)
            .allMatch(d -> d.getPriority() > 0, "All priorities should be positive");
    }

    @Test
    void shouldHaveNonNullFrameworkNames() {
        // When: Loading framework detectors
        ServiceLoader<FrameworkDetector> serviceLoader = ServiceLoader.load(FrameworkDetector.class);
        List<FrameworkDetector> detectors = new ArrayList<>();
        serviceLoader.forEach(detectors::add);
        
        // Then: All framework names should be non-null and non-empty
        assertThat(detectors)
            .allMatch(d -> d.getFrameworkName() != null && !d.getFrameworkName().isEmpty(),
                     "All framework names should be non-null and non-empty");
    }
}

