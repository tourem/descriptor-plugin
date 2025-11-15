# Implementation Prompt: Dependency Analysis Intelligence Layer

## 🎯 Objective

Implement an **intelligence layer** on top of Maven Dependency Plugin that transforms raw detection into actionable insights with context, recommendations, and visualization.

**Key Principle**: Don't replace Maven Dependency Plugin, **enrich it**.

---

## 📋 Implementation Roadmap

### Phase 1: Foundation (Week 1)
- Run Maven Dependency Plugin programmatically
- Parse and structure results
- Generate JSON output

### Phase 2: Intelligence (Week 2)
- Add Git context (blame, commits)
- Detect false positives (lombok, annotation processors)
- Generate recommendations with POM patches

### Phase 3: Visualization (Week 3)
- Create HTML dashboard
- Add health score calculation
- Multi-module aggregation

---

## 🔧 Phase 1: Foundation

### Step 1.1: Execute Maven Dependency Plugin

**Goal**: Run `mvn dependency:analyze` programmatically and capture output.

**Expected Capabilities**:
- Execute `dependency:analyze` goal
- Capture console output (unused, undeclared)
- Parse text output into structured data

**Expected Output Structure**:
```json
{
  "dependencyAnalysis": {
    "analyzer": "deploy-manifest-plugin",
    "baseAnalyzer": "maven-dependency-plugin",
    "timestamp": "2025-11-14T10:30:00Z",
    
    "rawResults": {
      "unused": [
        {
          "groupId": "org.apache.commons",
          "artifactId": "commons-lang3",
          "version": "3.12.0",
          "scope": "compile"
        },
        {
          "groupId": "org.projectlombok",
          "artifactId": "lombok",
          "version": "1.18.30",
          "scope": "provided"
        }
      ],
      "undeclared": [
        {
          "groupId": "org.springframework",
          "artifactId": "spring-web",
          "version": "6.1.0",
          "scope": "compile"
        }
      ]
    }
  }
}
```

---

### Step 1.2: Add Basic Metadata

**Goal**: Enrich each dependency with size, scope, and classification.

**Expected Output**:
```json
{
  "unused": [
    {
      "groupId": "org.apache.commons",
      "artifactId": "commons-lang3",
      "version": "3.12.0",
      "scope": "compile",
      
      "metadata": {
        "sizeBytes": 581632,
        "sizeKB": 568,
        "sizeMB": 0.55,
        "fileLocation": "/home/user/.m2/repository/org/apache/commons/commons-lang3/3.12.0/commons-lang3-3.12.0.jar",
        "sha256": "a3f5b2c8d9e1f4a7b6c5d8e9f1a2b3c4...",
        "packaging": "jar"
      }
    }
  ]
}
```

---

### Step 1.3: Calculate Summary Statistics

**Goal**: Provide high-level metrics.

**Expected Output**:
```json
{
  "dependencyAnalysis": {
    "summary": {
      "totalDependencies": 87,
      "directDependencies": 12,
      "transitiveDependencies": 75,
      
      "issues": {
        "unused": 5,
        "undeclared": 2,
        "totalIssues": 7
      },
      
      "potentialSavings": {
        "bytes": 3506176,
        "kb": 3424,
        "mb": 3.34,
        "percentage": 7.5
      }
    }
  }
}
```

---

## 🧠 Phase 2: Intelligence Layer

### Step 2.1: Add Git Context

**Goal**: For each unused dependency, find WHO added it, WHEN, and WHY.

**Implementation Notes**:
- Use `git blame` on `pom.xml`
- Find the line where dependency was added
- Extract commit info (author, date, message)
- Get commit SHA for traceability

**Expected Output**:
```json
{
  "unused": [
    {
      "groupId": "org.apache.commons",
      "artifactId": "commons-lang3",
      "version": "3.12.0",
      
      "gitContext": {
        "declaredIn": {
          "file": "pom.xml",
          "line": 45,
          "section": "<dependencies>"
        },
        "addedBy": {
          "commit": "abc123def456",
          "commitShort": "abc123",
          "author": "John Doe",
          "email": "john.doe@company.com",
          "date": "2024-03-15T14:23:00Z",
          "message": "Added commons-lang3 for string utility methods",
          "daysAgo": 244
        },
        "lastModified": {
          "commit": "abc123def456",
          "date": "2024-03-15T14:23:00Z",
          "neverChanged": true
        }
      }
    }
  ]
}
```

---

### Step 2.2: False Positive Detection

