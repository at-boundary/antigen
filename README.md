# Antigen

**Self-Validating, Reinforced LLM Test Generation with MetaTest**

Antigen is a standalone CLI tool that orchestrates LLM-based test generation with automated validation using MetaTest fault injection. It generates comprehensive API tests from OpenAPI specifications and iteratively improves them until they catch injected faults.

## What is Antigen?

Antigen solves a critical problem in LLM-based test generation: **how do you know if the generated tests are actually good?**

Traditional LLM test generators create tests that might compile and run, but often miss edge cases, fail to validate response fields properly, or don't catch common API faults. Antigen addresses this by:

1. **Generating tests** from API specifications using Claude Code CLI
2. **Validating tests** by injecting faults using MetaTest
3. **Iteratively improving** tests based on which faults escaped detection
4. **Repeating** until all faults are caught or max retries reached

## How It Works

### Architecture

Antigen operates as an orchestrator that coordinates three main components:

**1. Claude Code CLI Integration**
- Invokes Claude Code CLI with OpenAPI specifications
- Generates JUnit 5 tests using RestAssured and AssertJ
- Writes tests to `src/test/java/generated/` in the target project

**2. Gradle Build Integration**
- Compiles generated tests in the target project
- Runs tests without fault injection (baseline validation)
- Captures compilation errors and test failures

**3. MetaTest Fault Injection**
- Runs tests with fault injection enabled
- Injects faults: `missing_field`, `null_field`, `invalid_value`, `empty_list`, `empty_string`
- Generates fault detection report (`fault_simulation_report.json`)
- Identifies which faults escaped detection

### State Machine Pipeline

Antigen executes a 4-state pipeline for each attempt:

**State 1: Generation**
- Invokes Claude Code CLI with the API spec
- On retry: includes feedback about previous failures
- Creates/updates test files in `src/test/java/generated/`

**State 2: Build**
- Runs `./gradlew compileTestJava`
- If failed: extracts compilation errors and retries with feedback

**State 3: Test Execution**
- Runs `./gradlew test -DrunWithMetatest=false`
- Validates tests pass without fault injection
- If failed: extracts test failures and retries with feedback

**State 4: MetaTest Validation**
- Runs `./gradlew test -DrunWithMetatest=true`
- Injects faults into API responses
- Parses `fault_simulation_report.json`
- If faults escaped: provides feedback pointing Claude to the report

**Retry Loop:**
- If any state fails, adds feedback to context and returns to State 1
- Maximum retries configurable (default: 5)
- Stops early if same MetaTest failures repeat

## MetaTest Integration

### What is MetaTest?

MetaTest is a fault injection framework that mutates API responses to verify test quality. It injects common API faults and checks if tests catch them.

### Fault Types

- **missing_field**: Removes fields from JSON responses
- **null_field**: Sets field values to null
- **invalid_value**: Changes field types (e.g., string instead of number)
- **empty_list**: Returns empty arrays instead of populated ones
- **empty_string**: Returns empty strings for text fields

### Report Structure

MetaTest generates `fault_simulation_report.json` in the project root:

```json
{
  "/api/v1/endpoint": {
    "fieldName": {
      "fault_type": {
        "caught_by_any_test": true,
        "details": [
          {
            "test": "testMethodName",
            "caught": true,
            "error": "error message if caught"
          }
        ]
      }
    }
  }
}
```

**Key fields:**
- `caught_by_any_test`: Aggregated boolean - `false` means this fault escaped detection by all tests
- `details`: Individual test results showing which tests ran and whether each caught the fault

**Key insight**: Antigen doesn't parse and format every fault. Instead, it tells Claude to:
1. Read the JSON file directly using the Read tool
2. Find entries where `"caught_by_any_test": false`
3. Analyze the `details` array to see which tests failed
4. Update tests to add missing assertions

This allows Claude to recognize patterns and understand the context better than pre-formatted feedback.

## Gradle Plugin

Antigen is distributed as a Gradle plugin. Add it to your project via JitPack:

**`settings.gradle.kts`**
```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") }
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "io.antigen") {
                useModule("com.github.at-boundary:antigen:v0.2")
            }
        }
    }
}
```

**`build.gradle.kts`**
```kotlin
plugins {
    id("io.antigen") version "v0.2"
}
```

Then create `antigen.yml` in your project root and run:
```bash
./gradlew generateAITests
```

