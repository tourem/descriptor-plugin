# Prompt : Dependency Tree Feature - Spécification et Design

## 🎯 Contexte

Je développe un plugin Maven (`deploy-manifest-plugin`) qui génère des descripteurs de déploiement au format JSON/YAML/HTML. Le plugin analyse automatiquement les projets Maven et extrait les métadonnées de déploiement.

**Repository GitHub** : https://github.com/tourem/deploy-manifest-plugin

**Descripteur actuel** : Le plugin génère actuellement des informations sur les modules, Spring Boot, Docker images, Git metadata, etc.

## 🎯 Objectif

Ajouter une fonctionnalité pour **inclure l'arbre de dépendances Maven** dans le descripteur généré, avec les caractéristiques suivantes :

### Exigences

1. **Feature optionnelle** : Désactivée par défaut pour maintenir la rétrocompatibilité
2. **Niveaux de détail configurables** : Limiter la profondeur de l'arbre (depth)
3. **Filtrage par scope** : Exclure certains scopes (test, provided, etc.)
4. **Multiple formats** : flat (liste avec profondeur) et tree (hiérarchique)
5. **Statistiques** : Résumé du nombre de dépendances
6. **Interface HTML interactive** : Recherche, filtrage, visualisation
7. **Multi-module** : Support des projets Maven reactor

---

## 📋 Paramètres de Configuration

### CLI Usage

```bash
# Activer la feature (désactivée par défaut)
mvn deploy-manifest-plugin:generate -Ddescriptor.includeDependencyTree=true

# Limiter la profondeur à 2 niveaux
mvn deploy-manifest-plugin:generate \
  -Ddescriptor.includeDependencyTree=true \
  -Ddescriptor.dependencyTreeDepth=2

# Filtrer les scopes (compile et runtime uniquement)
mvn deploy-manifest-plugin:generate \
  -Ddescriptor.includeDependencyTree=true \
  -Ddescriptor.dependencyScopes=compile,runtime

# Format tree au lieu de flat
mvn deploy-manifest-plugin:generate \
  -Ddescriptor.includeDependencyTree=true \
  -Ddescriptor.dependencyTreeFormat=tree

# Seulement les dépendances directes (pas de transitives)
mvn deploy-manifest-plugin:generate \
  -Ddescriptor.includeDependencyTree=true \
  -Ddescriptor.excludeTransitive=true

# Les deux formats (flat + tree)
mvn deploy-manifest-plugin:generate \
  -Ddescriptor.includeDependencyTree=true \
  -Ddescriptor.dependencyTreeFormat=both
```

### POM Configuration

```xml
<plugin>
    <groupId>io.github.tourem</groupId>
    <artifactId>deploy-manifest-plugin</artifactId>
    <version>1.4.0</version>
    <configuration>
        <!-- Activer l'arbre de dépendances -->
        <includeDependencyTree>true</includeDependencyTree>
        
        <!-- Profondeur (-1 = illimité, 0 = directes uniquement, 1,2,3... = niveaux) -->
        <dependencyTreeDepth>-1</dependencyTreeDepth>
        
        <!-- Scopes à inclure (défaut: compile,runtime) -->
        <dependencyScopes>compile,runtime</dependencyScopes>
        
        <!-- Format (flat, tree, both) -->
        <dependencyTreeFormat>flat</dependencyTreeFormat>
        
        <!-- Exclure les dépendances transitives -->
        <excludeTransitive>false</excludeTransitive>
        
        <!-- Inclure les dépendances optionnelles -->
        <includeOptional>false</includeOptional>
    </configuration>
</plugin>
```

### Tableau des Paramètres

| Paramètre | System Property | Valeur par Défaut | Description |
|-----------|----------------|-------------------|-------------|
| `includeDependencyTree` | `descriptor.includeDependencyTree` | `false` | Activer/désactiver la feature |
| `dependencyTreeDepth` | `descriptor.dependencyTreeDepth` | `-1` | Profondeur (-1=illimité, 0=direct, 1,2,3...) |
| `dependencyScopes` | `descriptor.dependencyScopes` | `compile,runtime` | Scopes à inclure (CSV) |
| `dependencyTreeFormat` | `descriptor.dependencyTreeFormat` | `flat` | Format (flat, tree, both) |
| `excludeTransitive` | `descriptor.excludeTransitive` | `false` | Exclure les dépendances transitives |
| `includeOptional` | `descriptor.includeOptional` | `false` | Inclure les dépendances optionnelles |

---

## 📄 Exemples de Sorties Attendues

### Exemple 1 : Format Flat (Liste avec Profondeur)

**Commande** :
```bash
mvn deploy-manifest-plugin:generate \
  -Ddescriptor.includeDependencyTree=true \
  -Ddescriptor.dependencyTreeFormat=flat
```

**Sortie JSON** (`descriptor.json`) :

