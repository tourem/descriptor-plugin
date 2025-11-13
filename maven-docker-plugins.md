# Plugins Maven pour la Construction d'Images Docker

## 📋 Vue d'ensemble

Ce document présente les principaux plugins Maven maintenus et utilisés pour construire des images Docker, avec leurs options de configuration pour le nom et la version des images.

---

## 1. 🚀 Jib Maven Plugin (Google)

**Statut**: ✅ Activement maintenu
**Repository**: https://github.com/GoogleContainerTools/jib

### Avantages
- Pas besoin de Docker daemon
- Build rapide avec layering optimisé
- Pas besoin de Dockerfile
- Supporte les registries privés

### Configuration de base

```xml
<plugin>
    <groupId>com.google.cloud.tools</groupId>
    <artifactId>jib-maven-plugin</artifactId>
    <version>3.4.0</version>
    <configuration>
        <from>
            <image>eclipse-temurin:21-jre</image>
        </from>
        <to>
            <image>mon-registry.com/mon-application</image>
            <tags>
                <tag>${project.version}</tag>
                <tag>latest</tag>
            </tags>
        </to>
        <container>
            <jvmFlags>
                <jvmFlag>-Xms512m</jvmFlag>
                <jvmFlag>-Xmx1024m</jvmFlag>
            </jvmFlags>
            <ports>
                <port>8080</port>
            </ports>
            <creationTime>USE_CURRENT_TIMESTAMP</creationTime>
        </container>
    </configuration>
</plugin>
```

### Options de nommage et versioning

```xml
<configuration>
    <to>
        <!-- Format: registry/repository/image-name -->
        <image>docker.io/mycompany/myapp</image>

        <!-- Utilisation de propriétés Maven -->
        <image>${docker.registry}/${project.artifactId}</image>

        <!-- Tags multiples -->
        <tags>
            <tag>${project.version}</tag>
            <tag>${git.commit.id.abbrev}</tag>
            <tag>latest</tag>
            <tag>${env.BUILD_NUMBER}</tag>
        </tags>
    </to>
</configuration>
```

### Commandes
```bash
# Build et push vers le registry
mvn compile jib:build

# Build vers le daemon Docker local
mvn compile jib:dockerBuild

# Build vers un fichier tar
mvn compile jib:buildTar
```

---

## 2. 🐳 Fabric8 Docker Maven Plugin

**Statut**: ✅ Activement maintenu
**Repository**: https://github.com/fabric8io/docker-maven-plugin

### Avantages
- Très flexible et configurable
- Support des Dockerfile personnalisés
- Gestion du cycle de vie complet
- Support multi-images

### Configuration de base

```xml
<plugin>
    <groupId>io.fabric8</groupId>
    <artifactId>docker-maven-plugin</artifactId>
    <version>0.44.0</version>
    <configuration>
        <images>
            <image>
                <name>mon-registry.com/${project.artifactId}:${project.version}</name>
                <alias>service-app</alias>
                <build>
                    <from>eclipse-temurin:21-jre</from>
                    <assembly>
                        <descriptorRef>artifact</descriptorRef>
                    </assembly>
                    <ports>
                        <port>8080</port>
                    </ports>
                    <cmd>
                        <exec>
                            <arg>java</arg>
                            <arg>-jar</arg>
                            <arg>/maven/${project.artifactId}-${project.version}.jar</arg>
                        </exec>
                    </cmd>
                </build>
                <run>
                    <ports>
                        <port>8080:8080</port>
                    </ports>
                </run>
            </image>
        </images>
    </configuration>
</plugin>
```

### Options avancées de nommage

```xml
<configuration>
    <images>
        <image>
            <!-- Pattern complexe avec propriétés -->
            <name>${docker.registry}/${docker.namespace}/${project.artifactId}:%l</name>

            <!-- %l = latest pour SNAPSHOT, version pour release -->
            <!-- %v = version du projet -->
            <!-- %t = timestamp -->

            <!-- Alternative avec tags multiples -->
            <name>${docker.registry}/${project.artifactId}</name>
            <build>
                <tags>
                    <tag>${project.version}</tag>
                    <tag>latest</tag>
                    <tag>${git.commit.id.abbrev}</tag>
                </tags>
            </build>
        </image>
    </images>
</configuration>
```

