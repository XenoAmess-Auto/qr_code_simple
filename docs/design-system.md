# QR Code Simple Technology-Editorial Design System

## 1. Status and scope

This document is the canonical visual specification for QR Code Simple. It applies to every new or modified screen, dialog, list item, widget, custom view, and exported app-branded graphic.

The default direction for all new features is **technology editorial**:

- light mode: porcelain canvas, ink typography, petroleum-cyan emphasis;
- dark mode: blue-black canvas, charcoal surfaces, ice-cyan emphasis;
- strong information hierarchy with restrained decoration;
- flat outlined panels instead of elevated card stacks;
- one primary action per section;
- technical metadata may use monospace typography, while normal content does not.

The About screen is the original approved reference. New work must consume the shared `app_*` semantic colors and `Widget.QRCodeSimple.*` styles rather than copying About-specific literals.

## 2. Principles

### 2.1 Editorial hierarchy

- Lead with a clear title, current state, or task rather than a decorative hero card.
- Use a short accent rail plus a strong section heading to divide long pages.
- Keep supporting information visually quieter than the main value or action.
- Do not give unrelated actions equal visual weight.

### 2.2 Restrained surfaces

- Page canvas uses `app_background`.
- Content panels use `app_surface`, a 1dp `app_outline_variant` stroke, 12dp radius, and 0dp elevation.
- Compact controls and buttons use an 8dp radius.
- Avoid nested filled cards. A panel may contain flat rows separated by inset dividers.
- Shadows are reserved for transient overlays that need physical separation.

### 2.3 Deliberate color

- Petroleum/ice cyan identifies the active state, key metadata, and the single primary action.
- Normal icons and supporting text use neutral semantic colors.
- Purple/lavender Material defaults are not part of the application chrome.
- Barcode artwork, the color picker spectrum, images, and user-selected generation colors are content and may use any color.
- Success, warning, and error use their semantic tokens and never reuse the primary accent.

## 3. Semantic palette

Application chrome must reference semantic resources, not raw hex colors.

| Role | Light | Dark | Resource |
|---|---:|---:|---|
| Canvas | `#F4F7F6` | `#071012` | `app_background` |
| Surface | `#FBFDFC` | `#0E191B` | `app_surface` |
| Surface variant | `#EAF1EF` | `#172629` | `app_surface_variant` |
| Primary | `#006B70` | `#79DCE2` | `app_primary` |
| Primary container | `#D8EFED` | `#153438` | `app_primary_container` |
| Primary text | `#172020` | `#F0F6F5` | `app_text_primary` |
| Secondary text | `#596967` | `#A7B8B7` | `app_text_secondary` |
| Strong outline | `#758684` | `#819391` | `app_outline` |
| Panel outline | `#CFDDDA` | `#294044` | `app_outline_variant` |
| Divider | `#E0E9E7` | `#203336` | `app_divider` |

The Material 3 theme maps these resources into primary, secondary, tertiary, surface, outline, control, navigation, and dialog roles. Components must not depend on unspecified Material defaults.

## 4. Typography

- Page title: Material `HeadlineSmall` or `TitleLarge`, bold only when it establishes the page hierarchy.
- Section title: `TextAppearance.QRCodeSimple.SectionLabel`.
- Row title/body: `TextAppearance.QRCodeSimple.RowTitle` or the corresponding Material body role.
- Supporting copy and metadata: `TextAppearance.QRCodeSimple.Metadata`.
- Use monospace only for hashes, versions, timestamps, format identifiers, logs, and similarly technical values.
- Avoid hardcoded text sizes when a Material text appearance expresses the role.
- Do not use color alone to distinguish a status.

## 5. Spacing and shape

Shared dimensions live in `values/dimens.xml`.

- screen gutter: 16dp;
- spacing scale: 4, 8, 12, 16, 24dp;
- compact radius: 8dp;
- panel radius: 12dp;
- panel stroke: 1dp;
- minimum touch target: 48dp;
- standard editorial row: minimum 64dp;
- icon tile: 36dp;
- readable tablet content width: 560dp unless a two-pane workflow needs more space.

Arbitrary 20-24dp cards and full-width pills are not the default style.

## 6. Components

### 6.1 Panels and rows

- Use `Widget.QRCodeSimple.Panel` for grouped content.
- Use `Widget.QRCodeSimple.EditorialRow` for navigation and setting rows.
- A row contains, in order: optional icon tile, title/supporting value, optional state/control, and optional directional affordance.
- Use `app_divider` for inset row dividers.
- Decorative icons use `contentDescription="@null"`; the containing row owns the accessible label.