**Goal**: Identify and flag known false positives (lombok, annotation processors, runtime agents).

**False Positive Rules**:
1. **Annotation Processors**: lombok, mapstruct, immutables
2. **Runtime Agents**: aspectjweaver, byte-buddy-agent
3. **Dev Tools**: spring-boot-devtools
4. **Provided Scope**: Often appear unused but needed at compile time
5. **Reflection Usage**: Dependencies loaded via Class.forName()

**Expected Output**:
```json
{
  "unused": [
    {
      "groupId": "org.projectlombok",
      "artifactId": "lombok",
      "version": "1.18.30",
      "scope": "provided",
      
      "mavenDependencyAnalysis": {
        "status": "UNUSED",
        "confidence": "HIGH"
      },
      
      "intelligentAnalysis": {
        "isFalsePositive": true,
        "confidence": "LOW",
        "adjustedConfidence": -50,
        
        "falsePositiveReason": {
          "category": "ANNOTATION_PROCESSOR",
          "description": "Lombok is a compile-time annotation processor that generates code at build time",
          "pattern": ".*lombok.*",
          "scope": "provided"
        },
        
        "recommendation": {
          "action": "KEEP",
          "severity": "INFO",
          "reasoning": "Required at compile time for annotation processing"
        }
      }
    },
    
    {
      "groupId": "org.apache.commons",
      "artifactId": "commons-lang3",
      "version": "3.12.0",
      "scope": "compile",
      
      "mavenDependencyAnalysis": {
        "status": "UNUSED",
        "confidence": "HIGH"
      },
      
      "intelligentAnalysis": {
        "isFalsePositive": false,
        "confidence": "HIGH",
        
        "recommendation": {
          "action": "REMOVE",
          "severity": "MEDIUM",
          "reasoning": "No classes from this artifact are used in compiled bytecode"
        }
      }
    }
  ]
}
```

---

### Step 2.3: Generate Actionable Recommendations

**Goal**: Provide POM patches, commands, and rollback plans.

**Expected Output**:
```json
{
  "unused": [
    {
      "groupId": "org.apache.commons",
      "artifactId": "commons-lang3",
      "version": "3.12.0",
      
      "recommendation": {
        "action": "REMOVE",
        "confidence": "HIGH",
        "risk": "LOW",
        
        "impact": {
          "sizeSavings": {
            "kb": 568,
            "percentage": 1.2
          },
          "buildTimeSavings": {
            "seconds": 2,
            "percentage": 0.8
          },
          "securityImpact": {
            "cveRemoved": 0,
            "licenseImpact": "None"
          }
        },
        
        "automatedFix": {
          "type": "POM_REMOVAL",
          
          "pomPatch": {
            "action": "REMOVE",
            "location": "pom.xml:45-49",
            "diff": "--- pom.xml\n+++ pom.xml\n@@ -45,5 +45,0 @@\n-    <dependency>\n-      <groupId>org.apache.commons</groupId>\n-      <artifactId>commons-lang3</artifactId>\n-      <version>3.12.0</version>\n-    </dependency>"
          },
          
          "xmlToRemove": "<dependency>\n  <groupId>org.apache.commons</groupId>\n  <artifactId>commons-lang3</artifactId>\n  <version>3.12.0</version>\n</dependency>",
          
          "verification": {
            "testCommand": "mvn clean test",
            "expectedResult": "All tests should pass"
          },
          
          "rollbackPlan": {
            "method": "git revert",
            "command": "git revert HEAD",
            "backupLocation": "pom.xml.backup"
          }
        }
      }
    }
  ]
}
```

---

### Step 2.4: Detect Version Conflicts

**Goal**: Identify when the same artifact appears in multiple versions and provide alignment recommendations.