```json
{
  "project": {
    "groupId": "com.example",
    "artifactId": "my-app",
    "version": "1.0.0",
    "buildTime": "2025-11-12T10:30:00Z"
  },
  "modules": [
    {
      "groupId": "com.example",
      "artifactId": "backend-service",
      "version": "1.0.0",
      "packaging": "jar",
      "path": "backend-service",
      
      "springBoot": {
        "version": "3.2.0",
        "mainClass": "com.example.Application"
      },
      
      "dependencies": {
        "summary": {
          "total": 87,
          "direct": 12,
          "transitive": 75,
          "scopes": {
            "compile": 45,
            "runtime": 30,
            "provided": 12
          },
          "optional": 3
        },
        
        "flat": [
          {
            "groupId": "org.springframework.boot",
            "artifactId": "spring-boot-starter-web",
            "version": "3.2.0",
            "scope": "compile",
            "type": "jar",
            "optional": false,
            "depth": 1,
            "path": "org.springframework.boot:spring-boot-starter-web:jar:3.2.0"
          },
          {
            "groupId": "org.springframework.boot",
            "artifactId": "spring-boot-starter",
            "version": "3.2.0",
            "scope": "compile",
            "type": "jar",
            "optional": false,
            "depth": 2,
            "path": "org.springframework.boot:spring-boot-starter-web:jar:3.2.0 -> org.springframework.boot:spring-boot-starter:jar:3.2.0"
          },
          {
            "groupId": "org.springframework.boot",
            "artifactId": "spring-boot",
            "version": "3.2.0",
            "scope": "compile",
            "type": "jar",
            "optional": false,
            "depth": 3,
            "path": "org.springframework.boot:spring-boot-starter-web:jar:3.2.0 -> org.springframework.boot:spring-boot-starter:jar:3.2.0 -> org.springframework.boot:spring-boot:jar:3.2.0"
          },
          {
            "groupId": "org.springframework",
            "artifactId": "spring-core",
            "version": "6.1.0",
            "scope": "compile",
            "type": "jar",
            "optional": false,
            "depth": 3,
            "path": "org.springframework.boot:spring-boot-starter-web:jar:3.2.0 -> org.springframework.boot:spring-boot-starter:jar:3.2.0 -> org.springframework:spring-core:jar:6.1.0"
          },
          {
            "groupId": "org.springframework",
            "artifactId": "spring-web",
            "version": "6.1.0",
            "scope": "compile",
            "type": "jar",
            "optional": false,
            "depth": 2,
            "path": "org.springframework.boot:spring-boot-starter-web:jar:3.2.0 -> org.springframework:spring-web:jar:6.1.0"
          },
          {
            "groupId": "org.springframework",
            "artifactId": "spring-webmvc",
            "version": "6.1.0",
            "scope": "compile",
            "type": "jar",
            "optional": false,
            "depth": 2,
            "path": "org.springframework.boot:spring-boot-starter-web:jar:3.2.0 -> org.springframework:spring-webmvc:jar:6.1.0"
          },
          {
            "groupId": "com.fasterxml.jackson.core",
            "artifactId": "jackson-databind",
            "version": "2.15.3",
            "scope": "compile",
            "type": "jar",
            "optional": false,
            "depth": 2,
            "path": "org.springframework.boot:spring-boot-starter-web:jar:3.2.0 -> com.fasterxml.jackson.core:jackson-databind:jar:2.15.3"
          },
          {
            "groupId": "com.fasterxml.jackson.core",
            "artifactId": "jackson-core",
            "version": "2.15.3",
            "scope": "compile",
            "type": "jar",
            "optional": false,
            "depth": 3,
            "path": "org.springframework.boot:spring-boot-starter-web:jar:3.2.0 -> com.fasterxml.jackson.core:jackson-databind:jar:2.15.3 -> com.fasterxml.jackson.core:jackson-core:jar:2.15.3"
          },
          {
            "groupId": "org.apache.logging.log4j",
            "artifactId": "log4j-api",
            "version": "2.21.1",
            "scope": "compile",
            "type": "jar",
            "optional": false,
            "depth": 1,
            "path": "org.apache.logging.log4j:log4j-api:jar:2.21.1"
          },
          {
            "groupId": "org.slf4j",
            "artifactId": "slf4j-api",
            "version": "2.0.9",
            "scope": "compile",
            "type": "jar",
            "optional": false,
            "depth": 2,
            "path": "org.springframework.boot:spring-boot-starter-web:jar:3.2.0 -> org.slf4j:slf4j-api:jar:2.0.9"
          },
          {
            "groupId": "org.projectlombok",
            "artifactId": "lombok",
            "version": "1.18.30",
            "scope": "provided",
            "type": "jar",
            "optional": true,
            "depth": 1,
            "path": "org.projectlombok:lombok:jar:1.18.30"
          },
          {
            "groupId": "junit",
            "artifactId": "junit",
            "version": "4.13.2",
            "scope": "test",
            "type": "jar",
            "optional": false,
            "depth": 1,
            "path": "junit:junit:jar:4.13.2"
          }
        ]
      }
    }
  ]
}
```

---

### Exemple 2 : Format Tree (Hiérarchique)

**Commande** :
```bash
mvn deploy-manifest-plugin:generate \
  -Ddescriptor.includeDependencyTree=true \
  -Ddescriptor.dependencyTreeFormat=tree \
  -Ddescriptor.dependencyTreeDepth=2
```

**Sortie JSON** (`descriptor.json`) :

```json
{
  "project": {
    "groupId": "com.example",
    "artifactId": "my-app",
    "version": "1.0.0"
  },
  "modules": [
    {
      "artifactId": "backend-service",
      "packaging": "jar",
      
      "dependencies": {
        "summary": {
          "total": 45,
          "direct": 8,
          "transitive": 37,
          "scopes": {
            "compile": 30,
            "runtime": 15
          },
          "optional": 0
        },
        
        "tree": [
          {
            "groupId": "org.springframework.boot",
            "artifactId": "spring-boot-starter-web",
            "version": "3.2.0",
            "scope": "compile",
            "type": "jar",
            "optional": false,
            "children": [
              {
                "groupId": "org.springframework.boot",
                "artifactId": "spring-boot-starter",
                "version": "3.2.0",
                "scope": "compile",
                "type": "jar",
                "optional": false,
                "children": []
              },
              {
                "groupId": "org.springframework",
                "artifactId": "spring-web",
                "version": "6.1.0",
                "scope": "compile",
                "type": "jar",
                "optional": false,
                "children": []
              },
              {
                "groupId": "org.springframework",
                "artifactId": "spring-webmvc",
                "version": "6.1.0",
                "scope": "compile",
                "type": "jar",
                "optional": false,
                "children": []
              },
              {
                "groupId": "com.fasterxml.jackson.core",
                "artifactId": "jackson-databind",
                "version": "2.15.3",
                "scope": "compile",
                "type": "jar",
                "optional": false,
                "children": []
              }
            ]
          },
          {
            "groupId": "org.springframework.boot",
            "artifactId": "spring-boot-starter-data-jpa",
            "version": "3.2.0",
            "scope": "compile",
            "type": "jar",
            "optional": false,
            "children": [
              {
                "groupId": "org.hibernate.orm",
                "artifactId": "hibernate-core",
                "version": "6.3.1",
                "scope": "compile",
                "type": "jar",
                "optional": false,
                "children": []
              },
              {
                "groupId": "jakarta.persistence",
                "artifactId": "jakarta.persistence-api",
                "version": "3.1.0",
                "scope": "compile",
                "type": "jar",
                "optional": false,
                "children": []
              }
            ]
          },
          {
            "groupId": "org.postgresql",
            "artifactId": "postgresql",
            "version": "42.7.1",
            "scope": "runtime",
            "type": "jar",
            "optional": false,
            "children": []
          }
        ]
      }
    }
  ]
}
```