### 6.2 Buttons

- Primary: `Widget.QRCodeSimple.Button.Primary`; at most one visible primary action per section.
- Secondary: `Widget.QRCodeSimple.Button.Outlined`.
- Tertiary: `Widget.QRCodeSimple.Button.Text`.
- Destructive: `Widget.QRCodeSimple.Button.Destructive`; destructive actions must not look like ordinary primary actions.
- Do not mix several filled buttons in one action group.

### 6.3 Inputs and choices

- Use `Widget.QRCodeSimple.TextInput` and Material text fields, not bare `EditText` for app-authored forms.
- Use the shared switch thumb/track selectors.
- Chips, toggles, radio controls, and selected indicators use the primary container/on-primary-container pair.
- Labels remain visible after input; hints are not the only accessible label.

### 6.4 Navigation and app bars

- Bottom navigation uses the shared petroleum/ice selected state and a compact active indicator.
- Standalone pages use the common ActionBar or an in-layout Material toolbar with a zero-elevation surface and clear Up navigation.
- Camera surfaces may use translucent blue-black chrome, but active states still use ice cyan.

### 6.5 Dialogs

- Use the project Material alert-dialog overlay.
- Forms inside dialogs use styled text-input layouts.
- Validation errors remain visible and do not dismiss the dialog.
- Long technical content uses a scrollable/selectable region.
- System pickers and biometric prompts remain system-controlled.

### 6.6 Lists and result cards

- Prefer flat rows with dividers or restrained outlined panels.
- Keep at most one frequent direct row action; move secondary actions to overflow where practical.
- History rows place favorite and overflow beside the content block; secondary actions must not create a separate action row.
- Interactive icons expose at least a 48dp target and state-aware descriptions.
- Barcode previews use a deliberate light substrate so codes remain readable in dark mode.

### 6.7 Status treatments

- Success: `app_success*` resources.
- Warning: `app_warning*` resources.
- Error/destructive: `app_error*` resources.
- Every status includes text or an icon in addition to color.

## 7. Responsive behavior

- Support phone portrait, landscape, and `sw600dp+`.
- Long forms use a centered readable width or an intentional two-pane layout.
- Do not use horizontal action/radio groups that depend on English label length.
- Dense history controls use a two-row, three-column grid; labels auto-size within a 10-13sp range while retaining 48dp touch targets.
- Use `wrap_content` plus minimum heights rather than fixed heights for translated or scalable text.
- Verify English, Simplified Chinese, German, and Russian at minimum during visual review; all ten locales remain resource-complete.

## 8. Accessibility requirements

- Normal text meets WCAG AA contrast; large text and non-text controls meet their applicable thresholds.
- Touch targets are at least 48dp.
- Section labels should be accessibility headings where the platform/API permits.
- Switches, checkboxes, and custom controls expose labels and state.
- Focus order follows visual reading order.
- Test at 1.0x and 1.3x font scale for routine changes; use 2.0x for new component families or dense screens.
- RTL directional icons use `autoMirrored` or direction-aware resources.

## 9. Exceptions

- Camera preview and scan-region overlays may be full bleed and use translucent dark chrome.
- Barcode colors, gradients, uploaded logos, images, and color spectra are user content, not application chrome.
- Home-screen widgets use explicit day/night resources because launcher theme attributes are unreliable.
- Platform permission screens, document pickers, camera providers, and biometric prompts retain platform styling.

## 10. Implementation checklist

Before merging a new UI feature:

1. Use semantic `app_*` colors; do not add a raw chrome color without documenting a new semantic role.
2. Use shared panel, row, button, input, switch, navigation, and typography styles.
3. Identify the single primary action in every section.
4. Verify light and dark mode contain no inherited purple/lavender controls.
5. Verify phone and tablet/landscape behavior.
6. Verify translated text and font scaling do not clip.
7. Verify 48dp targets, content descriptions, state descriptions, and contrast.
8. Run unit tests, lint, and the coverage gate.
9. Capture emulator screenshots for any substantial visual change and review aesthetics, not only layout correctness.

## 11. Source files

- Palette: `app/src/main/res/values/colors.xml`, `values-night/colors.xml`
- Theme mapping: `app/src/main/res/values/themes.xml`, plus day/night `bools.xml` system-bar resources
- Components: `app/src/main/res/values/styles.xml`
- Dimensions: `app/src/main/res/values/dimens.xml`
- Reference screen: `app/src/main/res/layout/view_about_content.xml`
- UI verification strategy: `docs/testing-strategy.md`, `docs/ui-testing-plan.md`
