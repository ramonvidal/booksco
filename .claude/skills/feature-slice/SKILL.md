---
name: feature-slice
description: Step-by-step recipe to add a Booksco feature end-to-end (model, data, ViewModel, UI, tests, plan update). Use when implementing any item from plan.md milestones.
---

# Adding a feature

1. **Find it in `plan.md`.** If it is not there, add it (ask the owner if it changes scope).
2. **Domain model** in `domain/model/` — immutable data classes, no Android imports.
3. **Data**: Room entity + DAO (return `Flow` for observed data, `suspend` for writes); bump DB version
   with a migration if the schema changes. Network DTOs stay in `data/network/` and are mapped to domain.
4. **Repository** interface + `Impl`, bound in a Hilt module. Write unit tests with fakes.
5. **ViewModel**: `@HiltViewModel`, one `StateFlow<UiState>` built with `stateIn(WhileSubscribed(5_000))`,
   event functions. Unit-test with Turbine.
6. **UI**: follow the `compose-ui` skill (Route + stateless Screen + previews + strings).
7. **Navigation**: add a type-safe `@Serializable` route in `ui/navigation/`.
8. **Verify**: follow the `android-build-verify` skill.
9. **Docs**: tick the checkbox in `plan.md`; add a Decisions-log row if you chose between alternatives.
10. Commit with a conventional message; one feature per PR.
