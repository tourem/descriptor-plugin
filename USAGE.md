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
  "generatedAt": [2025, 11, 9, 0, 20, 48, 83495000],
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
  "deployableModulesCount": 3
}
```

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

## Fonctionnalités détectées

Le plugin détecte automatiquement :

✅ **Modules déployables** : JAR, WAR, EAR  
✅ **Spring Boot** : Exécutables, profils, configurations  
✅ **Environnements** : dev, hml, prod avec configurations spécifiques  
✅ **Actuator** : Endpoints health, info, métriques  
✅ **Maven Assembly** : Artefacts ZIP, TAR.GZ
✅ **Métadonnées** : Version Java, classe principale, ports

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

