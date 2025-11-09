package com.larbotech.maven.descriptor.service;

import com.larbotech.maven.descriptor.model.AssemblyArtifact;
import com.larbotech.maven.descriptor.model.DeployableModule;
import com.larbotech.maven.descriptor.model.PackagingType;
import com.larbotech.maven.descriptor.model.ProjectDescriptor;
import com.larbotech.maven.descriptor.spi.FrameworkDetector;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service to analyze Maven projects and extract deployable modules.
 */
@Slf4j
public class MavenProjectAnalyzer {

    private final MavenRepositoryPathGenerator pathGenerator;
    private final SpringBootDetector springBootDetector;
    private final SpringBootProfileDetector profileDetector;
    private final MavenAssemblyDetector assemblyDetector;
    private final DeploymentMetadataDetector metadataDetector;
    private final EnvironmentConfigDetector environmentConfigDetector;
    private final ExecutablePluginDetector executablePluginDetector;
    private final GitInfoCollector gitInfoCollector;
    private final List<FrameworkDetector> frameworkDetectors;

    /**
     * Default constructor that initializes all dependencies.
     */
    public MavenProjectAnalyzer() {
        this.pathGenerator = new MavenRepositoryPathGenerator();
        this.springBootDetector = new SpringBootDetector();
        this.profileDetector = new SpringBootProfileDetector();
        this.assemblyDetector = new MavenAssemblyDetector(pathGenerator);
        this.environmentConfigDetector = new EnvironmentConfigDetector();
        this.metadataDetector = new DeploymentMetadataDetector();
        this.executablePluginDetector = new ExecutablePluginDetector();
        this.gitInfoCollector = new GitInfoCollector();
        this.frameworkDetectors = loadFrameworkDetectors();
    }

    /**
     * Load framework detectors via ServiceLoader and sort by priority.
     */
    private List<FrameworkDetector> loadFrameworkDetectors() {
        ServiceLoader<FrameworkDetector> loader = ServiceLoader.load(FrameworkDetector.class);
        List<FrameworkDetector> detectors = new ArrayList<>();
        loader.forEach(detectors::add);

        // Sort by priority (higher first)
        detectors.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

        log.info("Loaded {} framework detectors: {}",
                detectors.size(),
                detectors.stream()
                    .map(FrameworkDetector::getFrameworkName)
                    .collect(Collectors.joining(", ")));

        return detectors;
    }
    
    /**
     * Analyze a Maven project and generate a descriptor.
     *
     * @param projectRootPath Root directory of the Maven project
     * @return ProjectDescriptor containing all deployable modules
     */
    public ProjectDescriptor analyzeProject(Path projectRootPath) {
        log.info("Analyzing Maven project at: {}", projectRootPath);
        
        if (!Files.exists(projectRootPath) || !Files.isDirectory(projectRootPath)) {
            throw new IllegalArgumentException("Invalid project path: " + projectRootPath);
        }
        
        File rootPom = projectRootPath.resolve("pom.xml").toFile();
        if (!rootPom.exists()) {
            throw new IllegalArgumentException("No pom.xml found at: " + projectRootPath);
        }
        
        try {
            Model rootModel = parsePom(rootPom);
            List<DeployableModule> deployableModules = new ArrayList<>();
            int totalModules = 0;
            
            // Analyze root project
            totalModules++;
            DeployableModule rootModule = analyzeModule(rootModel, projectRootPath, projectRootPath);
            if (rootModule != null) {
                deployableModules.add(rootModule);
            }
            
            // Analyze sub-modules
            if (rootModel.getModules() != null && !rootModel.getModules().isEmpty()) {
                for (String moduleName : rootModel.getModules()) {
                    Path modulePath = projectRootPath.resolve(moduleName);
                    File modulePom = modulePath.resolve("pom.xml").toFile();
                    
                    if (modulePom.exists()) {
                        totalModules++;
                        Model moduleModel = parsePom(modulePom);
                        DeployableModule module = analyzeModule(moduleModel, modulePath, projectRootPath);
                        if (module != null) {
                            deployableModules.add(module);
                        }
                        
                        // Recursively analyze nested modules
                        totalModules += analyzeNestedModules(moduleModel, modulePath, projectRootPath, deployableModules);
                    }
                }
            }
            
            // Collect build info
            var buildInfo = gitInfoCollector.collectBuildInfo(projectRootPath);

            // Extract Maven repository URL from distributionManagement
            String mavenRepositoryUrl = extractMavenRepositoryUrl(rootModel);

            // Enrich modules with repository URLs
            if (mavenRepositoryUrl != null) {
                deployableModules.forEach(module -> {
                    if (module.getRepositoryPath() != null) {
                        module.setRepositoryUrl(mavenRepositoryUrl + "/" + module.getRepositoryPath());
                    }
                    if (module.getAssemblyArtifacts() != null) {
                        List<AssemblyArtifact> enrichedAssemblies = module.getAssemblyArtifacts().stream()
                            .map(assembly -> AssemblyArtifact.builder()
                                .assemblyId(assembly.assemblyId())
                                .format(assembly.format())
                                .repositoryPath(assembly.repositoryPath())
                                .repositoryUrl(mavenRepositoryUrl + "/" + assembly.repositoryPath())
                                .build())
                            .collect(Collectors.toList());
                        module.setAssemblyArtifacts(enrichedAssemblies);
                    }
                });
            }

            return ProjectDescriptor.builder()
                    .projectGroupId(resolveGroupId(rootModel))
                    .projectArtifactId(rootModel.getArtifactId())
                    .projectVersion(resolveVersion(rootModel))
                    .projectName(rootModel.getName())
                    .projectDescription(rootModel.getDescription())
                    .generatedAt(LocalDateTime.now())
                    .deployableModules(deployableModules)
                    .totalModules(totalModules)
                    .deployableModulesCount(deployableModules.size())
                    .buildInfo(buildInfo)
                    .mavenRepositoryUrl(mavenRepositoryUrl)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error analyzing project: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to analyze Maven project", e);
        }
    }
    