**Expected Output**:
```json
{
  "dependencyAnalysis": {
    "versionConflicts": [
      {
        "artifactId": "jackson-databind",
        "groupId": "com.fasterxml.jackson.core",
        
        "detectedVersions": [
          {
            "version": "2.15.3",
            "source": "spring-boot-starter-web → spring-boot-starter-json",
            "depth": 2,
            "scope": "compile",
            "selected": true,
            "selectionReason": "NEAREST_WINS"
          },
          {
            "version": "2.14.2",
            "source": "legacy-api-client → jackson-mapper",
            "depth": 3,
            "scope": "compile",
            "selected": false,
            "selectionReason": "FARTHER"
          },
          {
            "version": "2.15.0",
            "source": "custom-json-lib",
            "depth": 3,
            "scope": "compile",
            "selected": false,
            "selectionReason": "FARTHER"
          }
        ],
        
        "analysis": {
          "risk": "MEDIUM",
          "reasoning": "Multiple minor versions (2.14.x vs 2.15.x) may cause runtime compatibility issues",
          "hasBreakingChanges": false,
          "majorVersionConflict": false,
          "minorVersionConflict": true,
          "patchVersionConflict": true
        },
        
        "recommendation": {
          "action": "ALIGN_TO_VERSION",
          "targetVersion": "2.15.3",
          "method": "DEPENDENCY_MANAGEMENT",
          
          "pomPatch": {
            "section": "dependencyManagement",
            "xml": "<dependencyManagement>\n  <dependencies>\n    <dependency>\n      <groupId>com.fasterxml.jackson.core</groupId>\n      <artifactId>jackson-databind</artifactId>\n      <version>2.15.3</version>\n    </dependency>\n  </dependencies>\n</dependencyManagement>",
            "location": "Add to parent POM or root pom.xml"
          },
          
          "expectedOutcome": "All modules will use jackson-databind:2.15.3",
          "affectedModules": ["backend-service", "api-gateway", "worker-service"]
        }
      }
    ]
  }
}
```

---

### Step 2.5: Multi-Module Aggregation

**Goal**: For multi-module projects, provide a cross-module view with common issues.

**Expected Output**:
```json
{
  "dependencyAnalysis": {
    "multiModule": {
      "isMultiModule": true,
      "totalModules": 3,
      
      "perModuleSummary": [
        {
          "moduleName": "backend-service",
          "path": "backend-service/pom.xml",
          "unused": 5,
          "undeclared": 2,
          "versionConflicts": 3,
          "potentialSavingsKB": 2345
        },
        {
          "moduleName": "common-utils",
          "path": "common-utils/pom.xml",
          "unused": 2,
          "undeclared": 0,
          "versionConflicts": 1,
          "potentialSavingsKB": 678
        },
        {
          "moduleName": "api-gateway",
          "path": "api-gateway/pom.xml",
          "unused": 3,
          "undeclared": 1,
          "versionConflicts": 2,
          "potentialSavingsKB": 1234
        }
      ],
      
      "aggregateIssues": {
        "totalUnused": 10,
        "totalUndeclared": 3,
        "totalConflicts": 6,
        "totalPotentialSavingsKB": 4257
      },
      
      "commonUnused": [
        {
          "artifact": "commons-lang3:3.12.0",
          "unusedInModules": [
            "backend-service",
            "common-utils",
            "api-gateway"
          ],
          "count": 3,
          
          "recommendation": {
            "action": "REMOVE_FROM_PARENT",
            "target": "Parent POM dependencyManagement",
            "impact": "All 3 modules will be cleaned",
            "savingsKB": 1704
          }
        }
      ],
      
      "commonConflicts": [
        {
          "artifact": "jackson-databind",
          "conflictInModules": [
            "backend-service",
            "api-gateway"
          ],
          "versions": ["2.15.3", "2.14.2"],
          
          "recommendation": {
            "action": "ADD_TO_PARENT_DEPENDENCY_MANAGEMENT",
            "targetVersion": "2.15.3",
            "xml": "<dependency>...</dependency>"
          }
        }
      ],
      
      "crossModuleDuplicates": [
        {
          "artifact": "guava:32.1.0",
          "appearsInModules": [
            "backend-service",
            "common-utils"
          ],
          "recommendation": "Move to parent POM to avoid duplication"
        }
      ]
    }
  }
}
```

---

## 📊 Phase 3: Visualization & Scoring

### Step 3.1: Calculate Health Score

**Goal**: Single metric (0-100) representing dependency health.

**Scoring Formula**:
- **Base**: 100 points
- **Unused dependencies**: -2 points each
- **Version conflicts**: -3 points each (MEDIUM risk), -5 points (HIGH risk)
- **Undeclared usage**: -2 points each
- **False positives**: No penalty
- **Bonus**: +5 points if no issues

