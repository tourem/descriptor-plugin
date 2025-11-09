# Descriptor Plugin - Guide d'utilisation

## Description

Le plugin Maven **Descriptor** génère automatiquement un descripteur JSON complet de votre projet Maven, incluant :

### 🎯 Fonctionnalités de base
- Les modules déployables (JAR, WAR, EAR)
- Les exécutables Spring Boot
- Les configurations par environnement (dev, hml, prod)
- Les endpoints Actuator
- Les artefacts Maven Assembly
- Les métadonnées de déploiement

### 🚀 Fonctionnalités avancées
- **Métadonnées Git et CI/CD** : Traçabilité complète (commit SHA, branche, auteur, provider CI)
- **Extensibilité par SPI** : Détection de frameworks pluggable (Spring Boot, Quarkus, Micronaut)
- **Mode dry-run** : Aperçu dans la console sans générer de fichiers
- **Documentation HTML** : Génération de rapports HTML lisibles
- **Hooks post-génération** : Exécution de scripts personnalisés

### 🎁 Fonctionnalités bonus
- Export multi-formats (JSON, YAML)
- Validation du descripteur
- Signature numérique SHA-256
- Compression GZIP
- Notifications webhook

## Installation

Le plugin est disponible dans votre repository Maven local après installation.

```xml
<plugin>
    <groupId>com.larbotech</groupId>
    <artifactId>descriptor-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
</plugin>
```

## Utilisation

### 1. Utilisation en ligne de commande

#### Génération simple (fichier à la racine du projet)
```bash
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate
```

Cela génère `descriptor.json` à la racine de votre projet.

#### Génération avec nom de fichier personnalisé
```bash
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate \
  -Ddescriptor.outputFile=deployment-info.json
```

#### Génération dans un répertoire spécifique
```bash
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate \
  -Ddescriptor.outputDirectory=target \
  -Ddescriptor.outputFile=deployment-descriptor.json
```

#### Désactiver le pretty print
```bash
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate \
  -Ddescriptor.prettyPrint=false
```

#### Générer une archive ZIP
```bash
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate \
  -Ddescriptor.format=zip
```
Résultat : `target/monapp-1.0-SNAPSHOT-descriptor.zip`

#### Générer une archive TAR.GZ avec classifier personnalisé
```bash
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate \
  -Ddescriptor.format=tar.gz \
  -Ddescriptor.classifier=deployment
```
Résultat : `target/monapp-1.0-SNAPSHOT-deployment.tar.gz`

#### Générer et attacher au projet pour déploiement
```bash
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate \
  -Ddescriptor.format=zip \
  -Ddescriptor.attach=true
```
L'artifact sera déployé vers le repository Maven lors de `mvn deploy`

#### Générer au format YAML
```bash
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate \
  -Ddescriptor.exportFormat=yaml
```
Résultat : `target/descriptor.yaml`

#### Générer JSON et YAML
```bash
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate \
  -Ddescriptor.exportFormat=both
```
Résultat : `target/descriptor.json` et `target/descriptor.yaml`

#### Générer avec validation et signature numérique
```bash
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate \
  -Ddescriptor.validate=true \
  -Ddescriptor.sign=true
```
Résultat : `target/descriptor.json` et `target/descriptor.json.sha256`

#### Générer avec compression
```bash
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate \
  -Ddescriptor.compress=true
```
Résultat : `target/descriptor.json` et `target/descriptor.json.gz`

#### Envoyer une notification webhook
```bash
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate \
  -Ddescriptor.webhookUrl=https://api.example.com/webhooks/descriptor \
  -Ddescriptor.webhookToken=votre-token-secret
```
Envoie un HTTP POST avec le contenu du descripteur vers l'URL spécifiée

#### Mode dry-run (aperçu sans générer de fichiers)
```bash
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate \
  -Ddescriptor.summary=true
```
Affiche un tableau de bord ASCII dans la console avec un aperçu du projet

#### Générer la documentation HTML
```bash
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate \
  -Ddescriptor.generateHtml=true
```
Résultat : `target/descriptor.html` - Page HTML lisible pour les équipes non techniques

#### Exécuter un hook post-génération
```bash
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate \
  -Ddescriptor.postGenerationHook="./scripts/notifier.sh"
```
Exécute un script/commande local après la génération du descripteur

