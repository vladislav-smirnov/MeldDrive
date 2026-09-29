---
applyTo: "**/*.kt,**/*.kts"
---

# Kotlin & General Style Guidelines for MeldDrive

- **Companion Objects**: `companion object` MUST ALWAYS be placed at the very bottom of the class declaration, after all member functions, properties, and inner/nested classes.
- **Naming Conventions**:
  - `Boolean` properties MUST start with `is`, `has`, or `are` (e.g., `isHidden`, `hasPermission`, `areNotificationsEnabled`).
  - Use PascalCase for classes, interfaces, objects, and composable functions.
  - Use lowerCamelCase for variables, properties, functions, and parameters.
- **Modifier Order**: Always adhere strictly to standard Kotlin modifier order (`override open suspend fun ...`).
- **Quality Gates**: All Kotlin code must pass Detekt static analysis (`config/detekt/detekt.yml`) with zero issues (`warningsAsErrors: true`) and adhere to Spotless formatting.