See [antigen-example](https://github.com/at-boundary/antigen-example) for a complete working example.

---

## Local Development

### Build

```bash
./gradlew build
```

### Publish to mavenLocal

Publishes the plugin to your local Maven repository (`~/.m2`), making it available to other local projects without going through JitPack.

```bash
./gradlew publishToMavenLocal
```

### Use in antigen-example (two options)

**Option A — Composite build (recommended, no publish step)**

In `antigen-example/settings.gradle.kts`, add `includeBuild` inside `pluginManagement`:
```kotlin
pluginManagement {
    includeBuild("../antigen")   // resolves plugin from local source directly
    repositories { ... }
}
```
Changes to antigen are reflected immediately. No `publishToMavenLocal` needed.

**Option B — mavenLocal**

After running `./gradlew publishToMavenLocal`, ensure `mavenLocal()` is listed first in both `pluginManagement.repositories` and `dependencyResolutionManagement.repositories` in `antigen-example/settings.gradle.kts`. Comment out `includeBuild` if present.

```kotlin
pluginManagement {
    // includeBuild("../antigen")   // commented out
    repositories {
        mavenLocal()               // picks up publishToMavenLocal artifact
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") }
    }
}
```

---

## Setup and Installation

### Prerequisites

1. **Java 17+**
   ```bash
   java -version
   ```

2. **Claude Code CLI**
   - Install from: https://claude.com/claude-code
   - Verify installation:
     ```bash
     claude --version
     ```
   - On Windows, Antigen auto-detects npm installation at:
     `%USERPROFILE%\AppData\Roaming\npm\node_modules\@anthropic-ai\claude-code\cli.js`

3. **Target Project with MetaTest**
   - Your test project must have MetaTest configured
   - See: https://github.com/codevalley/metatest

### Building Antigen

```bash
cd E:\Projects\METATEST\ANTIGEN\antigen
./gradlew build
```

### Target Project Structure

Your external test project should have this structure:

```
antigen-example/
├── build.gradle.kts          # Must include MetaTest dependency
├── src/
│   ├── main/                  # Your API implementation (optional)
│   └── test/
│       ├── java/
│       │   └── generated/     # Antigen writes tests here
│       └── resources/
│           └── config.yml     # MetaTest configuration
└── fault_simulation_report.json  # Generated by MetaTest
```

### MetaTest Configuration Example

In `src/test/resources/config.yml`:

```yaml
metatest:
  enabled: true
  fault_types:
    - missing_field
    - null_field
    - invalid_value
    - empty_list
    - empty_string
  endpoints:
    - path: /api/v1/users/{userId}
      method: GET
```

## Usage

### Generate Tests

```bash
./gradlew run --args="generate \
  --spec <path-to-openapi-spec.yaml> \
  --project <path-to-test-project> \
  --max-retries 3 \
  --verbose"
```

**Example:**
```bash
./gradlew run --args="generate \
  --spec E:\Projects\METATEST\ANTIGEN\antigen-example\simple-api-spec.yaml \
  --project E:\Projects\METATEST\ANTIGEN\antigen-example \
  --max-retries 3 \
  --verbose"
```

**Options:**
- `--spec` (required): Path to OpenAPI/Swagger YAML specification
- `--project` (required): Path to target Java project directory
- `--max-retries`: Maximum retry attempts (default: 5)
- `--verbose`: Enable detailed logging
- `--requirements`: Additional test requirements (comma-separated)
- `--timeout-build`: Build timeout in minutes (default: 5)
- `--timeout-test`: Test timeout in minutes (default: 10)
- `--timeout-metatest`: MetaTest timeout in minutes (default: 30)

### Debug Command

Test Claude CLI integration without full pipeline:

```bash
./gradlew run --args="debug --project <path-to-test-project>"
```

**Example:**
```bash
./gradlew run --args="debug --project E:\Projects\METATEST\ANTIGEN\antigen-example"
```

**What it does:**
- Verifies Claude CLI is accessible
- Creates a simple `DebugTest.java` in `src/test/java/generated/`
- Validates file creation and permissions
- Displays command execution details

**Use debug when:**
- Setting up Antigen for the first time
- Troubleshooting Claude CLI detection
- Verifying project directory structure
- Testing permission modes

## Configuration

### Default Configuration

Antigen uses sensible defaults (see `AntigenConfig.java`):

```java
maxRetries = 5
allowedTools = "Write,Read,Edit"
permissionMode = "acceptEdits"  // Safe non-interactive mode
buildTimeout = 5 minutes
testTimeout = 10 minutes
metaTestTimeout = 30 minutes
llmTimeout = 5 minutes
```

### Permission Modes

Antigen uses `--permission-mode acceptEdits` by default, which:
- Automatically accepts file write operations
- Suitable for CI/CD and automated environments
- More appropriate than `--dangerously-skip-permissions`

Alternative modes:
- `acceptEdits` (default, recommended)
- `bypassPermissions` (less documented)
- Set `useDangerousSkip = true` for `--dangerously-skip-permissions`

### Cross-Platform Support

Antigen works identically on:
- **Windows**: Auto-detects npm installation, runs via node
- **Linux/Mac**: Uses `claude` command from PATH

All paths are normalized with forward slashes internally for consistency.

## Example Workflow

### 1. Initial Generation

```bash
./gradlew run --args="generate --spec api-spec.yaml --project test-project --max-retries 3"
```

**Output:**
```
=== Attempt 1/3 ===
State 1: Generating tests with Claude...
Result: SUCCESS
State 2: Building project...
Result: SUCCESS
State 3: Running tests (without MetaTest)...
Result: SUCCESS
State 4: Running tests with MetaTest fault injection...
Result: FAILED
Fault Detection Rate: 45.2%

=== Attempt 2/3 ===
State 1: Generating tests with Claude...
[Claude reads fault_simulation_report.json and adds missing assertions]
...
```

### 2. MetaTest Feedback Example

When tests fail to catch faults, Antigen provides:

```
METATEST FAILURE - Your tests did NOT catch 23 out of 42 injected faults (45.2% detection rate).

IMPORTANT: Use the Read tool to read fault_simulation_report.json in the project root.
DO NOT write scripts to parse it - read it directly with the Read tool.

The report structure:
{
  "/api/endpoint": {
    "fieldName": {
      "fault_type": {
        "caught_by_any_test": true/false,  <- KEY FIELD: false means this fault escaped
        "details": [
          { "test": "testName", "caught": true/false, "error": "..." }
        ]
      }
    }
  }
}

Your task:
1. Read fault_simulation_report.json using the Read tool
2. Find all entries where "caught_by_any_test": false
3. Look at the "details" array to see which tests failed to catch it
4. Update those tests to add proper assertions for:
   - Field presence validation (for missing_field faults)
   - Null value checks (for null_field faults)
   - Type validation (for invalid_value faults)
   - Empty collection checks (for empty_list faults)
   - Empty string validation (for empty_string faults)

Focus on the faults where "caught_by_any_test": false - these are the ones that escaped detection.
```

Claude then:
1. Uses the Read tool to access `fault_simulation_report.json`
2. Searches for `"caught_by_any_test": false` entries
3. Identifies patterns like:
   - "All tests for the `userId` field's `null_field` fault have `caught: false`"
   - "Tests missing `.body("userId", notNullValue())` assertions"
4. Updates the specific tests to add missing validations

### 3. Success Criteria

Antigen succeeds when:
- All tests compile (State 2)
- All tests pass without fault injection (State 3)
- All MetaTest faults are caught (State 4, 100% detection rate)

## Output

### Success Output

```
=== SUCCESS ===
Generated and validated 3 test files
Attempts: 2

Generated files:
  - src/test/java/generated/UserApiTest.java
  - src/test/java/generated/PaymentApiTest.java
  - src/test/java/generated/OrderApiTest.java
```

### Files Generated

```
test-project/
└── src/test/java/generated/
    ├── UserApiTest.java
    ├── PaymentApiTest.java
    └── OrderApiTest.java
```

Each test file contains:
- JUnit 5 test methods with `@Test` annotations
- RestAssured HTTP calls
- AssertJ assertions for response validation
- Null checks, field presence validation, type validation
- Edge case testing

## Troubleshooting

### Claude CLI Not Found

**Error:**
```
Claude CLI is not available. Please install Claude Code and ensure 'claude' command is in PATH.
```

**Solution:**
1. Install Claude Code from https://claude.com/claude-code
2. On Windows, Antigen auto-detects npm installation
3. On Unix/Mac, ensure `claude` is in PATH:
   ```bash
   which claude
   ```

### Permission Denied / Hanging

**Symptom:** Command hangs or asks for permissions interactively

**Solution:**
- Antigen uses `--permission-mode acceptEdits` by default
- Verify with debug command:
  ```bash
  ./gradlew run --args="debug --project <project-path>"
  ```
- If still hanging, set `useDangerousSkip = true` in `AntigenConfig.java`

### MetaTest Report Not Found

**Error:**
```
MetaTest report not found at: fault_simulation_report.json
```

**Solution:**
1. Verify MetaTest is configured in target project
2. Check `src/test/resources/config.yml` exists
3. Ensure `build.gradle` includes MetaTest dependency
4. Run tests manually to verify report generation:
   ```bash
   cd test-project
   ./gradlew test -DrunWithMetatest=true
   ```

### Tests Not Generated in Correct Location

**Symptom:** No files in `src/test/java/generated/`

**Solution:**
1. Run debug command to verify Claude CLI integration
2. Check verbose output for errors
3. Ensure `src/test/java/generated/` directory exists (Antigen auto-creates it)
4. Verify project path is absolute and correct

## Advanced Usage

### Custom Requirements

Add specific test requirements:

```bash
./gradlew run --args="generate \
  --spec api-spec.yaml \
  --project test-project \
  --requirements 'Test authentication errors,Validate rate limiting,Check CORS headers'"
```

### Requirements File

Create `requirements.json`:
```json
{
  "requirements": [
    "Test authentication errors",
    "Validate rate limiting",
    "Check CORS headers"
  ]
}
```

Use with:
```bash
./gradlew run --args="generate \
  --spec api-spec.yaml \
  --project test-project \
  --requirements-file requirements.json"
```

## Project Structure

```
antigen/
├── src/main/java/io/antigen/
│   ├── Antigen.java                 # CLI entry point (PicoCLI)
│   ├── plugin/
│   │   ├── AntigenPlugin.java       # Gradle plugin (registers generateAITests task)
│   │   └── GenerateAITestsTask.java # Task: reads antigen.yml, runs orchestrator
│   ├── config/
│   │   ├── YamlConfig.java          # antigen.yml model
│   │   ├── YamlConfigLoader.java    # Loads antigen.yml
│   │   └── ConfigConverter.java     # YamlConfig → AntigenConfig
│   ├── orchestrator/
│   │   ├── Orchestrator.java        # State machine coordinator
│   │   ├── GenerationContext.java   # Immutable context/state
│   │   └── AntigenConfig.java       # Runtime configuration
│   ├── llm/
│   │   ├── ClaudeGenerator.java     # Claude CLI integration
│   │   └── PromptBuilder.java       # Prompt construction (supports custom templates)
│   ├── runners/
│   │   ├── GradleRunner.java        # Gradle command execution
│   │   └── ProcessExecutor.java     # Process management
│   ├── phases/
│   │   ├── GenerationPhase.java     # LLM generation result
│   │   ├── BuildPhase.java          # Compilation result
│   │   ├── TestPhase.java           # Test execution result
│   │   └── MetaTestPhase.java       # Fault injection result
│   ├── model/
│   │   ├── EscapedFault.java        # Fault that escaped detection
│   │   ├── CompilationError.java    # Build error
│   │   └── TestFailure.java         # Test failure
│   └── feedback/
│       └── ErrorParser.java         # Parse Gradle output
└── build.gradle.kts
```

## Design Principles

### 1. Separation of Concerns
- **Orchestrator**: State machine logic only
- **ClaudeGenerator**: LLM interaction only
- **GradleRunner**: Build/test execution only
- **Phases**: Immutable result objects with feedback

### 2. Immutable Context
- `GenerationContext` is immutable
- Each retry creates new context with added feedback
- No shared mutable state

### 3. Fail Fast
- Each phase returns success/failure immediately
- Orchestrator decides retry strategy
- No silent failures

### 4. LLM-Friendly Feedback
- MetaTest feedback points to JSON file, not formatted text
- Claude reads raw data for better pattern recognition
- Includes context about what each fault type means

### 5. Cross-Platform First
- All paths normalized to forward slashes
- Platform detection for Claude CLI invocation
- Gradle wrapper detection (gradlew vs gradlew.bat)

## Contributing

### Development Setup

```bash
git clone <repository-url>
cd antigen
./gradlew build
```

### Running Tests

```bash
./gradlew test
```