#### Toutes les fonctionnalités combinées
```bash
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate \
  -Ddescriptor.exportFormat=both \
  -Ddescriptor.validate=true \
  -Ddescriptor.sign=true \
  -Ddescriptor.compress=true \
  -Ddescriptor.format=zip \
  -Ddescriptor.attach=true \
  -Ddescriptor.generateHtml=true \
  -Ddescriptor.webhookUrl=https://api.example.com/webhooks/descriptor \
  -Ddescriptor.postGenerationHook="echo 'Descripteur généré!'"
```

### 2. Configuration dans le POM

Vous pouvez configurer le plugin directement dans votre `pom.xml` :

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.larbotech</groupId>
            <artifactId>descriptor-plugin</artifactId>
            <version>1.0-SNAPSHOT</version>
            <configuration>
                <!-- Nom du fichier de sortie (défaut: descriptor.json) -->
                <outputFile>deployment-info.json</outputFile>

                <!-- Répertoire de sortie (défaut: racine du projet) -->
                <outputDirectory>target</outputDirectory>

                <!-- Pretty print JSON (défaut: true) -->
                <prettyPrint>true</prettyPrint>

                <!-- Skip l'exécution du plugin (défaut: false) -->
                <skip>false</skip>

                <!-- Format d'archive: zip, tar.gz, tar.bz2, jar (défaut: aucun) -->
                <format>zip</format>

                <!-- Classifier pour l'artifact (défaut: descriptor) -->
                <classifier>descriptor</classifier>

                <!-- Attacher l'artifact au projet pour déploiement (défaut: false) -->
                <attach>true</attach>
            </configuration>
            <executions>
                <execution>
                    <id>generate-descriptor</id>
                    <phase>package</phase>
                    <goals>
                        <goal>generate</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### 3. Exécution automatique pendant le build

Avec la configuration ci-dessus, le descripteur sera généré automatiquement lors de la phase `package` :

```bash
mvn clean package
```

## Paramètres de configuration

| Paramètre | Propriété système | Défaut | Description |
|-----------|------------------|--------|-------------|
| `outputFile` | `descriptor.outputFile` | `descriptor.json` | Nom du fichier JSON de sortie |
| `outputDirectory` | `descriptor.outputDirectory` | `${project.build.directory}` (target/) | Répertoire de sortie (absolu ou relatif) |
| `prettyPrint` | `descriptor.prettyPrint` | `true` | Formater le JSON avec indentation |
| `skip` | `descriptor.skip` | `false` | Ignorer l'exécution du plugin |
| `format` | `descriptor.format` | aucun | Format d'archive: `zip`, `tar.gz`, `tar.bz2`, `jar` |
| `classifier` | `descriptor.classifier` | `descriptor` | Classifier pour l'artifact attaché |
| `attach` | `descriptor.attach` | `false` | Attacher l'artifact au projet pour déploiement |
| `exportFormat` | `descriptor.exportFormat` | `json` | Format d'export: `json`, `yaml`, `both` |
| `validate` | `descriptor.validate` | `false` | Valider la structure du descripteur |
| `sign` | `descriptor.sign` | `false` | Générer une signature numérique SHA-256 |
| `compress` | `descriptor.compress` | `false` | Compresser le JSON avec GZIP |
| `webhookUrl` | `descriptor.webhookUrl` | aucun | URL HTTP pour notification après génération |
| `webhookToken` | `descriptor.webhookToken` | aucun | Token Bearer pour authentification webhook |
| `webhookTimeout` | `descriptor.webhookTimeout` | `10` | Timeout du webhook en secondes |

## Exemple de sortie