**Expected Output**:
```json
{
  "dependencyHealthScore": {
    "overall": 72,
    "grade": "C+",
    "previousScore": 68,
    "change": "+4",
    "trend": "IMPROVING",
    
    "breakdown": {
      "cleanliness": {
        "score": 65,
        "outOf": 100,
        "factors": [
          {
            "factor": "5 unused dependencies",
            "impact": -10,
            "details": "2 points per unused (excluding false positives)"
          },
          {
            "factor": "3 version conflicts (MEDIUM risk)",
            "impact": -9,
            "details": "3 points per conflict"
          },
          {
            "factor": "2 undeclared uses",
            "impact": -4,
            "details": "2 points per undeclared"
          }
        ]
      },
      
      "security": {
        "score": 85,
        "outOf": 100,
        "factors": [
          {
            "factor": "0 critical CVEs",
            "impact": +20
          },
          {
            "factor": "2 high CVEs",
            "impact": -10
          },
          {
            "factor": "All dependencies < 2 years old",
            "impact": +10
          }
        ]
      },
      
      "maintainability": {
        "score": 70,
        "outOf": 100,
        "factors": [
          {
            "factor": "12 direct dependencies (good)",
            "impact": +10
          },
          {
            "factor": "75 transitive dependencies (average)",
            "impact": 0
          },
          {
            "factor": "No deprecated dependencies",
            "impact": +5
          }
        ]
      },
      
      "licenses": {
        "score": 80,
        "outOf": 100,
        "factors": [
          {
            "factor": "No GPL/AGPL licenses",
            "impact": +15
          },
          {
            "factor": "5 unknown licenses",
            "impact": -5
          }
        ]
      }
    },
    
    "benchmarks": {
      "averageSpringBootProject": 68,
      "topQuartile": 85,
      "bottomQuartile": 45,
      "yourRanking": "Above Average"
    },
    
    "actionableImprovements": [
      {
        "action": "Remove 5 unused dependencies",
        "scoreImpact": +8,
        "effort": "LOW",
        "priority": 1
      },
      {
        "action": "Fix 3 version conflicts",
        "scoreImpact": +6,
        "effort": "MEDIUM",
        "priority": 2
      },
      {
        "action": "Declare 2 undeclared dependencies",
        "scoreImpact": +4,
        "effort": "LOW",
        "priority": 3
      }
    ],
    
    "historicalTrend": [
      {
        "version": "1.0.0",
        "date": "2024-09-01",
        "score": 65
      },
      {
        "version": "1.1.0",
        "date": "2024-10-01",
        "score": 68
      },
      {
        "version": "1.2.0",
        "date": "2024-11-01",
        "score": 72,
        "current": true
      }
    ]
  }
}
```

---

### Step 3.2: Generate HTML Dashboard

**Goal**: Beautiful, interactive HTML report with actionable buttons.

**Expected HTML Structure**:

