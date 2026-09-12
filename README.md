# AnaRapport

A Java Swing viewer for textile "rapport" patterns (pattern repeats). Loads an
image and previews it tiled across a grid, with zoom, pan, and three repeat
modes — aimed at previewing and preparing a motif before handing it off to a
tool like Photoshop for final production work. The UI supports multiple
languages (English by default; see **Language** under Usage below).

## Requirements

- Java 17+ (only needed to build/run from source; not needed for the Windows download below)
- Maven 3.8+

## Getting it

- **Windows, no Java required**: grab `AnaRapport-windows.zip` from the
  [latest release](https://github.com/amaralhcarlos/anarapport/releases/latest),
  unzip it, and run `AnaRapport.exe` — it bundles its own Java runtime
  (built with `jpackage`).
- **Any platform, with Java installed**: grab `anarapport.jar` from the same
  release and run `java -jar anarapport.jar`.
- **From source**:
  ```bash
  mvn clean package
  java -jar target/anarapport.jar
  ```
  The `maven-shade-plugin` packages a self-contained executable jar at
  `target/anarapport.jar` (manifest points to `com.anarapport.app.Main`).

Pushing a `vX.Y.Z` tag triggers `.github/workflows/release.yml`, which builds
both of the above and attaches them to a new GitHub Release.

## Usage

- **Language** menu: one item per supported language (English, the default,
  and Português for now). Switching languages updates the whole UI (menus,
  labels, dialogs) immediately, no restart needed, and the choice is saved
  (via `java.util.prefs.Preferences`) for the next time the app opens. With
  no saved preference, the app always starts in English.
- **File → Open image**: loads a PNG, JPEG, TIFF, BMP or GIF as the rapport
  motif — any format the JDK's own `ImageIO` can decode without extra
  plugins. The technical info panel (ICC profile, EXIF, JPEG quality) only
  extracts those specific details from PNG/JPEG today; for other formats, or
  whenever a given piece of data simply isn't in the file, it shows "Not
  available" instead of failing.
- **Mode toolbar**: switches between the three repeat modes at any time,
  keeping the current zoom/pan.
- **Grid size**: controls how many motif columns fit across the visible
  width (3 to 9).
- **Show seam lines**: draws a subtle line along each cell's border, with a
  selectable style (dark gray, white, dashed gray, or high-contrast red), to
  help spot where each repeat starts and ends.
- **Horizontal / vertical offset (%)**: pulls cells apart or overlaps them,
  as a percentage of the motif's own size (-50% to 100%, default 0% = exact
  edge-to-edge fit). Positive values open a visible gap between repeats;
  negative values simulate a slight overlap. Works with all three rapport
  modes.
- **Zoom**: mouse wheel, centered on the cursor.
- **Pan**: drag with the left mouse button held down.
- **File → Export composition**: renders exactly what's currently visible on
  screen (grid, rapport mode, and seam lines if enabled) to a PNG or JPEG
  file.
- **Image → Image information...**: opens a dialog with technical data about
  the file, aimed at someone who will rework it in another tool (e.g.
  Photoshop) — pixel dimensions, DPI (when the file specifies one),
  estimated physical size in cm/inches, color mode, bit depth, embedded ICC
  profile, format, file size, path, last modified date, and EXIF metadata
  (camera, capture date, orientation) when present. Fields that aren't
  available in the file show "Not available" rather than being silently
  omitted. Its **Advanced analysis** section only runs on demand ("Analyze
  image" button, with a progress bar backed by a `SwingWorker`, since it
  processes the image pixel by pixel):
  - Unique color count.
  - Alpha channel presence and percentage of transparent pixels.
  - An approximate out-of-CMYK-gamut warning (a saturation/brightness
    heuristic, since the JDK ships no CMYK ICC profile to convert against).
  - A print-resolution check against a user-supplied print size (cm or
    inches) and a configurable minimum recommended DPI.
  - Estimated JPEG quality (derived from the file's actual quantization
    table), when the file is a JPEG.
  - Dominant color palette (top colors with a swatch and hex/RGB value).
  - An edge-continuity score: compares the left/right and top/bottom borders
    (as they'd meet when the motif is tiled side by side) and buckets the
    result into "Good continuity", "Attention", or "High discontinuity".

## Rapport modes

- **Straight**: the motif repeats on a plain grid, every row and column
  aligned, with no offset between them.
- **Half Drop**: alternating columns are shifted down by half the motif's
  height, following the textile half-drop convention.
- **Mirrored**: each cell alternates horizontal flip, vertical flip, or
  both, based on its row/column parity (a checkerboard of orientations), so
  the motif mirrors continuously across every edge shared with a
  neighboring cell.

## Code structure

- `app` — entry point (`Main`) and UI assembly.
- `ui` — `ImagePanel` (the custom panel that draws the grid and handles
  zoom/pan) and `ImageInfoDialog` (the technical info + advanced analysis
  dialog).
- `render` — `RapportRenderer`, the tiling algorithm for each rapport mode
  (includes a cache of pre-scaled tile bitmaps to keep pan/zoom smooth).
- `model` — application state (`AppState`), the `RapportType`, `SeamStyle`
  and `ColorMode` enums, and the `ImageMetadata`/`ExifInfo` records.
- `io` — loading (`ImageLoader`), exporting (`ImageExporter`), and technical
  metadata extraction (`ImageMetadataReader`, `ExifReader`) for image files.
- `analysis` — `ImageAnalyzer`, the pixel-level calculations behind
  "Advanced analysis" (unique/dominant colors, transparency, approximate
  CMYK gamut, edge continuity, resolution check, estimated JPEG quality),
  reused by the UI layer, which is only responsible for triggering the
  analysis and displaying its result.
- `i18n` — `Messages`, the localized-text loader
  (`src/main/resources/i18n/messages*.properties`) used throughout the UI;
  keeps track of the current language and notifies listeners so they can
  rebuild the UI when it changes. `RapportType`, `SeamStyle`, and
  `ContinuityLevel` also go through it to resolve their own display text.
