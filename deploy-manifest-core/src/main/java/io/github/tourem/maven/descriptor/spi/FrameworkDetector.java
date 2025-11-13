package io.github.tourem.maven.descriptor.spi;

import io.github.tourem.maven.descriptor.model.DeployableModule;
import org.apache.maven.model.Model;

import java.nio.file.Path;

/**
 * Service Provider Interface for detecting framework-specific features in Maven modules.
 *
 * Implementations of this interface can be registered via Java ServiceLoader to extend
 * the descriptor plugin with support for additional frameworks like Quarkus, Micronaut,
 * Jakarta EE, etc.
 *
 * To register a custom detector:
 * 1. Implement this interface
 * 2. Create META-INF/services/com.larbotech.maven.descriptor.spi.FrameworkDetector
 * 3. Add the fully qualified class name of your implementation
 *
 * Example:
 * <pre>
 * public class QuarkusDetector implements FrameworkDetector {
 *     public String getFrameworkName() {
 *         return "Quarkus";
 *     }
 *
 *     public boolean isApplicable(Model model, Path modulePath) {
 *         return model.getDependencies().stream()
 *             .anyMatch(d -> "io.quarkus".equals(d.getGroupId()));
 *     }
 *
 *     public void enrichModule(DeployableModule.DeployableModuleBuilder builder,
 *                              Model model, Path modulePath, Path projectRoot) {
 *         // Add Quarkus-specific metadata
 *     }
 * }
 * </pre>
 * @author tourem

 */
public interface FrameworkDetector {

    /**
     * Get the name of the framework this detector supports.
     *
     * @return Framework name (e.g., "Quarkus", "Micronaut", "Jakarta EE")
     */
    String getFrameworkName();

    /**
     * Check if this detector is applicable to the given module.
     *
     * @param model Maven model of the module
     * @param modulePath Path to the module directory
     * @return true if this detector should process this module
     */
    boolean isApplicable(Model model, Path modulePath);

    /**
     * Enrich the deployable module with framework-specific metadata.
     *
     * This method is called when isApplicable() returns true.
     * Implementations should add framework-specific information to the builder.
     *
     * @param builder Builder for the deployable module
     * @param model Maven model of the module
     * @param modulePath Path to the module directory
     * @param projectRoot Path to the project root directory
     */
    void enrichModule(DeployableModule.DeployableModuleBuilder builder,
                     Model model,
                     Path modulePath,
                     Path projectRoot);

    /**
     * Get the priority of this detector.
     * Higher priority detectors are executed first.
     * Default priority is 0.
     *
     * @return Priority value (higher = executed first)
     */
    default int getPriority() {
        return 0;
    }
}