    /**
     * Recursively analyze nested modules.
     */
    private int analyzeNestedModules(Model parentModel, Path parentPath, Path projectRoot, 
                                     List<DeployableModule> deployableModules) throws Exception {
        int count = 0;
        
        if (parentModel.getModules() != null && !parentModel.getModules().isEmpty()) {
            for (String moduleName : parentModel.getModules()) {
                Path modulePath = parentPath.resolve(moduleName);
                File modulePom = modulePath.resolve("pom.xml").toFile();
                
                if (modulePom.exists()) {
                    count++;
                    Model moduleModel = parsePom(modulePom);
                    DeployableModule module = analyzeModule(moduleModel, modulePath, projectRoot);
                    if (module != null) {
                        deployableModules.add(module);
                    }
                    
                    // Continue recursion
                    count += analyzeNestedModules(moduleModel, modulePath, projectRoot, deployableModules);
                }
            }
        }
        
        return count;
    }
    
    /**
     * Analyze a single module and determine if it's deployable.
     */
    private DeployableModule analyzeModule(Model model, Path modulePath, Path projectRoot) {
        String packaging = model.getPackaging() != null ? model.getPackaging() : "jar";
        PackagingType packagingType = PackagingType.fromString(packaging);
        
        log.debug("Analyzing module: {} with packaging: {}", model.getArtifactId(), packaging);
        
        // Check if module is deployable
        if (!packagingType.isDeployable()) {
            log.debug("Module {} is not deployable (packaging: {})", model.getArtifactId(), packaging);
            return null;
        }
        
        String groupId = resolveGroupId(model);
        String artifactId = model.getArtifactId();
        String version = resolveVersion(model);
        
        // Detect Spring Boot executable
        boolean isSpringBoot = springBootDetector.isSpringBootExecutable(model);
        String finalName = determineFinalName(model, artifactId, version, isSpringBoot);
        String classifier = determineClassifier(model, isSpringBoot);
        
        // Generate repository path
        String repositoryPath = pathGenerator.generatePath(groupId, artifactId, version,
                                                           finalName, packaging, classifier);

        // Calculate relative module path
        String relativeModulePath = projectRoot.relativize(modulePath).toString();
        if (relativeModulePath.isEmpty()) {
            relativeModulePath = ".";
        }

        // Detect Spring Boot profiles (only for Spring Boot executables)
        List<String> profiles = null;
        if (isSpringBoot) {
            profiles = profileDetector.detectProfiles(modulePath, model, projectRoot);
            if (profiles.isEmpty()) {
                profiles = null; // Don't include empty list in JSON
            }
        }

        // Detect assembly artifacts
        List<AssemblyArtifact> assemblyArtifacts = assemblyDetector.detectAssemblies(
                modulePath, model, groupId, artifactId, version);
        if (assemblyArtifacts.isEmpty()) {
            assemblyArtifacts = null; // Don't include empty list in JSON
        }

        // Detect deployment metadata
        String javaVersion = metadataDetector.detectJavaVersion(model);
        String mainClass = isSpringBoot ? metadataDetector.detectMainClass(model) : null;
        Boolean actuatorEnabled = isSpringBoot ? metadataDetector.detectActuatorEnabled(model) : null;

        // Detect environment-specific configurations
        List<com.larbotech.maven.descriptor.model.EnvironmentConfig> environments = null;
        if (isSpringBoot && profiles != null && !profiles.isEmpty()) {
            environments = environmentConfigDetector.detectEnvironmentConfigs(modulePath, profiles, actuatorEnabled);
            if (environments != null && environments.isEmpty()) {
                environments = null;
            }
        }

        List<String> localDeps = metadataDetector.detectLocalDependencies(model,
                resolveGroupId(model));
        if (localDeps != null && localDeps.isEmpty()) {
            localDeps = null;
        }

        // Detect executable plugins
        List<String> buildPlugins = executablePluginDetector.detectExecutablePlugins(model);
        if (buildPlugins.isEmpty()) {
            buildPlugins = null; // Don't include empty list in JSON
        }

        DeployableModule.DeployableModuleBuilder builder = DeployableModule.builder()
                .groupId(groupId)
                .artifactId(artifactId)
                .version(version)
                .packaging(packaging)
                .repositoryPath(repositoryPath)
                .finalName(finalName)
                .springBootExecutable(isSpringBoot)
                .classifier(classifier)
                .modulePath(relativeModulePath)
                .environments(environments)
                .assemblyArtifacts(assemblyArtifacts)
                .javaVersion(javaVersion)
                .mainClass(mainClass)
                .localDependencies(localDeps)
                .buildPlugins(buildPlugins);

        // Apply framework detectors via SPI
        for (FrameworkDetector detector : frameworkDetectors) {
            if (detector.isApplicable(model, modulePath)) {
                log.debug("Applying {} detector to module {}",
                         detector.getFrameworkName(), artifactId);
                detector.enrichModule(builder, model, modulePath, projectRoot);
            }
        }

        return builder.build();
    }
    
