package io.github.tourem.maven.descriptor.model;

import lombok.Getter;

/**
 * Enum representing different Maven packaging types.
 * @author tourem

 */
@Getter
public enum PackagingType {
    JAR("jar"),
    WAR("war"),
    EAR("ear"),
    POM("pom"),
    MAVEN_PLUGIN("maven-plugin"),
    EJB("ejb"),
    RAR("rar");

    private final String type;

    PackagingType(String type) {
        this.type = type;
    }

    /**
     * Check if this packaging type is deployable.
     * POM and maven-plugin are not deployable.
     */
    public boolean isDeployable() {
        return switch (this) {
            case POM, MAVEN_PLUGIN -> false;
            case JAR, WAR, EAR, EJB, RAR -> true;
        };
    }

    /**
     * Parse packaging type from string.
     */
    public static PackagingType fromString(String type) {
        if (type == null || type.isEmpty()) {
            return JAR; // Default Maven packaging
        }

        return java.util.Arrays.stream(values())
                .filter(pt -> pt.type.equalsIgnoreCase(type))
                .findFirst()
                .orElse(JAR); // Default fallback
    }
}