---

### Exemple 3 : Les Deux Formats (Both)

**Commande** :
```bash
mvn deploy-manifest-plugin:generate \
  -Ddescriptor.includeDependencyTree=true \
  -Ddescriptor.dependencyTreeFormat=both \
  -Ddescriptor.dependencyScopes=compile
```

**Sortie JSON** (`descriptor.json`) :

```json
{
  "modules": [
    {
      "artifactId": "backend-service",
      
      "dependencies": {
        "summary": {
          "total": 30,
          "direct": 5,
          "transitive": 25,
          "scopes": {
            "compile": 30
          },
          "optional": 0
        },
        
        "flat": [
          {
            "groupId": "org.springframework.boot",
            "artifactId": "spring-boot-starter-web",
            "version": "3.2.0",
            "scope": "compile",
            "type": "jar",
            "optional": false,
            "depth": 1,
            "path": "org.springframework.boot:spring-boot-starter-web:jar:3.2.0"
          },
          {
            "groupId": "org.springframework",
            "artifactId": "spring-core",
            "version": "6.1.0",
            "scope": "compile",
            "type": "jar",
            "optional": false,
            "depth": 2,
            "path": "org.springframework.boot:spring-boot-starter-web:jar:3.2.0 -> org.springframework:spring-core:jar:6.1.0"
          }
        ],
        
        "tree": [
          {
            "groupId": "org.springframework.boot",
            "artifactId": "spring-boot-starter-web",
            "version": "3.2.0",
            "scope": "compile",
            "type": "jar",
            "optional": false,
            "children": [
              {
                "groupId": "org.springframework",
                "artifactId": "spring-core",
                "version": "6.1.0",
                "scope": "compile",
                "type": "jar",
                "optional": false,
                "children": []
              }
            ]
          }
        ]
      }
    }
  ]
}
```

---

### Exemple 4 : Dépendances Directes Uniquement

**Commande** :
```bash
mvn deploy-manifest-plugin:generate \
  -Ddescriptor.includeDependencyTree=true \
  -Ddescriptor.excludeTransitive=true
```

**Sortie JSON** (`descriptor.json`) :

```json
{
  "modules": [
    {
      "artifactId": "backend-service",
      
      "dependencies": {
        "summary": {
          "total": 8,
          "direct": 8,
          "transitive": 0,
          "scopes": {
            "compile": 6,
            "runtime": 2
          },
          "optional": 0
        },
        
        "flat": [
          {
            "groupId": "org.springframework.boot",
            "artifactId": "spring-boot-starter-web",
            "version": "3.2.0",
            "scope": "compile",
            "type": "jar",
            "optional": false,
            "depth": 1,
            "path": "org.springframework.boot:spring-boot-starter-web:jar:3.2.0"
          },
          {
            "groupId": "org.springframework.boot",
            "artifactId": "spring-boot-starter-data-jpa",
            "version": "3.2.0",
            "scope": "compile",
            "type": "jar",
            "optional": false,
            "depth": 1,
            "path": "org.springframework.boot:spring-boot-starter-data-jpa:jar:3.2.0"
          },
          {
            "groupId": "org.postgresql",
            "artifactId": "postgresql",
            "version": "42.7.1",
            "scope": "runtime",
            "type": "jar",
            "optional": false,
            "depth": 1,
            "path": "org.postgresql:postgresql:jar:42.7.1"
          }
        ]
      }
    }
  ]
}
```

---

### Exemple 5 : Profondeur Limitée

**Commande** :
```bash
mvn deploy-manifest-plugin:generate \
  -Ddescriptor.includeDependencyTree=true \
  -Ddescriptor.dependencyTreeDepth=1
```

**Sortie JSON** : Uniquement les dépendances directes et leur premier niveau de transitives (depth <= 2).

```json
{
  "modules": [
    {
      "dependencies": {
        "summary": {
          "total": 25,
          "direct": 8,
          "transitive": 17,
          "scopes": {
            "compile": 20,
            "runtime": 5
          }
        },
        
        "flat": [
          {
            "groupId": "org.springframework.boot",
            "artifactId": "spring-boot-starter-web",
            "version": "3.2.0",
            "depth": 1
          },
          {
            "groupId": "org.springframework",
            "artifactId": "spring-web",
            "version": "6.1.0",
            "depth": 2
          },
          {
            "groupId": "org.springframework",
            "artifactId": "spring-webmvc",
            "version": "6.1.0",
            "depth": 2
          }
        ]
      }
    }
  ]
}
```

---

### Exemple 6 : Format YAML

**Commande** :
```bash
mvn deploy-manifest-plugin:generate \
  -Ddescriptor.includeDependencyTree=true \
  -Ddescriptor.exportFormat=yaml
```

**Sortie YAML** (`descriptor.yaml`) :

```yaml
project:
  groupId: com.example
  artifactId: my-app
  version: 1.0.0

modules:
  - artifactId: backend-service
    packaging: jar
    
    dependencies:
      summary:
        total: 87
        direct: 12
        transitive: 75
        scopes:
          compile: 45
          runtime: 30
          provided: 12
        optional: 3
      
      flat:
        - groupId: org.springframework.boot
          artifactId: spring-boot-starter-web
          version: 3.2.0
          scope: compile
          type: jar
          optional: false
          depth: 1
          path: "org.springframework.boot:spring-boot-starter-web:jar:3.2.0"
        
        - groupId: org.springframework
          artifactId: spring-core
          version: 6.1.0
          scope: compile
          type: jar
          optional: false
          depth: 2
          path: "org.springframework.boot:spring-boot-starter-web:jar:3.2.0 -> org.springframework:spring-core:jar:6.1.0"
```

---

## 🎨 Design HTML Attendu