```html
<!DOCTYPE html>
<html>
<head>
    <title>Dependency Analysis Report</title>
</head>
<body>
    <!-- HEADER: Health Score Widget -->
    <div class="health-score-widget">
        <h1>Dependency Health Score</h1>
        <div class="score-display">
            <span class="score">72</span>
            <span class="grade">C+</span>
        </div>
        <div class="progress-bar">
            <div class="progress" style="width: 72%"></div>
        </div>
        <div class="trend">
            📈 +4 points since v1.1.0 (IMPROVING)
        </div>
    </div>
    
    <!-- SUMMARY CARDS -->
    <div class="summary-grid">
        <div class="card">
            <h3>Total Dependencies</h3>
            <span class="value">87</span>
        </div>
        <div class="card warning">
            <h3>Unused</h3>
            <span class="value">5</span>
            <span class="savings">Save 3.3 MB</span>
        </div>
        <div class="card warning">
            <h3>Version Conflicts</h3>
            <span class="value">3</span>
        </div>
        <div class="card info">
            <h3>Undeclared</h3>
            <span class="value">2</span>
        </div>
    </div>
    
    <!-- UNUSED DEPENDENCIES TABLE -->
    <div class="section">
        <h2>🗑️ Unused Dependencies (5)</h2>
        <table>
            <thead>
                <tr>
                    <th>Artifact</th>
                    <th>Size</th>
                    <th>Confidence</th>
                    <th>Added By</th>
                    <th>Actions</th>
                </tr>
            </thead>
            <tbody>
                <!-- Real unused dependency -->
                <tr class="unused-item">
                    <td>
                        <strong>commons-lang3:3.12.0</strong>
                        <br>
                        <small>org.apache.commons</small>
                    </td>
                    <td>568 KB</td>
                    <td><span class="badge confidence-high">HIGH</span></td>
                    <td>
                        <small>john.doe@company.com</small>
                        <br>
                        <small>2024-03-15 (244 days ago)</small>
                    </td>
                    <td>
                        <button class="btn btn-primary" onclick="showPOMPatch('commons-lang3')">
                            🔧 Show Patch
                        </button>
                        <button class="btn btn-secondary" onclick="createIssue('commons-lang3')">
                            📝 GitHub Issue
                        </button>
                    </td>
                </tr>
                
                <!-- False positive -->
                <tr class="false-positive-item">
                    <td>
                        <strong>lombok:1.18.30</strong>
                        <br>
                        <small>org.projectlombok</small>
                        <br>
                        <span class="badge warning">FALSE POSITIVE</span>
                    </td>
                    <td>1.8 MB</td>
                    <td><span class="badge confidence-low">LOW</span></td>
                    <td>
                        <small>Annotation processor</small>
                    </td>
                    <td>
                        <button class="btn btn-success" disabled>
                            ✅ Keep (Required)
                        </button>
                        <button class="btn btn-info" onclick="showWhy('lombok')">
                            ℹ️ Why?
                        </button>
                    </td>
                </tr>
            </tbody>
        </table>
    </div>
    
    <!-- VERSION CONFLICTS TABLE -->
    <div class="section">
        <h2>⚠️ Version Conflicts (3)</h2>
        <div class="conflict-item">
            <h3>jackson-databind</h3>
            <div class="versions">
                <div class="version selected">
                    ✅ 2.15.3 (selected)
                    <small>← spring-boot-starter-web</small>
                </div>
                <div class="version ignored">
                    ❌ 2.14.2 (ignored)
                    <small>← legacy-api-client</small>
                </div>
                <div class="version ignored">
                    ❌ 2.15.0 (ignored)
                    <small>← custom-json-lib</small>
                </div>
            </div>
            <div class="risk-badge medium">MEDIUM RISK</div>
            <p>Multiple minor versions may cause runtime issues</p>
            <button class="btn btn-primary" onclick="alignVersion('jackson-databind')">
                🔧 Auto-fix with dependencyManagement
            </button>
            <button class="btn btn-secondary" onclick="showPatch('jackson-databind')">
                📋 Show POM Patch
            </button>
        </div>
    </div>
    
    <!-- ACTIONABLE IMPROVEMENTS -->
    <div class="section">
        <h2>🎯 Quick Wins</h2>
        <div class="improvement-list">
            <div class="improvement-item priority-1">
                <div class="improvement-header">
                    <h4>Remove 5 unused dependencies</h4>
                    <span class="score-impact">+8 points</span>
                </div>
                <p>Save 3.3 MB and reduce complexity</p>
                <div class="effort-badge low">LOW EFFORT</div>
                <button class="btn btn-primary">Start Cleanup</button>
            </div>
            
            <div class="improvement-item priority-2">
                <div class="improvement-header">
                    <h4>Fix 3 version conflicts</h4>
                    <span class="score-impact">+6 points</span>
                </div>
                <p>Align jackson-databind and others to single versions</p>
                <div class="effort-badge medium">MEDIUM EFFORT</div>
                <button class="btn btn-primary">Fix Conflicts</button>
            </div>
        </div>
    </div>
    
    <!-- MODAL: POM PATCH -->
    <div id="pom-patch-modal" class="modal">
        <div class="modal-content">
            <h3>POM Patch: commons-lang3</h3>
            <pre class="code-diff">
--- pom.xml
+++ pom.xml
@@ -45,5 +45,0 @@
-    &lt;dependency&gt;
-      &lt;groupId&gt;org.apache.commons&lt;/groupId&gt;
-      &lt;artifactId&gt;commons-lang3&lt;/artifactId&gt;
-      &lt;version&gt;3.12.0&lt;/version&gt;
-    &lt;/dependency&gt;
            </pre>
            <h4>Verification Steps:</h4>
            <ol>
                <li>Remove the dependency from pom.xml</li>
                <li>Run: <code>mvn clean test</code></li>
                <li>If tests pass: <code>mvn clean install</code></li>
            </ol>
            <h4>Rollback:</h4>
            <p><code>git revert HEAD</code></p>
            <button class="btn btn-primary">Copy Patch</button>
            <button class="btn btn-secondary" onclick="closeModal()">Close</button>
        </div>
    </div>
</body>
</html>
```

**Visual Design Requirements**:
- ✅ Health score with colored progress bar (🟢 >80, 🟡 60-80, 🔴 <60)
- ✅ Summary cards with icons and colors
- ✅ Tables with sortable columns
- ✅ Badges for confidence levels, risks, false positives
- ✅ Interactive buttons (Show Patch, Create Issue, etc.)
- ✅ Modals for detailed views
- ✅ Responsive design (mobile-friendly)
- ✅ Dark/light theme toggle