### Avec Dockerfile personnalisé

```xml
<configuration>
    <images>
        <image>
            <name>mon-registry.com/${project.artifactId}:${project.version}</name>
            <build>
                <dockerFile>${project.basedir}/Dockerfile</dockerFile>
                <contextDir>${project.basedir}</contextDir>
                <tags>
                    <tag>latest</tag>
                    <tag>${project.version}</tag>
                </tags>
            </build>
        </image>
    </images>
</configuration>
```

### Commandes
```bash
# Build l'image
mvn docker:build

# Start/stop les conteneurs
mvn docker:start
mvn docker:stop

# Push vers le registry
mvn docker:push

# Lifecycle complet
mvn clean package docker:build docker:push
```

---


## 4. 🍃 Spring Boot Maven Plugin (Build Image Native)

**Statut**: ✅ Activement maintenu (intégré à Spring Boot)
**Version**: 3.x+

### Avantages
- Intégré à Spring Boot
- Utilise Cloud Native Buildpacks
- Pas besoin de Dockerfile
- Support des images natives (GraalVM)

### Configuration

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <version>3.2.0</version>
    <configuration>
        <image>
            <name>${docker.registry}/${project.artifactId}:${project.version}</name>
            <tags>
                <tag>${project.version}</tag>
                <tag>latest</tag>
            </tags>
            <env>
                <BP_JVM_VERSION>21</BP_JVM_VERSION>
            </env>
            <publish>false</publish>
        </image>
        <docker>
            <publishRegistry>
                <username>${env.DOCKER_USERNAME}</username>
                <password>${env.DOCKER_PASSWORD}</password>
            </publishRegistry>
        </docker>
    </configuration>
</plugin>
```

### Configuration avancée avec Spring Boot

```xml
<configuration>
    <image>
        <!-- Nom de l'image avec pattern -->
        <name>${docker.registry}/${docker.namespace}/${project.artifactId}:${project.version}</name>

        <!-- Builder personnalisé -->
        <builder>paketobuildpacks/builder:base</builder>

        <!-- Image de base pour le runtime -->
        <runImage>paketobuildpacks/run:base</runImage>

        <!-- Tags additionnels -->
        <tags>
            <tag>latest</tag>
            <tag>${git.commit.id.abbrev}</tag>
        </tags>

        <!-- Variables d'environnement pour le build -->
        <env>
            <BP_JVM_VERSION>21</BP_JVM_VERSION>
            <BPE_JAVA_TOOL_OPTIONS>-Xms512m -Xmx1024m</BPE_JAVA_TOOL_OPTIONS>
        </env>

        <!-- Publish automatique -->
        <publish>true</publish>
    </image>
</configuration>
```

### Commandes
```bash
# Build l'image OCI
mvn spring-boot:build-image

# Build et publish
mvn spring-boot:build-image -Dspring-boot.build-image.publish=true

# Build image native (GraalVM)
mvn -Pnative spring-boot:build-image
```

---

## 5. 🔴 Quarkus Maven Plugin

**Statut**: ✅ Activement maintenu
**Version**: 3.x+

### Avantages
- Support natif des images Docker
- Support des images natives (GraalVM)
- Multiples stratégies de build
- Optimisé pour Kubernetes

### Configuration

```xml
<plugin>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-maven-plugin</artifactId>
    <version>3.6.0</version>
    <configuration>
        <containerImage>
            <registry>docker.io</registry>
            <group>mycompany</group>
            <name>${project.artifactId}</name>
            <tag>${project.version}</tag>
            <additionalTags>
                <additionalTag>latest</additionalTag>
            </additionalTags>
        </containerImage>
    </configuration>
</plugin>
```

### Configuration avec propriétés (application.properties)

```properties
# Configuration de l'image
quarkus.container-image.registry=docker.io
quarkus.container-image.group=mycompany
quarkus.container-image.name=${project.artifactId}
quarkus.container-image.tag=${project.version}
quarkus.container-image.additional-tags=latest,${git.commit.id.abbrev}

# Builder à utiliser (docker, jib, buildpack, etc.)
quarkus.container-image.builder=docker