### Vue d'Ensemble

Le rapport HTML doit inclure une section **"Dependencies"** interactive avec les fonctionnalités suivantes :

1. ✅ **Dashboard de statistiques** : Nombre total, direct, transitive, par scope
2. ✅ **Barre de recherche** : Rechercher un artifact par nom/groupId
3. ✅ **Filtres interactifs** : Filtrer par scope, profondeur, optionnel
4. ✅ **Visualisation en arbre** : Arbre collapsible/expandable
5. ✅ **Tableau détaillé** : Liste triable avec toutes les infos
6. ✅ **Export CSV** : Bouton pour exporter les dépendances en CSV
7. ✅ **Détection de duplicata** : Highlighter les versions multiples d'un même artifact

---

### Wireframe / Mockup HTML

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Deployment Descriptor - my-app v1.0.0</title>
    <style>
        /* Layout principal */
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
            margin: 0;
            padding: 0;
            background: #f5f7fa;
            color: #2c3e50;
        }
        
        .container {
            max-width: 1400px;
            margin: 0 auto;
            padding: 2rem;
        }
        
        header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 2rem;
            margin-bottom: 2rem;
            border-radius: 12px;
            box-shadow: 0 4px 6px rgba(0,0,0,0.1);
        }
        
        h1 { margin: 0; font-size: 2rem; }
        h2 { 
            color: #2c3e50; 
            border-bottom: 3px solid #667eea; 
            padding-bottom: 0.5rem;
            margin-top: 2rem;
        }
        h3 { color: #34495e; margin-top: 1.5rem; }
        
        /* Section Dependencies */
        .dependencies-section {
            background: white;
            border-radius: 12px;
            padding: 2rem;
            margin-top: 2rem;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }
        
        /* Dashboard Statistiques */
        .stats-dashboard {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 1.5rem;
            margin: 2rem 0;
        }
        
        .stat-card {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 1.5rem;
            border-radius: 12px;
            text-align: center;
            box-shadow: 0 4px 6px rgba(0,0,0,0.1);
            transition: transform 0.2s;
        }
        
        .stat-card:hover {
            transform: translateY(-5px);
        }
        
        .stat-value {
            font-size: 3rem;
            font-weight: bold;
            margin: 0;
        }
        
        .stat-label {
            font-size: 0.875rem;
            opacity: 0.9;
            margin-top: 0.5rem;
            text-transform: uppercase;
            letter-spacing: 1px;
        }
        
        /* Breakdown des scopes */
        .scope-breakdown {
            background: #f8f9fa;
            padding: 1.5rem;
            border-radius: 8px;
            margin: 2rem 0;
        }
        
        .scope-bar-container {
            margin: 1rem 0;
        }
        
        .scope-bar {
            display: flex;
            align-items: center;
            margin: 0.75rem 0;
        }
        
        .scope-name {
            width: 120px;
            font-weight: 600;
            color: #2c3e50;
        }
        
        .bar-wrapper {
            flex: 1;
            background: #e9ecef;
            height: 24px;
            border-radius: 12px;
            overflow: hidden;
            margin: 0 1rem;
            position: relative;
        }
        
        .bar-fill {
            height: 100%;
            transition: width 0.5s ease;
            display: flex;
            align-items: center;
            justify-content: flex-end;
            padding-right: 8px;
            color: white;
            font-weight: bold;
            font-size: 0.75rem;
        }
        
        .bar-fill.compile { background: linear-gradient(90deg, #3498db, #2980b9); }
        .bar-fill.runtime { background: linear-gradient(90deg, #2ecc71, #27ae60); }
        .bar-fill.provided { background: linear-gradient(90deg, #f39c12, #e67e22); }
        .bar-fill.test { background: linear-gradient(90deg, #95a5a6, #7f8c8d); }
        
        .scope-count {
            width: 60px;
            text-align: right;
            font-weight: bold;
            color: #7f8c8d;
        }
        
        /* Barre de recherche et filtres */
        .search-filter-bar {
            background: #f8f9fa;
            padding: 1.5rem;
            border-radius: 8px;
            margin: 2rem 0;
            display: flex;
            gap: 1rem;
            flex-wrap: wrap;
            align-items: center;
        }
        
        .search-box {
            flex: 1;
            min-width: 300px;
            position: relative;
        }
        
        .search-input {
            width: 100%;
            padding: 0.75rem 2.5rem 0.75rem 1rem;
            border: 2px solid #dee2e6;
            border-radius: 8px;
            font-size: 1rem;
            transition: border-color 0.2s;
        }
        
        .search-input:focus {
            outline: none;
            border-color: #667eea;
        }
        
        .search-icon {
            position: absolute;
            right: 1rem;
            top: 50%;
            transform: translateY(-50%);
            color: #95a5a6;
        }
        
        .filter-group {
            display: flex;
            gap: 0.5rem;
            align-items: center;
        }
        
        .filter-label {
            font-weight: 600;
            color: #2c3e50;
            margin-right: 0.5rem;
        }
        
        .filter-btn {
            padding: 0.5rem 1rem;
            border: 2px solid #dee2e6;
            background: white;
            border-radius: 6px;
            cursor: pointer;
            transition: all 0.2s;
            font-size: 0.875rem;
        }
        
        .filter-btn:hover {
            border-color: #667eea;
            background: #f8f9fa;
        }
        
        .filter-btn.active {
            background: #667eea;
            color: white;
            border-color: #667eea;
        }
        
        .export-btn {
            padding: 0.75rem 1.5rem;
            background: #2ecc71;
            color: white;
            border: none;
            border-radius: 8px;
            cursor: pointer;
            font-weight: 600;
            transition: background 0.2s;
        }
        
        .export-btn:hover {
            background: #27ae60;
        }
        
        /* Visualisation en arbre */
        .tree-view {
            background: #f8f9fa;
            padding: 1.5rem;
            border-radius: 8px;
            margin: 2rem 0;
            font-family: 'Courier New', monospace;
        }
        
        .tree-node {
            margin: 0.25rem 0;
            padding: 0.5rem;
            border-radius: 4px;
            transition: background 0.2s;
        }
        
        .tree-node:hover {
            background: white;
        }
        
        .tree-node.depth-1 { 
            padding-left: 0rem; 
            font-weight: bold;
            color: #2c3e50;
        }
        
        .tree-node.depth-2 { 
            padding-left: 2rem; 
            color: #34495e;
        }
        
        .tree-node.depth-3 { 
            padding-left: 4rem; 
            color: #7f8c8d;
        }
        
        .tree-toggle {
            cursor: pointer;
            display: inline-block;
            width: 20px;
            text-align: center;
            user-select: none;
        }
        
        .tree-children {
            display: none;
        }
        
        .tree-node.expanded > .tree-children {
            display: block;
        }
        
        /* Tableau détaillé */
        .dependencies-table {
            width: 100%;
            border-collapse: collapse;
            margin: 2rem 0;
            background: white;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
            border-radius: 8px;
            overflow: hidden;
        }
        
        .dependencies-table thead {
            background: #2c3e50;
            color: white;
        }
        
        .dependencies-table th {
            padding: 1rem;
            text-align: left;
            font-weight: 600;
            cursor: pointer;
            user-select: none;
            position: relative;
        }
        
        .dependencies-table th:hover {
            background: #34495e;
        }
        
        .dependencies-table th::after {
            content: '⇅';
            position: absolute;
            right: 1rem;
            opacity: 0.5;
        }
        
        .dependencies-table th.sorted-asc::after {
            content: '↑';
            opacity: 1;
        }
        
        .dependencies-table th.sorted-desc::after {
            content: '↓';
            opacity: 1;
        }
        
        .dependencies-table td {
            padding: 0.875rem 1rem;
            border-bottom: 1px solid #ecf0f1;
        }
        
        .dependencies-table tr:hover {
            background: #f8f9fa;
        }
        
        .dependencies-table tr.depth-1 {
            font-weight: 600;
            background: #ffffff;
        }
        
        .dependencies-table tr.depth-2 {
            background: #f8f9fa;
        }
        
        .dependencies-table tr.depth-3 {
            background: #ecf0f1;
        }
        
        .dependencies-table tr.hidden {
            display: none;
        }
        
        /* Badges */
        .badge {
            display: inline-block;
            padding: 0.25rem 0.75rem;
            border-radius: 12px;
            font-size: 0.75rem;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }
        
        .badge.compile { background: #3498db; color: white; }
        .badge.runtime { background: #2ecc71; color: white; }
        .badge.provided { background: #f39c12; color: white; }
        .badge.test { background: #95a5a6; color: white; }
        .badge.optional { background: #e74c3c; color: white; }
        
        .depth-indicator {
            display: inline-block;
            width: 30px;
            height: 30px;
            background: #667eea;
            color: white;
            border-radius: 50%;
            text-align: center;
            line-height: 30px;
            font-weight: bold;
            font-size: 0.875rem;
        }
        
        /* Alerte duplicata */
        .duplicate-alert {
            background: #fff3cd;
            border-left: 4px solid #ffc107;
            padding: 1rem;
            margin: 1rem 0;
            border-radius: 4px;
        }
        
        .duplicate-artifact {
            background: #fff3cd !important;
        }
        
        /* Responsive */
        @media (max-width: 768px) {
            .stats-dashboard {
                grid-template-columns: 1fr;
            }
            
            .search-filter-bar {
                flex-direction: column;
                align-items: stretch;
            }
            
            .search-box {
                width: 100%;
            }
        }
    </style>
</head>
<body>
    <div class="container">
        <!-- Header -->
        <header>
            <h1>📦 Deployment Descriptor</h1>
            <p>Project: <strong>com.example:my-app:1.0.0</strong></p>
            <p>Build Time: 2025-11-12T10:30:00Z</p>
        </header>
        
        <!-- Section Dependencies -->
        <section class="dependencies-section">
            <h2>📊 Dependencies Analysis - backend-service</h2>
            
            <!-- Dashboard Statistiques -->
            <div class="stats-dashboard">
                <div class="stat-card">
                    <div class="stat-value">87</div>
                    <div class="stat-label">Total Dependencies</div>
                </div>
                <div class="stat-card">
                    <div class="stat-value">12</div>
                    <div class="stat-label">Direct</div>
                </div>
                <div class="stat-card">
                    <div class="stat-value">75</div>
                    <div class="stat-label">Transitive</div>
                </div>
                <div class="stat-card">
                    <div class="stat-value">3</div>
                    <div class="stat-label">Optional</div>
                </div>
            </div>
            
            <!-- Breakdown des scopes -->
            <div class="scope-breakdown">
                <h3>Breakdown by Scope</h3>
                <div class="scope-bar-container">
                    <div class="scope-bar">
                        <span class="scope-name">Compile</span>
                        <div class="bar-wrapper">
                            <div class="bar-fill compile" style="width: 52%;">52%</div>
                        </div>
                        <span class="scope-count">45</span>
                    </div>
                    <div class="scope-bar">
                        <span class="scope-name">Runtime</span>
                        <div class="bar-wrapper">
                            <div class="bar-fill runtime" style="width: 34%;">34%</div>
                        </div>
                        <span class="scope-count">30</span>
                    </div>
                    <div class="scope-bar">
                        <span class="scope-name">Provided</span>
                        <div class="bar-wrapper">
                            <div class="bar-fill provided" style="width: 14%;">14%</div>
                        </div>
                        <span class="scope-count">12</span>
                    </div>
                </div>
            </div>
            
            <!-- Barre de recherche et filtres -->
            <div class="search-filter-bar">
                <div class="search-box">
                    <input 
                        type="text" 
                        class="search-input" 
                        id="searchInput" 
                        placeholder="🔍 Search artifact (e.g., spring-boot, jackson, log4j)..."
                    >
                    <span class="search-icon">🔍</span>
                </div>
                
                <div class="filter-group">
                    <span class="filter-label">Scope:</span>
                    <button class="filter-btn active" data-scope="all">All</button>
                    <button class="filter-btn" data-scope="compile">Compile</button>
                    <button class="filter-btn" data-scope="runtime">Runtime</button>
                    <button class="filter-btn" data-scope="provided">Provided</button>
                    <button class="filter-btn" data-scope="test">Test</button>
                </div>
                
                <div class="filter-group">
                    <span class="filter-label">Depth:</span>
                    <button class="filter-btn active" data-depth="all">All</button>
                    <button class="filter-btn" data-depth="1">Direct (1)</button>
                    <button class="filter-btn" data-depth="2">Level 2</button>
                    <button class="filter-btn" data-depth="3">Level 3+</button>
                </div>
                
                <button class="export-btn" onclick="exportToCSV()">📥 Export CSV</button>
            </div>
            
            <!-- Alerte duplicata -->
            <div class="duplicate-alert" id="duplicateAlert" style="display: none;">
                <strong>⚠️ Version Conflicts Detected</strong><br>
                <span id="duplicateList"></span>
            </div>
            
            <!-- Tabs: Tree View / Table View -->
            <div style="margin: 2rem 0;">
                <button class="filter-btn active" onclick="showView('tree')">🌳 Tree View</button>
                <button class="filter-btn" onclick="showView('table')">📋 Table View</button>
            </div>
            
            <!-- Visualisation en arbre -->
            <div class="tree-view" id="treeView">
                <div class="tree-node depth-1 expanded" data-artifact="spring-boot-starter-web">
                    <span class="tree-toggle" onclick="toggleNode(this)">▼</span>
                    <strong>org.springframework.boot:spring-boot-starter-web</strong>:3.2.0
                    <span class="badge compile">compile</span>
                    <div class="tree-children">
                        <div class="tree-node depth-2 expanded">
                            <span class="tree-toggle" onclick="toggleNode(this)">▼</span>
                            org.springframework.boot:spring-boot-starter:3.2.0
                            <span class="badge compile">compile</span>
                            <div class="tree-children">
                                <div class="tree-node depth-3">
                                    <span class="tree-toggle"></span>
                                    org.springframework.boot:spring-boot:3.2.0
                                    <span class="badge compile">compile</span>
                                </div>
                                <div class="tree-node depth-3">
                                    <span class="tree-toggle"></span>
                                    org.springframework:spring-core:6.1.0
                                    <span class="badge compile">compile</span>
                                </div>
                            </div>
                        </div>
                        <div class="tree-node depth-2">
                            <span class="tree-toggle"></span>
                            org.springframework:spring-web:6.1.0
                            <span class="badge compile">compile</span>
                        </div>
                        <div class="tree-node depth-2">
                            <span class="tree-toggle"></span>
                            org.springframework:spring-webmvc:6.1.0
                            <span class="badge compile">compile</span>
                        </div>
                        <div class="tree-node depth-2">
                            <span class="tree-toggle"></span>
                            com.fasterxml.jackson.core:jackson-databind:2.15.3
                            <span class="badge compile">compile</span>
                        </div>
                    </div>
                </div>
                
                <div class="tree-node depth-1 expanded">
                    <span class="tree-toggle" onclick="toggleNode(this)">▼</span>
                    <strong>org.springframework.boot:spring-boot-starter-data-jpa</strong>:3.2.0
                    <span class="badge compile">compile</span>
                    <div class="tree-children">
                        <div class="tree-node depth-2">
                            <span class="tree-toggle"></span>
                            org.hibernate.orm:hibernate-core:6.3.1
                            <span class="badge compile">compile</span>
                        </div>
                    </div>
                </div>
                
                <div class="tree-node depth-1">
                    <span class="tree-toggle"></span>
                    <strong>org.postgresql:postgresql</strong>:42.7.1
                    <span class="badge runtime">runtime</span>
                </div>
            </div>
            
            <!-- Tableau détaillé -->
            <table class="dependencies-table" id="tableView" style="display: none;">
                <thead>
                    <tr>
                        <th onclick="sortTable(0)">Depth</th>
                        <th onclick="sortTable(1)">Artifact</th>
                        <th onclick="sortTable(2)">Version</th>
                        <th onclick="sortTable(3)">Scope</th>
                        <th onclick="sortTable(4)">Type</th>
                        <th>Path</th>
                    </tr>
                </thead>
                <tbody>
                    <tr class="depth-1" data-scope="compile" data-depth="1">
                        <td><span class="depth-indicator">1</span></td>
                        <td><strong>org.springframework.boot:spring-boot-starter-web</strong></td>
                        <td>3.2.0</td>
                        <td><span class="badge compile">compile</span></td>
                        <td>jar</td>
                        <td><code>org.springframework.boot:spring-boot-starter-web:jar:3.2.0</code></td>
                    </tr>
                    <tr class="depth-2" data-scope="compile" data-depth="2">
                        <td><span class="depth-indicator">2</span></td>
                        <td>org.springframework.boot:spring-boot-starter</td>
                        <td>3.2.0</td>
                        <td><span class="badge compile">compile</span></td>
                        <td>jar</td>
                        <td><code>...starter-web → spring-boot-starter</code></td>
                    </tr>
                    <tr class="depth-3" data-scope="compile" data-depth="3">
                        <td><span class="depth-indicator">3</span></td>
                        <td>org.springframework.boot:spring-boot</td>
                        <td>3.2.0</td>
                        <td><span class="badge compile">compile</span></td>
                        <td>jar</td>
                        <td><code>...starter-web → ...starter → spring-boot</code></td>
                    </tr>
                    <tr class="depth-3" data-scope="compile" data-depth="3">
                        <td><span class="depth-indicator">3</span></td>
                        <td>org.springframework:spring-core</td>
                        <td>6.1.0</td>
                        <td><span class="badge compile">compile</span></td>
                        <td>jar</td>
                        <td><code>...starter-web → ...starter → spring-core</code></td>
                    </tr>
                    <tr class="depth-2" data-scope="compile" data-depth="2">
                        <td><span class="depth-indicator">2</span></td>
                        <td>org.springframework:spring-web</td>
                        <td>6.1.0</td>
                        <td><span class="badge compile">compile</span></td>
                        <td>jar</td>
                        <td><code>...starter-web → spring-web</code></td>
                    </tr>
                    <tr class="depth-2" data-scope="compile" data-depth="2">
                        <td><span class="depth-indicator">2</span></td>
                        <td>org.springframework:spring-webmvc</td>
                        <td>6.1.0</td>
                        <td><span class="badge compile">compile</span></td>
                        <td>jar</td>
                        <td><code>...starter-web → spring-webmvc</code></td>
                    </tr>
                    <tr class="depth-2 duplicate-artifact" data-scope="compile" data-depth="2">
                        <td><span class="depth-indicator">2</span></td>
                        <td>com.fasterxml.jackson.core:jackson-databind</td>
                        <td>2.15.3</td>
                        <td><span class="badge compile">compile</span></td>
                        <td>jar</td>
                        <td><code>...starter-web → jackson-databind</code></td>
                    </tr>
                    <tr class="depth-1" data-scope="runtime" data-depth="1">
                        <td><span class="depth-indicator">1</span></td>
                        <td><strong>org.postgresql:postgresql</strong></td>
                        <td>42.7.1</td>
                        <td><span class="badge runtime">runtime</span></td>
                        <td>jar</td>
                        <td><code>org.postgresql:postgresql:jar:42.7.1</code></td>
                    </tr>
                    <tr class="depth-1" data-scope="provided" data-depth="1">
                        <td><span class="depth-indicator">1</span></td>
                        <td><strong>org.projectlombok:lombok</strong></td>
                        <td>1.18.30</td>
                        <td><span class="badge provided">provided</span> <span class="badge optional">optional</span></td>
                        <td>jar</td>
                        <td><code>org.projectlombok:lombok:jar:1.18.30</code></td>
                    </tr>
                </tbody>
            </table>
        </section>
    </div>
    
    <script>
        // Toggle tree nodes
        function toggleNode(element) {
            const node = element.parentElement;
            node.classList.toggle('expanded');
            element.textContent = node.classList.contains('expanded') ? '▼' : '▶';
        }
        
        // Switch between views
        function showView(view) {
            const treeView = document.getElementById('treeView');
            const tableView = document.getElementById('tableView');
            const buttons = document.querySelectorAll('[onclick^="showView"]');
            
            if (view === 'tree') {
                treeView.style.display = 'block';
                tableView.style.display = 'none';
                buttons[0].classList.add('active');
                buttons[1].classList.remove('active');
            } else {
                treeView.style.display = 'none';
                tableView.style.display = 'table';
                buttons[0].classList.remove('active');
                buttons[1].classList.add('active');
            }
        }
        
        // Search functionality
        document.getElementById('searchInput').addEventListener('input', function(e) {
            const searchTerm = e.target.value.toLowerCase();
            const rows = document.querySelectorAll('.dependencies-table tbody tr');
            
            rows.forEach(row => {
                const artifact = row.cells[1].textContent.toLowerCase();
                if (artifact.includes(searchTerm)) {
                    row.classList.remove('hidden');
                } else {
                    row.classList.add('hidden');
                }
            });
        });
        
        // Filter by scope
        document.querySelectorAll('[data-scope]').forEach(button => {
            button.addEventListener('click', function() {
                const scope = this.dataset.scope;
                
                // Toggle active class
                document.querySelectorAll('[data-scope]').forEach(btn => btn.classList.remove('active'));
                this.classList.add('active');
                
                // Filter rows
                const rows = document.querySelectorAll('.dependencies-table tbody tr');
                rows.forEach(row => {
                    if (scope === 'all' || row.dataset.scope === scope) {
                        row.classList.remove('hidden');
                    } else {
                        row.classList.add('hidden');
                    }
                });
            });
        });
        
        // Filter by depth
        document.querySelectorAll('[data-depth]').forEach(button => {
            button.addEventListener('click', function() {
                const depth = this.dataset.depth;
                
                // Toggle active class
                document.querySelectorAll('[data-depth]').forEach(btn => btn.classList.remove('active'));
                this.classList.add('active');
                
                // Filter rows
                const rows = document.querySelectorAll('.dependencies-table tbody tr');
                rows.forEach(row => {
                    if (depth === 'all') {
                        row.classList.remove('hidden');
                    } else if (depth === '3' && parseInt(row.dataset.depth) >= 3) {
                        row.classList.remove('hidden');
                    } else if (row.dataset.depth === depth) {
                        row.classList.remove('hidden');
                    } else {
                        row.classList.add('hidden');
                    }
                });
            });
        });
        
        // Sort table
        let sortDirection = {};
        function sortTable(columnIndex) {
            const table = document.getElementById('tableView');
            const tbody = table.querySelector('tbody');
            const rows = Array.from(tbody.querySelectorAll('tr'));
            
            const direction = sortDirection[columnIndex] === 'asc' ? 'desc' : 'asc';
            sortDirection[columnIndex] = direction;
            
            rows.sort((a, b) => {
                const aVal = a.cells[columnIndex].textContent.trim();
                const bVal = b.cells[columnIndex].textContent.trim();
                
                if (direction === 'asc') {
                    return aVal.localeCompare(bVal);
                } else {
                    return bVal.localeCompare(aVal);
                }
            });
            
            rows.forEach(row => tbody.appendChild(row));
            
            // Update header
            table.querySelectorAll('th').forEach(th => {
                th.classList.remove('sorted-asc', 'sorted-desc');
            });
            table.querySelectorAll('th')[columnIndex].classList.add(`sorted-${direction}`);
        }
        
        // Export to CSV
        function exportToCSV() {
            const table = document.getElementById('tableView');
            const rows = table.querySelectorAll('tr:not(.hidden)');
            let csv = [];
            
            rows.forEach(row => {
                const cols = Array.from(row.querySelectorAll('th, td')).map(col => {
                    return '"' + col.textContent.trim().replace(/"/g, '""') + '"';
                });
                csv.push(cols.join(','));
            });
            
            const csvContent = csv.join('\n');
            const blob = new Blob([csvContent], { type: 'text/csv' });
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = 'dependencies.csv';
            a.click();
        }
        
        // Detect duplicates on load
        window.addEventListener('load', function() {
            const artifacts = new Map();
            const rows = document.querySelectorAll('.dependencies-table tbody tr');
            
            rows.forEach(row => {
                const artifact = row.cells[1].textContent.split(':').slice(0, 2).join(':');
                const version = row.cells[2].textContent;
                
                if (!artifacts.has(artifact)) {
                    artifacts.set(artifact, []);
                }
                artifacts.get(artifact).push(version);
            });
            
            const duplicates = [];
            artifacts.forEach((versions, artifact) => {
                if (new Set(versions).size > 1) {
                    duplicates.push(`${artifact} (versions: ${[...new Set(versions)].join(', ')})`);
                }
            });
            
            if (duplicates.length > 0) {
                document.getElementById('duplicateAlert').style.display = 'block';
                document.getElementById('duplicateList').innerHTML = duplicates.join('<br>');
            }
        });
    </script>
</body>
</html>
```

---

## 🎨 Caractéristiques du Design HTML

### 1. Dashboard de Statistiques
- **4 cartes** avec gradient moderne (Total, Direct, Transitive, Optional)
- **Animation hover** : Lift effect au survol
- **Responsive** : S'adapte aux petits écrans

### 2. Breakdown des Scopes
- **Barres horizontales** avec pourcentages
- **Couleurs distinctes** par scope (compile=bleu, runtime=vert, provided=orange, test=gris)
- **Animation** : Les barres se remplissent au chargement

### 3. Barre de Recherche
- **Recherche instantanée** : Filtre les artifacts en temps réel
- **Icône** : Indicateur visuel de recherche
- **Auto-focus** : Facile à utiliser

### 4. Filtres Interactifs
- **Filtres par scope** : All, Compile, Runtime, Provided, Test
- **Filtres par profondeur** : All, Direct (1), Level 2, Level 3+
- **Boutons actifs** : Indication visuelle de l'état sélectionné

### 5. Vue en Arbre (Tree View)
- **Collapsible/Expandable** : Clic sur ▼/▶ pour ouvrir/fermer
- **Indentation visuelle** : Profondeur représentée par padding
- **Badges colorés** : Scope visible immédiatement
- **Hover effect** : Highlight de la ligne au survol

### 6. Vue Tableau (Table View)
- **Tri par colonnes** : Clic sur header pour trier
- **Indicateurs de tri** : Flèches ↑↓
- **Profondeur visuelle** : Badges ronds avec numéros
- **Paths complets** : Chaîne de dépendances visible
- **Alternance de couleurs** : Selon la profondeur

### 7. Détection de Duplicata
- **Alerte automatique** : Si plusieurs versions d'un même artifact
- **Highlight jaune** : Lignes concernées dans le tableau
- **Liste explicite** : Quels artifacts et quelles versions

### 8. Export CSV
- **Bouton vert** : Export visible
- **Respect des filtres** : Exporte seulement ce qui est affiché
- **Nom de fichier** : `dependencies.csv`

### 9. Responsive Design
- **Mobile-friendly** : S'adapte aux petits écrans
- **Grid flexible** : Les stats s'empilent sur mobile
- **Touch-friendly** : Boutons assez larges pour le tactile

---

## 💡 Use Cases et Exemples

### Use Case 1 : Audit de Sécurité

**Objectif** : Trouver toutes les dépendances Log4j pour vérifier les vulnérabilités

**CLI** :
```bash
mvn deploy-manifest-plugin:generate -Ddescriptor.includeDependencyTree=true
jq '.modules[].dependencies.flat[] | select(.artifactId | contains("log4j"))' descriptor.json
```

**HTML** : Utiliser la barre de recherche avec "log4j"

---

### Use Case 2 : Compliance Licences

**Objectif** : Lister toutes les dépendances uniques pour audit légal

**CLI** :
```bash
jq '.modules[].dependencies.flat[] | "\(.groupId):\(.artifactId):\(.version)"' descriptor.json | sort -u
```

**HTML** : Exporter le CSV et traiter avec Excel

---

### Use Case 3 : Debugging Classpath

**Objectif** : Comprendre pourquoi jackson-databind 2.15.3 est présent (alors que je veux 2.16.0)

**CLI** :
```bash
jq '.modules[].dependencies.flat[] | select(.artifactId == "jackson-databind") | .path' descriptor.json
```

**Résultat** :
```
"org.springframework.boot:spring-boot-starter-web:jar:3.2.0 -> com.fasterxml.jackson.core:jackson-databind:jar:2.15.3"
```

**HTML** : Rechercher "jackson-databind" et voir le chemin complet dans le tableau

---

### Use Case 4 : Évolution entre Versions

**Objectif** : Comparer les dépendances entre v1.0.0 et v1.1.0

**CLI** :
```bash
diff <(jq -r '.modules[0].dependencies.flat[].path' descriptor-v1.0.0.json | sort) \
     <(jq -r '.modules[0].dependencies.flat[].path' descriptor-v1.1.0.json | sort)
```

---

### Use Case 5 : Dépendances Directes Seulement

**Objectif** : Voir uniquement ce que j'ai déclaré dans mon POM

**CLI** :
```bash
mvn deploy-manifest-plugin:generate \
  -Ddescriptor.includeDependencyTree=true \
  -Ddescriptor.excludeTransitive=true
```

**HTML** : Filtrer par "Direct (1)" dans les filtres de profondeur

---

## 📝 Résumé des Attentes

### JSON/YAML
- ✅ Structure `dependencies` avec `summary`, `flat`, et `tree`
- ✅ Format flat : liste avec `depth` et `path`
- ✅ Format tree : hiérarchie avec `children`
- ✅ Statistiques : total, direct, transitive, par scope

### HTML
- ✅ Dashboard visuel moderne avec cartes de statistiques
- ✅ Recherche instantanée par artifact
- ✅ Filtres interactifs (scope, profondeur)
- ✅ Deux vues : Tree (collapsible) et Table (sortable)
- ✅ Export CSV
- ✅ Détection automatique de duplicata
- ✅ Design responsive et professionnel

### Comportement
- ✅ Feature désactivée par défaut
- ✅ Configurable via CLI et POM
- ✅ Support multi-module
- ✅ Performance acceptable (pas de ralentissement significatif)

---

**Date** : Novembre 2025  
**Version** : 1.0