```json
{
  "projectGroupId": "com.larbotech",
  "projectArtifactId": "github-actions-project",
  "projectVersion": "1.0-SNAPSHOT",
  "projectName": "github-actions-project",
  "projectDescription": "Projet multi-modules avec API REST et Batch",
  "generatedAt": "2025-11-09T14:20:48.083495",
  "deployableModules": [
    {
      "groupId": "com.larbotech",
      "artifactId": "task-api",
      "version": "1.0-SNAPSHOT",
      "packaging": "jar",
      "repositoryPath": "com/larbotech/task-api/1.0-SNAPSHOT/task-api-1.0-SNAPSHOT.jar",
      "finalName": "task-api",
      "springBootExecutable": true,
      "modulePath": "task-api",
      "environments": [
        {
          "profile": "dev",
          "serverPort": 8080,
          "contextPath": "/api/v1",
          "actuatorEnabled": true,
          "actuatorBasePath": "/actuator",
          "actuatorHealthPath": "/actuator/health",
          "actuatorInfoPath": "/actuator/info"
        }
      ],
      "assemblyArtifacts": [
        {
          "assemblyId": "distribution",
          "format": "zip",
          "repositoryPath": "com/larbotech/task-api/1.0-SNAPSHOT/task-api-1.0-SNAPSHOT.zip"
        }
      ],
      "mainClass": "com.larbotech.taskapi.TaskApiApplication",
      "buildPlugins": ["spring-boot-maven-plugin", "maven-assembly-plugin"]
    }
  ],
  "totalModules": 4,
  "deployableModulesCount": 3,
  "buildInfo": {
    "gitCommitSha": "a6b5ba8f2c1d3e4f5a6b7c8d9e0f1a2b3c4d5e6f",
    "gitCommitShortSha": "a6b5ba8",
    "gitBranch": "feature/advanced-features",
    "gitDirty": false,
    "gitRemoteUrl": "https://github.com/tourem/github-actions-project.git",
    "gitCommitMessage": "feat: Ajout des fonctionnalités avancées",
    "gitCommitAuthor": "Mohamed Touré",
    "gitCommitTime": "2025-11-09T13:15:30",
    "buildTimestamp": "2025-11-09T14:20:48.083495",
    "buildHost": "macbook-pro.local",
    "buildUser": "mtoure"
  }
}
```

> **Note** : La section `buildInfo` est **collectée automatiquement** lors de l'exécution du plugin. Elle inclut les métadonnées Git (commit, branche, auteur) et les informations de build (timestamp, host, utilisateur). Si le build s'exécute dans un environnement CI/CD (GitHub Actions, GitLab CI, Jenkins, etc.), des métadonnées CI supplémentaires seront incluses.

## Cas d'usage

### CI/CD Pipeline

Utilisez le descripteur généré dans vos pipelines CI/CD pour automatiser le déploiement :

```yaml
# GitHub Actions example
- name: Generate deployment descriptor
  run: mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate

- name: Deploy using descriptor
  run: |
    DESCRIPTOR=$(cat descriptor.json)
    # Parse JSON and deploy modules
```

### Scripts de déploiement

```bash
#!/bin/bash
# deploy.sh

# Générer le descripteur
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate

# Parser et déployer chaque module
jq -r '.deployableModules[] | select(.springBootExecutable == true) | .artifactId' descriptor.json | while read module; do
    echo "Deploying $module..."
    # Logique de déploiement
done
```

## Métadonnées Git et CI/CD (Collecte Automatique)

### 🔍 Comment ça fonctionne

Le plugin **collecte automatiquement** les métadonnées Git et CI/CD pour une traçabilité complète. **Aucune configuration nécessaire !**

Lors de l'exécution du plugin, il :
1. ✅ Détecte si le projet est dans un dépôt Git
2. ✅ Collecte les métadonnées Git (commit, branche, auteur, etc.)
3. ✅ Détecte les variables d'environnement CI/CD
4. ✅ Ajoute toutes les métadonnées dans la section `buildInfo` du descripteur

### 📊 Métadonnées Git collectées

