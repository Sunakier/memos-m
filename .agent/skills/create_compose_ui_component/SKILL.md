---
name: Create Compose UI Component
description: Instructions for scaffolding a new Jetpack Compose screen or component and using Koin for ViewModel injection in MemosM.
---

# Create Compose UI Component

MemosM uses Jetpack Compose for its UI.

## 1. File Location

Place new screen-level components inside `app/src/main/java/org/example/memosm/ui/component/` or a relevant sub-package.

## 2. ViewModels and Koin

The project uses Koin for Dependency Injection. If your screen requires a ViewModel (e.g., `MemosViewModel`), inject it using `koinViewModel()`.

```kotlin
import androidx.compose.runtime.Composable
import org.koin.androidx.compose.koinViewModel
import org.example.memosm.viewmodel.MemosViewModel

@Composable
fun MyNewScreen(
    viewModel: MemosViewModel = koinViewModel()
) {
    // ...
}
```

## 3. Previews

When creating a Component, always try to add a `@Preview` to ensure it renders correctly in Android Studio without building the entire app.

```kotlin
@Preview(showBackground = true)
@Composable
fun MyNewScreenPreview() {
    MaterialTheme {
        MyNewScreen(viewModel = /* mock viewmodel or state */)
    }
}
```

## 4. Navigation

If adding a completely new screen, add it to the navigation graph in `app/src/main/java/org/example/memosm/ui/MainScreen.kt` and define its route in `NavigationModels.kt`.