# Push automatique
quarkus.container-image.push=false

# Image de base
quarkus.docker.dockerfile-jvm-path=src/main/docker/Dockerfile.jvm
```

### Différentes stratégies de build

```xml
<!-- Dans le pom.xml -->
<dependencies>
    <!-- Pour Docker -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-container-image-docker</artifactId>
    </dependency>

    <!-- OU pour Jib -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-container-image-jib</artifactId>
    </dependency>

    <!-- OU pour Buildpacks -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-container-image-buildpack</artifactId>
    </dependency>
</dependencies>
```

### Commandes
```bash
# Build JVM image
mvn clean package -Dquarkus.container-image.build=true

# Build et push
mvn clean package -Dquarkus.container-image.build=true -Dquarkus.container-image.push=true

# Build native image
mvn clean package -Pnative -Dquarkus.container-image.build=true
```

---

## 6. 🟦 Micronaut Maven Plugin

**Statut**: ✅ Activement maintenu
**Version**: 4.x+

### Avantages
- Support natif GraalVM
- Intégration avec différents builders
- Configuration flexible

### Configuration

```xml
<plugin>
    <groupId>io.micronaut.maven</groupId>
    <artifactId>micronaut-maven-plugin</artifactId>
    <version>4.2.1</version>
    <configuration>
        <dockerRegistry>docker.io</dockerRegistry>
        <dockerGroup>mycompany</dockerGroup>
        <dockerName>${project.artifactId}</dockerName>
        <dockerTag>${project.version}</dockerTag>
        <dockerExtraTags>
            <tag>latest</tag>
        </dockerExtraTags>
    </configuration>
</plugin>
```

### Configuration avancée

```xml
<plugin>
    <groupId>io.micronaut.maven</groupId>
    <artifactId>micronaut-maven-plugin</artifactId>
    <configuration>
        <appArguments>
            <appArgument>--server.port=8080</appArgument>
        </appArguments>
        <dockerRegistry>docker.io</dockerRegistry>
        <dockerGroup>mycompany</dockerGroup>
        <dockerName>${project.artifactId}</dockerName>
        <dockerTag>${project.version}</dockerTag>
        <dockerExtraTags>
            <tag>latest</tag>
            <tag>${git.commit.id.abbrev}</tag>
        </dockerExtraTags>
        <dockerBaseImage>eclipse-temurin:21-jre</dockerBaseImage>
        <dockerPush>false</dockerPush>
    </configuration>
</plugin>
```

### Commandes
```bash
# Build Docker image
mvn package -Dpackaging=docker

# Build native Docker image
mvn package -Dpackaging=docker-native

# Build avec Jib
mvn package -Dpackaging=docker-jib
```

---

---

## 7. 🧩 Eclipse JKube Kubernetes Maven Plugin

**Statut**: ✅ Activement maintenu
**Repository**: https://github.com/eclipse-jkube/jkube

### Avantages
- Outil unifié pour Kubernetes/Openshift (génération de manifests + build/push d'images)
- Multiple stratégies de build (Docker, Jib, S2I/Buildpacks via extensions)
- Intégration aisée avec des clusters K8s

### Configuration (build image)

```xml
<plugin>
    <groupId>org.eclipse.jkube</groupId>
    <artifactId>kubernetes-maven-plugin</artifactId>
    <version>1.16.0</version>
    <configuration>
        <images>
            <image>
                <name>docker.io/${docker.namespace}/${project.artifactId}</name>
                <build>
                    <tags>
                        <tag>${project.version}</tag>
                        <tag>latest</tag>
                    </tags>
                </build>
            </image>
        </images>
    </configuration>