- **Commit SHA** (version complète et courte de 7 caractères)
- **Nom de la branche** (ex: `main`, `develop`, `feature/xyz`)
- **Tag** (si le commit actuel est taggé, ex: `v1.0.0`)
- **État dirty** (présence de modifications non commitées)
- **URL du remote** (ex: `https://github.com/user/repo.git`)
- **Message du commit** (dernier message de commit)
- **Auteur du commit** (nom de l'auteur)
- **Timestamp du commit** (date et heure du commit)

### 🏗️ Métadonnées de build collectées

- **Timestamp du build** (quand le descripteur a été généré)
- **Host du build** (nom de la machine exécutant le build)
- **Utilisateur du build** (nom d'utilisateur exécutant le build)

### 🚀 Providers CI/CD détectés

Le plugin détecte automatiquement et collecte les métadonnées de :

| Provider | Variables d'environnement utilisées |
|----------|-------------------------------------|
| **GitHub Actions** | `GITHUB_ACTIONS`, `GITHUB_RUN_ID`, `GITHUB_RUN_NUMBER`, `GITHUB_WORKFLOW`, `GITHUB_ACTOR`, `GITHUB_EVENT_NAME`, `GITHUB_REPOSITORY` |
| **GitLab CI** | `GITLAB_CI`, `CI_PIPELINE_ID`, `CI_PIPELINE_IID`, `CI_PIPELINE_URL`, `CI_JOB_NAME`, `CI_COMMIT_REF_NAME`, `GITLAB_USER_LOGIN` |
| **Jenkins** | `JENKINS_URL`, `BUILD_ID`, `BUILD_NUMBER`, `BUILD_URL`, `JOB_NAME`, `GIT_BRANCH`, `BUILD_USER` |
| **Travis CI** | `TRAVIS`, `TRAVIS_BUILD_ID`, `TRAVIS_BUILD_NUMBER`, `TRAVIS_BUILD_WEB_URL`, `TRAVIS_JOB_NAME`, `TRAVIS_EVENT_TYPE` |
| **CircleCI** | `CIRCLECI`, `CIRCLE_BUILD_NUM`, `CIRCLE_BUILD_URL`, `CIRCLE_JOB`, `CIRCLE_USERNAME` |
| **Azure Pipelines** | `TF_BUILD`, `BUILD_BUILDID`, `BUILD_BUILDNUMBER`, `BUILD_DEFINITIONNAME`, `BUILD_REQUESTEDFOR` |

### 📝 Exemple de sortie (Build local)

```json
{
  "buildInfo": {
    "gitCommitSha": "a6b5ba8f2c1d3e4f5a6b7c8d9e0f1a2b3c4d5e6f",
    "gitCommitShortSha": "a6b5ba8",
    "gitBranch": "feature/advanced-features",
    "gitDirty": false,
    "gitRemoteUrl": "https://github.com/tourem/github-actions-project.git",
    "gitCommitMessage": "feat: Ajout des fonctionnalités avancées",
    "gitCommitAuthor": "Mohamed Touré",
    "gitCommitTime": "2025-11-09T13:15:30",
    "buildTimestamp": "2025-11-09T14:20:48.083495",
    "buildHost": "macbook-pro.local",
    "buildUser": "mtoure"
  }
}
```

### 📝 Exemple de sortie (GitHub Actions)

```json
{
  "buildInfo": {
    "gitCommitSha": "77e6c5e7e2b98b46a5601d81d6ecbe06b2b450cc",
    "gitCommitShortSha": "77e6c5e",
    "gitBranch": "main",
    "gitTag": "v1.0.0",
    "gitDirty": false,
    "gitRemoteUrl": "https://github.com/tourem/github-actions-project.git",
    "gitCommitMessage": "feat: Nouvelle fonctionnalité",
    "gitCommitAuthor": "Mohamed Touré",
    "gitCommitTime": "2025-11-09T12:13:37",
    "ciProvider": "GitHub Actions",
    "ciBuildId": "123456789",
    "ciBuildNumber": "42",
    "ciBuildUrl": "https://github.com/tourem/github-actions-project/actions/runs/123456789",
    "ciJobName": "build",
    "ciActor": "mtoure",
    "ciEventName": "push",
    "buildTimestamp": "2025-11-09T14:06:02.951024",
    "buildHost": "runner-xyz",
    "buildUser": "runner"
  }
}
```

### 💡 Cas d'usage

**Traçabilité** : Savoir exactement quel commit Git a été utilisé pour construire chaque artefact
```bash
# Extraire le SHA du commit depuis le descripteur
jq -r '.buildInfo.gitCommitSha' descriptor.json
# Sortie : a6b5ba8f2c1d3e4f5a6b7c8d9e0f1a2b3c4d5e6f
```

**Reproductibilité** : Reconstruire exactement la même version
```bash
# Récupérer le commit et reconstruire
COMMIT=$(jq -r '.buildInfo.gitCommitSha' descriptor.json)
git checkout $COMMIT
mvn clean package
```

**Audit** : Tracer qui a construit quoi et quand
```bash
# Afficher les informations de build
jq '.buildInfo | {auteur: .gitCommitAuthor, timestamp: .buildTimestamp, host: .buildHost}' descriptor.json
```

## Détection de frameworks (SPI)

### 🔌 Extensibilité par SPI

Le plugin utilise une architecture **Service Provider Interface (SPI)** pour la détection de frameworks, ce qui le rend facilement extensible.

### 📦 Détecteurs intégrés

Le plugin inclut les détecteurs suivants :

| Framework | Détecteur | Description |
|-----------|-----------|-------------|
| **Spring Boot** | `SpringBootFrameworkDetector` | Détecte les applications Spring Boot, collecte les profils, configurations, actuator |
| **Quarkus** | `QuarkusFrameworkDetector` | Exemple de détecteur pour Quarkus (prêt pour extension) |

### 🔍 Comment ça fonctionne

Lors de l'analyse d'un module, le plugin :
1. ✅ Charge tous les détecteurs de frameworks via ServiceLoader
2. ✅ Vérifie si chaque détecteur est applicable au module
3. ✅ Exécute les détecteurs applicables par ordre de priorité
4. ✅ Enrichit le module avec les métadonnées spécifiques au framework

### 📊 Logs de détection

Lors de l'exécution, vous verrez :
```
[INFO] Loaded 2 framework detectors: Spring Boot, Quarkus
[INFO] Analyzing Maven project at: /Users/mtoure/dev/github-actions-project
```

### 🛠️ Créer un détecteur personnalisé

Pour ajouter le support d'un nouveau framework (Micronaut, Helidon, etc.) :

1. **Créer une classe implémentant `FrameworkDetector`** :
```java
public class MicronautFrameworkDetector implements FrameworkDetector {
    @Override
    public String getFrameworkName() {
        return "Micronaut";
    }

    @Override
    public boolean isApplicable(Model model, Path modulePath) {
        // Vérifier la présence de dépendances Micronaut
        return model.getDependencies().stream()
            .anyMatch(d -> d.getGroupId().equals("io.micronaut"));
    }

    @Override
    public void enrichModule(DeployableModule.DeployableModuleBuilder builder,
                            Model model, Path modulePath, Path projectRoot) {
        // Ajouter les métadonnées spécifiques à Micronaut
        builder.mainClass(detectMainClass(model));
    }

    @Override
    public int getPriority() {
        return 80; // Priorité d'exécution
    }
}
```

2. **Enregistrer via ServiceLoader** dans `META-INF/services/com.larbotech.maven.descriptor.spi.FrameworkDetector` :
```
com.example.MicronautFrameworkDetector
```

3. **Ajouter le JAR au classpath** du plugin dans votre `pom.xml` :
```xml
<plugin>
    <groupId>com.larbotech</groupId>
    <artifactId>descriptor-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
    <dependencies>
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>micronaut-detector</artifactId>
            <version>1.0.0</version>
        </dependency>
    </dependencies>
</plugin>
```

## Fonctionnalités détectées

Le plugin détecte automatiquement :

✅ **Modules déployables** : JAR, WAR, EAR
✅ **Spring Boot** : Exécutables, profils, configurations
✅ **Environnements** : dev, hml, prod avec configurations spécifiques
✅ **Actuator** : Endpoints health, info, métriques
✅ **Maven Assembly** : Artefacts ZIP, TAR.GZ
✅ **Métadonnées Git/CI** : Commit, branche, auteur, provider CI (automatique)
✅ **Métadonnées de build** : Version Java, classe principale, ports
✅ **Frameworks** : Spring Boot, Quarkus (extensible via SPI)

## Formats d'archive et déploiement

Le plugin supporte la création d'archives du fichier JSON descriptor, similaire au comportement du `maven-assembly-plugin`.

### Formats d'archive supportés

| Format | Extension | Description |
|--------|-----------|-------------|
| `zip` | `.zip` | Archive ZIP (le plus courant) |
| `jar` | `.zip` | Archive JAR (identique à ZIP) |
| `tar.gz` | `.tar.gz` | Archive TAR compressée avec Gzip |
| `tgz` | `.tar.gz` | Alias pour tar.gz |
| `tar.bz2` | `.tar.bz2` | Archive TAR compressée avec Bzip2 |
| `tbz2` | `.tar.bz2` | Alias pour tar.bz2 |

### Convention de nommage

Les archives suivent la convention Maven standard :

```
{artifactId}-{version}-{classifier}.{extension}
```

Exemples :
- `monapp-1.0.0-descriptor.zip`
- `monapp-1.0.0-deployment.tar.gz`

### Déploiement vers Maven Repository

Lorsque `attach=true`, l'archive est déployée vers Nexus/JFrog lors de `mvn deploy`.

**Exemple :**

```bash
mvn clean deploy
```

L'archive sera disponible dans le repository :
```
com/larbotech/monapp/1.0.0/
├── monapp-1.0.0.jar
├── monapp-1.0.0-descriptor.zip  ← Archive descriptor
```

### Téléchargement depuis le repository

```bash
# Maven dependency plugin
mvn dependency:get \
  -Dartifact=com.larbotech:monapp:1.0.0:zip:descriptor \
  -Ddest=./descriptor.zip

# Curl (Nexus)
curl -u user:password \
  https://nexus.example.com/.../monapp-1.0.0-descriptor.zip \
  -o descriptor.zip
```

## Support

Pour toute question ou problème, veuillez créer une issue sur le repository GitHub.

