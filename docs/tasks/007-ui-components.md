# 007 Shared UI components: artwork, genre label, change-genre dialog
Plan item: M2 — Change genre dialog (+ shared components for Library and Discover)
Status: done
Depends on: none. Read `docs/tasks/M2-conventions.md` and `.claude/skills/compose-ui/SKILL.md`.

## Goal
Reusable composables used by Discover (008) and Library (010).

## Files
- `app/src/main/java/com/punyplatipus/podouvir/ui/components/PodcastArtwork.kt` (new)
- `app/src/main/java/com/punyplatipus/podouvir/ui/components/GenreLabel.kt` (new)
- `app/src/main/java/com/punyplatipus/podouvir/ui/components/ChangeGenreDialog.kt` (new)
- `res/values/strings_components.xml`, `res/values-pt/strings_components.xml` (new)
- Tests: `app/src/test/java/com/punyplatipus/podouvir/ui/components/` (Robolectric + compose rule)

## Public API
```kotlin
package com.punyplatipus.podouvir.ui.components

/** Square artwork, rounded (MaterialTheme.shapes.medium), Coil AsyncImage with crossfade.
 *  While loading / on error / url == null: surfaceVariant box with Icons.Rounded.Podcasts centred. */
@Composable
fun PodcastArtwork(
    artworkUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
)

/** Localised label: UNKNOWN_GENRE → stringResource(R.string.components_genre_unknown) ("Other" / "Outros"), else genre as-is. */
@Composable
fun genreLabel(genre: String): String

/**
 * AlertDialog to change a podcast's genre.
 * - Text field prefilled with [currentOverride] (empty if null); label "Genre"/"Gênero"; single line.
 * - Below it, a FlowRow of SuggestionChips for [suggestions] (exclude UNKNOWN_GENRE); tapping one fills the field.
 * - Confirm button "Save"/"Salvar": onConfirm(trimmed text, or null if blank). Disabled when the trimmed text equals the current override.
 * - Dismiss button "Cancel"/"Cancelar": onDismiss().
 * - Text button "Use feed genre (<feedGenre label>)"/"Usar gênero do feed (...)", shown only when currentOverride != null: onConfirm(null).
 */
@Composable
fun ChangeGenreDialog(
    podcastTitle: String,
    feedGenre: String,
    currentOverride: String?,
    suggestions: List<String>,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
)
```

## Acceptance criteria
- Every string in EN and PT resource files; dialog title "Change genre"/"Alterar gênero" with the podcast title as supporting text.
- `PodcastArtwork` placeholder icon has no contentDescription of its own (decorative); the image uses [contentDescription].
- Dialog behaviour exactly as in the KDoc (prefill, chip fills field, save trims, blank → null, disabled when unchanged, reset button visibility).
- `@Preview`s for the artwork placeholder and the dialog (light + dark).

## Test plan

**Test files** (all under `app/src/test/java/com/punyplatipus/podouvir/ui/components/`):
- `GenreLabelTest.kt`
- `PodcastArtworkTest.kt`
- `ChangeGenreDialogTest.kt`

**Level**: all cases are `unit` in the sense of JVM-only Robolectric + `createComposeRule()`
(`@RunWith(RobolectricTestRunner::class)`, `@Config(sdk = [34])`), as required by M2-conventions. No androidTest, no real
image loading. Strings are looked up via `ctx.getString(R.string...)` / `stringResource`, never hardcoded, except where
the EN literal is the acceptance criterion.

**Fixtures/fakes**: none beyond a small test helper in `ChangeGenreDialogTest` that renders the dialog with
`mutableListOf` recorders for `onConfirm`/`onDismiss`. `PodcastArtwork` is tested with `artworkUrl = null` (and an
unreachable/non-loadable URL only to assert the placeholder is shown, never awaiting a load). Locale switching for PT uses
`@Config(qualifiers = "pt")`.

### genreLabel (`GenreLabelTest`)
| # | name | level | given | when | then |
|---|------|-------|-------|------|------|
| 1 | `genreLabel returns the genre unchanged for a regular genre` | unit | genre "Technology" | composed | returns "Technology" |
| 2 | `genreLabel maps UNKNOWN_GENRE to Other in English and Outros in Portuguese` | unit | `UNKNOWN_GENRE`; run once default locale, once with `qualifiers = "pt"` | composed | "Other" / "Outros" (equals `R.string.components_genre_unknown`) |
| 3 | `genreLabel passes through a blank or very long genre without crashing` | unit | "" and a 500-char string | composed | returned as-is (boundary) |

### PodcastArtwork (`PodcastArtworkTest`)
| # | name | level | given | when | then |
|---|------|-------|-------|------|------|
| 4 | `placeholder is shown with no own contentDescription when url is null` | unit | `artworkUrl = null`, contentDescription "Cover" | composed | placeholder icon node exists (tagged via test tag or found as the unmerged decorative icon) and has no `ContentDescription` semantics; no node with "Cover" description is required to be an image error |
| 5 | `placeholder stays decorative and does not duplicate the supplied contentDescription` | unit | url null, contentDescription "Cover" | semantics tree inspected | at most one node carries "Cover" (the container/image), the icon itself has none |
| 6 | `null contentDescription is accepted` | unit | url null, contentDescription null | composed | renders, no node with a description (no crash) |

