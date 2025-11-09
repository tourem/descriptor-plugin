package com.larbotech.maven.descriptor.spi.impl;

import com.larbotech.maven.descriptor.model.DeployableModule;
import com.larbotech.maven.descriptor.model.EnvironmentConfig;
import com.larbotech.maven.descriptor.service.*;
import com.larbotech.maven.descriptor.spi.FrameworkDetector;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.model.Model;

import java.nio.file.Path;
import java.util.List;

/**
 * Framework detector for Spring Boot applications.
 */
@Slf4j
public class SpringBootFrameworkDetector implements FrameworkDetector {
    
    private final SpringBootDetector springBootDetector;
    private final SpringBootProfileDetector profileDetector;
    private final DeploymentMetadataDetector metadataDetector;
    private final EnvironmentConfigDetector environmentConfigDetector;
    
    public SpringBootFrameworkDetector() {
        this.springBootDetector = new SpringBootDetector();
        this.profileDetector = new SpringBootProfileDetector();
        this.metadataDetector = new DeploymentMetadataDetector();
        this.environmentConfigDetector = new EnvironmentConfigDetector();
    }
    
    @Override
    public String getFrameworkName() {
        return "Spring Boot";
    }
    
    @Override
    public boolean isApplicable(Model model, Path modulePath) {
        return springBootDetector.isSpringBootExecutable(model);
    }

    @Override
    public void enrichModule(DeployableModule.DeployableModuleBuilder builder,
                            Model model,
                            Path modulePath,
                            Path projectRoot) {
        log.debug("Enriching module with Spring Boot metadata: {}", model.getArtifactId());

        // Mark as Spring Boot executable
        builder.springBootExecutable(true);

        // Detect main class
        String mainClass = metadataDetector.detectMainClass(model);
        if (mainClass != null) {
            builder.mainClass(mainClass);
        }

        // Detect profiles
        List<String> profiles = profileDetector.detectProfiles(modulePath, model, projectRoot);

        // Detect environment configurations
        if (profiles != null && !profiles.isEmpty()) {
            Boolean actuatorEnabled = metadataDetector.detectActuatorEnabled(model);
            List<EnvironmentConfig> environments = environmentConfigDetector.detectEnvironmentConfigs(
                modulePath, profiles, actuatorEnabled);
            if (environments != null && !environments.isEmpty()) {
                builder.environments(environments);
            }
        }
    }
    
    @Override
    public int getPriority() {
        return 100; // High priority for Spring Boot
    }
}