    /**
     * Parse a POM file into a Maven Model.
     */
    private Model parsePom(File pomFile) throws Exception {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        try (FileReader fileReader = new FileReader(pomFile)) {
            return reader.read(fileReader);
        }
    }
    
    /**
     * Resolve groupId (may be inherited from parent).
     */
    private String resolveGroupId(Model model) {
        if (model.getGroupId() != null) {
            return model.getGroupId();
        }
        if (model.getParent() != null && model.getParent().getGroupId() != null) {
            return model.getParent().getGroupId();
        }
        throw new IllegalStateException("Cannot resolve groupId for module: " + model.getArtifactId());
    }
    
    /**
     * Resolve version (may be inherited from parent).
     */
    private String resolveVersion(Model model) {
        if (model.getVersion() != null) {
            return model.getVersion();
        }
        if (model.getParent() != null && model.getParent().getVersion() != null) {
            return model.getParent().getVersion();
        }
        throw new IllegalStateException("Cannot resolve version for module: " + model.getArtifactId());
    }

    /**
     * Extract Maven repository URL from distributionManagement section.
     * Looks for repository or snapshotRepository URL.
     */
    private String extractMavenRepositoryUrl(Model model) {
        if (model.getDistributionManagement() == null) {
            log.debug("No distributionManagement found in POM");
            return null;
        }

        var distMgmt = model.getDistributionManagement();

        // Try to get release repository URL
        if (distMgmt.getRepository() != null && distMgmt.getRepository().getUrl() != null) {
            String url = distMgmt.getRepository().getUrl();
            log.info("Found Maven repository URL: {}", url);
            return url;
        }

        // Try to get snapshot repository URL
        if (distMgmt.getSnapshotRepository() != null && distMgmt.getSnapshotRepository().getUrl() != null) {
            String url = distMgmt.getSnapshotRepository().getUrl();
            log.info("Found Maven snapshot repository URL: {}", url);
            return url;
        }

        log.debug("No repository URL found in distributionManagement");
        return null;
    }

    /**
     * Determine the final name of the artifact.
     * This may be customized in the build configuration.
     */
    private String determineFinalName(Model model, String artifactId, String version, boolean isSpringBoot) {
        // Check if finalName is explicitly set in build configuration
        if (model.getBuild() != null && model.getBuild().getFinalName() != null) {
            return model.getBuild().getFinalName();
        }
        
        // Default Maven final name
        return artifactId + "-" + version;
    }
    
    /**
     * Determine classifier if any.
     * Spring Boot may add classifiers like "exec".
     */
    private String determineClassifier(Model model, boolean isSpringBoot) {
        if (isSpringBoot) {
            // Check Spring Boot plugin configuration for classifier
            String classifier = springBootDetector.getSpringBootClassifier(model);
            if (classifier != null && !classifier.isEmpty()) {
                return classifier;
            }
        }
        return null;
    }
}