### ChangeGenreDialog (`ChangeGenreDialogTest`)
| # | name | level | given | when | then |
|---|------|-------|-------|------|------|
| 7 | `shows localized title, podcast title as supporting text, field label, Save and Cancel` | unit | title "My Show", override null | composed (EN, then PT) | "Change genre"/"Alterar gênero", "My Show", "Genre"/"Gênero", "Save"/"Salvar", "Cancel"/"Cancelar" displayed |
| 8 | `field is prefilled with the current override, or empty when null` | unit | override "Comedy" / null | composed | text field has EditableText "Comedy" / "" |
| 9 | `tapping a suggestion chip fills the field and excludes UNKNOWN_GENRE` | unit | suggestions ["News", UNKNOWN_GENRE, "Comedy"] | tap chip "News" | field text "News"; no chip shows the unknown label ("Other"); chips "News" and "Comedy" exist |
| 10 | `Save returns the trimmed text` | unit | override null | type "  Jazz  ", click Save | `onConfirm("Jazz")` called once; onDismiss not called |
| 11 | `Save with blank text returns null` | unit | override "Comedy" | clear field, type "   ", click Save | `onConfirm(null)` called once |
| 12 | `Save is disabled while trimmed text equals the current override` | unit | override "Comedy" | initially; then type " Comedy "; then type "Drama" | disabled, disabled, then enabled. With override null and empty field also disabled (blank equals unchanged) |
| 13 | `Cancel invokes onDismiss without confirming` | unit | any | click Cancel | `onDismiss` once; `onConfirm` never |
| 14 | `Use feed genre button is shown only with an override and calls onConfirm(null)` | unit | override null → button absent; override "Comedy", feedGenre "Technology" → "Use feed genre (Technology)"/"Usar gênero do feed (Technology)" | click it | `onConfirm(null)` once |
| 15 | `Use feed genre label localizes an unknown feed genre` | unit | override "Comedy", feedGenre `UNKNOWN_GENRE` | composed EN and PT | button reads "Use feed genre (Other)" / "Usar gênero do feed (Outros)" |

**Acceptance criteria coverage**
- EN/PT strings and dialog title/supporting text: 2, 7, 15.
- Artwork decorative placeholder / image uses contentDescription: 4, 5, 6 (image-with-description path is not exercised, see out of scope).
- Dialog behaviour (prefill, chip fill, trim, blank → null, disabled when unchanged, reset visibility): 8–14.
- Previews: compile-only; verified by the build, not by tests.

**Out of scope**
- Real Coil image loading, crossfade, error state after a failed network load, and the loaded image's contentDescription: need an image loader/network; flaky in Robolectric. Placeholder branch covers the decorative-icon rule.
- Shape (`shapes.medium`) and colours (surfaceVariant): visual details, better checked via previews/screenshot tooling.
- `@Preview` functions: no runtime behaviour; compilation is the check.
- Chip layout wrapping in `FlowRow`: layout concern, not behaviour.
- Screen-level integration with Discover/Library: covered by tasks 008/010.

## Implementation notes
- Added ui/components/{PodcastArtwork,GenreLabel,ChangeGenreDialog}.kt and values(-pt)/strings_components.xml (names: components_genre_unknown, components_change_genre_title/_field/_use_feed, components_save, components_cancel).
- Placeholder Box has `testTag("podcastArtworkPlaceholder")`; its Icon has null contentDescription; the Box itself has none either. Only the loaded image (SubcomposeAsyncImage) carries contentDescription. Loading and error both show the placeholder.
- Dialog: field state is rememberSaveable; Save enabled iff trimmed text != trimmed override (null = ""). Suggestion chips are SuggestionChips with the genre as text. Podcast title is shown under the title text inside the dialog title slot.
- Build: not run: no SDK.

## Test results
Not run: no JDK/SDK (`java` not found); CI runs them. Tests reviewed by eye against the implementation; no spec violation found.

| # | Case | File | Result |
|---|------|------|--------|
| 1 | genreLabel returns the genre unchanged for a regular genre | GenreLabelTest | NOT RUN |
| 2 | genreLabel maps UNKNOWN_GENRE to Other (EN) / Outros (PT), split into two tests | GenreLabelTest | NOT RUN |
| 3 | genreLabel passes through blank / 500-char genre (split into two tests) | GenreLabelTest | NOT RUN |
| 4 | placeholder shown, no own contentDescription | PodcastArtworkTest | NOT RUN |
| 5 | placeholder decorative, no duplicate "Cover" | PodcastArtworkTest | NOT RUN |
| 6 | null contentDescription accepted | PodcastArtworkTest | NOT RUN |
| 7 | localized title, podcast title, label, Save, Cancel (EN and PT tests) | ChangeGenreDialogTest | NOT RUN |
| 8 | prefilled with override / empty when null (two tests) | ChangeGenreDialogTest | NOT RUN |
| 9 | chip fills field, UNKNOWN_GENRE excluded | ChangeGenreDialogTest | NOT RUN |
| 10 | Save returns trimmed text | ChangeGenreDialogTest | NOT RUN |
| 11 | Save blank returns null | ChangeGenreDialogTest | NOT RUN |
| 12 | Save disabled while trimmed equals override (+ null/empty case) | ChangeGenreDialogTest | NOT RUN |
| 13 | Cancel calls onDismiss only | ChangeGenreDialogTest | NOT RUN |
| 14 | Use feed genre absent without override; click gives onConfirm(null); PT label | ChangeGenreDialogTest | NOT RUN |
| 15 | Use feed genre localizes unknown feed genre (EN and PT) | ChangeGenreDialogTest | NOT RUN |

round 1: import fix
Removed the bad top-level import `androidx.compose.ui.test.assertDoesNotExist` from PodcastArtworkTest.kt (it is a member of SemanticsNodeInteraction and was unused). The other imports were checked: assertIsDisplayed, assertIsEnabled, assertIsNotEnabled, assertTextEquals and assertCountEquals are top-level extension functions in androidx.compose.ui.test, so they stay. assertExists is only used as a member call, never imported. All cases remain NOT RUN locally (no JDK).