---

## 📋 Configuration Parameters

**Maven Plugin Configuration**:

```xml
<configuration>
    <!-- Enable dependency analysis -->
    <analyzeDependencies>true</analyzeDependencies>
    
    <!-- Analysis options -->
    <detectUnused>true</detectUnused>
    <detectConflicts>true</detectConflicts>
    <detectUndeclared>true</detectUndeclared>
    
    <!-- Intelligence features -->
    <handleFalsePositives>true</handleFalsePositives>
    <addGitContext>true</addGitContext>
    <generateRecommendations>true</generateRecommendations>
    
    <!-- Scoring -->
    <calculateHealthScore>true</calculateHealthScore>
    <includeHistoricalTrend>true</includeHistoricalTrend>
    
    <!-- Multi-module -->
    <aggregateModules>true</aggregateModules>
    
    <!-- Output formats -->
    <generateHtml>true</generateHtml>
    <exportFormat>both</exportFormat>
    
    <!-- False positive handling -->
    <falsePositiveRules>
        <rule>.*lombok.*</rule>
        <rule>.*aspectjweaver.*</rule>
        <rule>.*spring-boot-devtools.*</rule>
    </falsePositiveRules>
    
    <!-- User overrides file -->
    <userOverridesFile>dependency-overrides.json</userOverridesFile>
</configuration>
```

**CLI Usage**:
```bash
# Full analysis
mvn deploy-manifest:generate -Ddescriptor.analyzeDependencies=true

# Analysis only (no manifest)
mvn deploy-manifest:analyze-dependencies -Ddescriptor.generateHtml=true

# With custom false positive rules
mvn deploy-manifest:generate \
  -Ddescriptor.analyzeDependencies=true \
  -Ddescriptor.falsePositiveRules=lombok,aspectj
```

---

## ✅ Success Criteria

### Phase 1 Success:
- ✅ Can execute Maven Dependency Plugin programmatically
- ✅ JSON output contains all unused/undeclared dependencies
- ✅ Size metadata added for all dependencies
- ✅ Summary statistics calculated correctly

### Phase 2 Success:
- ✅ Git context added (author, commit, date) for each dependency
- ✅ False positives detected (lombok, aspectj, etc.)
- ✅ Recommendations generated with POM patches
- ✅ Version conflicts identified with alignment suggestions
- ✅ Multi-module aggregation works (common issues identified)

### Phase 3 Success:
- ✅ Health score calculated (0-100 scale)
- ✅ HTML dashboard generated with all sections
- ✅ Interactive buttons work (Show Patch, Create Issue)
- ✅ Modals display correctly
- ✅ Dashboard is responsive (mobile-friendly)

---

## 🎯 Key Differentiators from Maven Dependency Plugin

| Feature | Maven Dependency | Your Plugin |
|---------|-----------------|-------------|
| Detection | ✅ Text output | ✅ JSON + YAML + HTML |
| Context | ❌ | ✅ Git blame (who, when, why) |
| False Positives | ❌ Many | ✅ Intelligent filtering |
| Recommendations | ❌ | ✅ POM patches + rollback |
| Conflicts | ❌ | ✅ Version alignment suggestions |
| Multi-Module | ❌ | ✅ Aggregate view |
| Health Score | ❌ | ✅ 0-100 metric |
| Interactive | ❌ | ✅ HTML dashboard with actions |
| Trend | ❌ | ✅ Historical comparison |

---

## 📝 Example User Workflow

**Scenario**: Developer wants to clean up dependencies before release.

**Steps**:
1. Run: `mvn deploy-manifest:generate -Ddescriptor.analyzeDependencies=true`
2. Open `descriptor.html` in browser
3. See health score: **72/100** (C+)
4. Review "Quick Wins" section:
   - Remove 5 unused → +8 points
   - Fix 3 conflicts → +6 points
5. Click "Show Patch" for `commons-lang3`
6. Copy patch and apply to `pom.xml`
7. Run `mvn clean test` to verify
8. Commit: `git commit -m "Remove unused commons-lang3"`
9. Re-run plugin to see new score: **80/100** (B-)
10. Repeat for other issues

**Time saved**: 2 hours → 15 minutes 🚀

---

**Implementation Notes**:
- Start with Phase 1 (foundation) to get quick value
- Phase 2 (intelligence) is the core differentiator
- Phase 3 (visualization) makes it user-friendly
- All phases should be independently testable
- Consider performance (cache Git blame results)
- Handle edge cases (no Git repo, empty project)