</plugin>
```

### Commandes
```bash
# Build image
mvn k8s:build
# Push image
mvn k8s:push
```


## 📊 Tableau Comparatif

| Plugin | Maintenu | Sans Docker Daemon | Buildpacks | GraalVM | Complexité |
|--------|----------|-------------------|------------|---------|------------|
| **Jib** | ✅ | ✅ | ❌ | ❌ | Faible |
| **Fabric8** | ✅ | ❌ | ❌ | ❌ | Moyenne |
| **Spring Boot** | ✅ | ✅ | ✅ | ✅ | Faible |
| **Quarkus** | ✅ | Optionnel | ✅ | ✅ | Faible |
| **Micronaut** | ✅ | Optionnel | ❌ | ✅ | Faible |
| **Eclipse JKube** | ✅ | Optionnel | Optionnel | — | Moyenne |

---

## 🎨 Patterns de Nommage Recommandés

### Pattern Standard
```
${docker.registry}/${docker.namespace}/${project.artifactId}:${project.version}
```

### Exemples
```
docker.io/mycompany/my-service:1.0.0
registry.company.com/team/my-service:1.2.3-SNAPSHOT
ghcr.io/myorg/my-service:v1.0.0
```

### Propriétés Maven recommandées

```xml
<properties>
    <!-- Registry -->
    <docker.registry>docker.io</docker.registry>
    <docker.namespace>mycompany</docker.namespace>

    <!-- Ou combiné -->
    <docker.image.prefix>${docker.registry}/${docker.namespace}</docker.image.prefix>

    <!-- Tags -->
    <docker.image.tag>${project.version}</docker.tag>
</properties>
```

### Utilisation dans la configuration

```xml
<configuration>
    <to>
        <image>${docker.image.prefix}/${project.artifactId}:${docker.image.tag}</image>
    </to>
</configuration>
```

---

## 🔐 Authentification aux Registries

### Via settings.xml

```xml
<settings>
    <servers>
        <server>
            <id>docker.io</id>
            <username>${env.DOCKER_USERNAME}</username>
            <password>${env.DOCKER_PASSWORD}</password>
        </server>
        <server>
            <id>registry.company.com</id>
            <username>${env.REGISTRY_USERNAME}</username>
            <password>${env.REGISTRY_PASSWORD}</password>
        </server>
    </servers>
</settings>
```

### Via variables d'environnement

```bash
export DOCKER_USERNAME=myuser
export DOCKER_PASSWORD=mypassword
mvn clean package jib:build
```

---

## 💡 Bonnes Pratiques

### 1. Versioning
- Utilisez toujours `${project.version}` pour la cohérence
- Ajoutez un tag `latest` pour les releases
- Ajoutez le commit SHA pour la traçabilité: `${git.commit.id.abbrev}`

### 2. Nommage
- Format: `registry/namespace/app-name:version`
- Évitez les caractères spéciaux
- Utilisez des minuscules

### 3. Tags multiples
```xml
<tags>
    <tag>${project.version}</tag>
    <tag>latest</tag>
    <tag>${maven.build.timestamp}</tag>
    <tag>${git.commit.id.abbrev}</tag>
</tags>
```

### 4. Sécurité
- Ne committez jamais les credentials
- Utilisez des variables d'environnement
- Configurez dans `settings.xml`

---

## 🚀 Recommandations par Use Case

### Pour Spring Boot
**Recommandé**: Spring Boot Maven Plugin ou Jib
```bash
mvn spring-boot:build-image
# ou
mvn compile jib:build
```

### Pour Quarkus
**Recommandé**: Quarkus Container Image (avec Jib ou Docker)
```bash
mvn clean package -Dquarkus.container-image.build=true
```

### Pour Micronaut
**Recommandé**: Micronaut Maven Plugin
```bash
mvn package -Dpackaging=docker
```

### Pour applications legacy
**Recommandé**: Fabric8 Docker Maven Plugin
```bash
mvn clean package docker:build
```

### Sans Docker Daemon (CI/CD)
**Recommandé**: Jib
```bash
mvn compile jib:build
```

---

## 📚 Ressources Additionnelles

- [Jib Documentation](https://github.com/GoogleContainerTools/jib/tree/master/jib-maven-plugin)
- [Fabric8 Docker Maven Plugin](https://dmp.fabric8.io/)
- [Spring Boot Build Images](https://docs.spring.io/spring-boot/docs/current/maven-plugin/reference/htmlsingle/#build-image)
- [Quarkus Container Images](https://quarkus.io/guides/container-image)
- [Micronaut Maven Plugin](https://micronaut-projects.github.io/micronaut-maven-plugin/latest/)

---

**Dernière mise à jour**: Novembre 2025
