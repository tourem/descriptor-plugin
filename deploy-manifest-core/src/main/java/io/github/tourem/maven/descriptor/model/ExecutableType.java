package io.github.tourem.maven.descriptor.model;

/**
 * Type of executable Maven artifact.
 * @author tourem

 */
public enum ExecutableType {
    /**
     * JAR (Java ARchive) - standalone executable or library
     */
    JAR,

    /**
     * WAR (Web ARchive) - web application
     */
    WAR,

    /**
     * EAR (Enterprise ARchive) - enterprise application
     */
    EAR
}

