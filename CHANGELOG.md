# Change Log

## Unreleased

- Removed the repeated "Configure Qilletni sources" notification. `qilletni-src` no longer needs to be marked as a Sources Root; imports find it by directory name.

### Setup
- Qilletni is detected automatically from `~/.qilletni`; the toolchain JAR, Qilletni home and JDK are optional overrides.
- Settings moved to **Settings | Languages & Frameworks | Qilletni**, with **Spotify & Last.fm** and **Package Registry** pages. The old "Qilletni: Toolchain", "Qilletni: Spotify" and "Qilletni: Libraries" pages under Tools are gone.
- Run configurations, the version check and new-project creation now launch the toolchain the same way, with a JDK 22+ (the Project SDK by default).
- Editor banners explain a missing toolchain, JDK or dependency and offer the fix.

### Dependencies
- Libraries come from each project's `qilletni.lock` (or `qilletni_info.yml` when there is no lockfile), resolved from `packages-local` then `packages` like `qilletni run`. Multiple installed versions of a package no longer disable it, and only the project's own dependencies are visible.
- Fixed reading `qll.info` from current packages, which failed for every archive.
- Fixed `lib:` imports of installed packages, which never resolved.
- Native classes of packages built by current toolchains (stored under `native/` in the `.qll`) appear again as `native.jar` under External Libraries, and the gutter icon on a library's `native fun` navigates to the decompiled Java method. Decompiled methods in a library's `native.jar` get a gutter icon back to the Qilletni `native fun`.
- Libraries load when a project opens; no manual rescan is needed.
- New **Qilletni** tool window with project dependencies and toolchain, Spotify and registry status, replacing "Qilletni Libraries".
- **Tools | Qilletni** actions: Sync Dependencies (`qpm install`), Update Lockfile, Build Package, Publish Locally, Publish to Registry, Generate Documentation, and logging in to Spotify and the package registry.
- Completion of package names and versions in `qilletni_info.yml` from the package registry, and an inspection for dependencies that aren't installed.

### Debugging
- Debug Qilletni Run configurations: line breakpoints (with conditions and log expressions), stepping, the call stack, variables, watches and Evaluate Expression. This needs a toolchain with debugger support; older toolchains show an error asking to update Qilletni. The protocol is described in `docs/debugger-protocol.md`.

### Running and building
- Toolchain logs from Run configurations appear in the Run console; builds report warnings and errors in the Build tool window and pass or fail by exit code.
- Fixed "Build Jar" (now "Rebuild native.jar") doing the opposite of what it said, and "Verbose Gradle output" passing an unknown option.
- Environment variables in Run and Build configurations are now saved.
- Run icon in the gutter of `.ql` files, and a Build configuration from the context menu of `qilletni_info.yml`.
- After a successful build, offers to publish the package.

### New projects
- Fixed project creation, which failed with current toolchains. Libraries now ask for a scope, and input is validated before the project is created.

### Spotify
- The Spotify Client ID and redirect URI are shared with Qilletni programs (`~/.qilletni/persistence/spotify.properties`), so one Spotify app setup serves both. The IDE's sign-in token is stored in the IDE password safe.
- Sign in from the music picker or completion, with progress and a notification of the result.
- One "Choose Spotify Music…" action (Ctrl+Alt+M, now also on Windows) replaces "Select Spotify music" and "Insert music…".
- Spotify completion no longer freezes while waiting for the network.

### Editor
- Comment toggling, code folding, structure view, breadcrumbs, live templates (`fun`, `entity`, `for`, `fori`, `if`) and File | New | Qilletni File.
- Function and entity indexes now work, making cross-file lookups faster.

## [1.0.0](https://github.com/Qilletni/QilletniPlugin/releases/tag/1.0.0) *(11-23-2025)*

- Initial release with basic support for the language
