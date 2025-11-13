# Contributing to Maven Deploy Manifest Plugin

Thank you for your interest in contributing to the Maven Deploy Manifest Plugin! We welcome contributions from the community.

## 🚀 Getting Started

### Prerequisites

- Java 21 or higher
- Maven 3.9.0 or higher
- Git

### Setting Up Your Development Environment

1. **Fork the repository** on GitHub
2. **Clone your fork** locally:
   ```bash
   git clone https://github.com/YOUR_USERNAME/deploy-manifest-plugin.git
   cd deploy-manifest-plugin
   ```
3. **Add upstream remote**:
   ```bash
   git remote add upstream https://github.com/tourem/deploy-manifest-plugin.git
   ```
4. **Build the project**:
   ```bash
   mvn clean install
   ```

## 🔧 Development Workflow

### Creating a Feature Branch

```bash
git checkout main
git pull upstream main
git checkout -b feature/your-feature-name
```

### Making Changes

1. **Write clean, readable code** following existing patterns
2. **Add tests** for new functionality
3. **Update documentation** if needed
4. **Run tests** before committing:
   ```bash
   mvn clean test
   ```

### Commit Guidelines

We follow [Conventional Commits](https://www.conventionalcommits.org/):

- `feat:` - New feature
- `fix:` - Bug fix
- `docs:` - Documentation changes
- `test:` - Adding or updating tests
- `refactor:` - Code refactoring
- `chore:` - Maintenance tasks

**Example:**
```bash
git commit -m "feat: Add support for Gradle projects"
git commit -m "fix: Resolve NPE in ExecutableDetector"
```

### Running Tests

```bash
# Run all tests
mvn clean test

# Run specific test class
mvn test -Dtest=EnhancedExecutableDetectorTest

# Run with coverage
mvn clean verify
```

### Building the Project

```bash
# Build without tests
mvn clean install -DskipTests

# Full build with tests
mvn clean install
```

## 📝 Pull Request Process

1. **Update your branch** with latest upstream changes:
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

2. **Push your changes**:
   ```bash
   git push origin feature/your-feature-name
   ```

3. **Create a Pull Request** on GitHub with:
   - Clear title describing the change
   - Description of what changed and why
   - Reference to related issues (if any)
   - Screenshots (if UI changes)

4. **Wait for review** - maintainers will review your PR and may request changes

5. **Address feedback** - make requested changes and push updates

6. **Merge** - once approved, a maintainer will merge your PR

## 🧪 Testing Guidelines

- **Write unit tests** for all new functionality
- **Maintain test coverage** - aim for >80%
- **Test edge cases** and error conditions
- **Use descriptive test names** that explain what is being tested

**Example:**
```java
@Test
void shouldDetectSpringBootWithoutPlugin() {
    // Given
    Model model = createModelWithSpringBootDependencies();
    
    // When
    ExecutableInfo info = detector.detectExecutable(model, modulePath);
    
    // Then
    assertTrue(info.isSpringBootApplication());
}
```

## 📚 Documentation

- Update `README.md` for user-facing changes
- Add JavaDoc for public APIs
- Create/update technical documentation in `/docs` if needed
- Update `CHANGELOG.md` with your changes

## 🐛 Reporting Bugs

1. **Check existing issues** to avoid duplicates
2. **Create a new issue** with:
   - Clear, descriptive title
   - Steps to reproduce
   - Expected vs actual behavior
   - Maven version, Java version, OS
   - Sample `pom.xml` if relevant

## 💡 Suggesting Features

1. **Open an issue** with the `enhancement` label
2. **Describe the feature** and its use case
3. **Explain why** it would be valuable
4. **Discuss implementation** approach if you have ideas

## 🏗️ Project Structure

```
deploy-manifest-plugin/
├── deploy-manifest-core/     # Core analysis logic
│   ├── src/main/java/       # Source code
│   └── src/test/java/       # Unit tests
├── deploy-manifest-plugin/   # Maven plugin wrapper
└── docs/                     # Documentation
```

## 🔍 Code Review Checklist

Before submitting your PR, ensure:

- [ ] Code compiles without errors
- [ ] All tests pass
- [ ] New tests added for new functionality
- [ ] Code follows existing style and patterns
- [ ] JavaDoc added for public APIs
- [ ] No unnecessary dependencies added
- [ ] Documentation updated
- [ ] Commit messages follow conventions
- [ ] Branch is up-to-date with main

## 📞 Getting Help

- **GitHub Issues** - for bugs and feature requests
- **Discussions** - for questions and general discussion
- **Email** - contact maintainers directly for sensitive issues

## 📜 License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.

---

**Thank you for contributing to Maven Descriptor Plugin!** 🎉

