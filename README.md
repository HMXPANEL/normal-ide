<p align="center">
  <img src="./images/icon.png" alt="HMX IDE" width="80" height="80"/>
</p>

<h2 align="center"><b>HMX IDE</b></h2>
<p align="center">
  A modern, lightweight coding workspace to develop real, Gradle-based Android applications on Android devices.
</p>

<p align="center">
<!-- Build and test -->
<img src="https://github.com/USERNAME/HMX-IDE/actions/workflows/build.yml/badge.svg" alt="Builds and tests">
<!-- License -->
<img src="https://img.shields.io/badge/License-GPLv3-blue.svg" alt="License">
</p>

## Project Overview

**HMX IDE** is a modern, lightweight Android coding workspace that runs directly on your Android device. It lets you create, edit, build, and debug real Gradle-based Android applications without needing a desktop computer. HMX IDE brings a focused, fast editing experience with language intelligence, a visual UI designer, and first-class Gradle integration.

## Features

- [x] Gradle support (AGP 7.2.0+).
- [x] Bundled `JDK 17` for building and running.
- [x] Custom environment variables (for Build).
- [x] API information for classes and their members (since, removed, deprecated).
- [x] Log reader (shows your app's logs in real-time).
- [ ] Language servers
    - [x] Java
    - [x] XML
    - [ ] Kotlin
- [ ] UI Designer
    - [x] Layout inflater
    - [x] Resolve resource references
    - [x] Auto-complete resource values when editing attributes
    - [x] Drag & Drop
    - [x] Visual attribute editor
    - [x] Android Widgets
- [x] Git integration.

## Screenshots

> Screenshots will be added here.

| Editor | UI Designer | Project View |
|--------|-------------|--------------|
| _Coming soon_ | _Coming soon_ | _Coming soon_ |

## Installation

- Download the latest HMX IDE APK from GitHub Releases or GitHub Actions builds.
- Install the APK on your Android device (allow installation from unknown sources if prompted).
- Launch HMX IDE and open or create a project.

## Requirements

- An Android device running Android 8.0 (API 26) or newer.
- Approximately 500 MB of free storage for the IDE and build tools.
- Internet connection for the first build (to download Gradle, dependencies, and plugins).

## Project Structure

HMX IDE is organized as a multi-module Gradle project:

- `core/` — Core application, common utilities, resources, and indexing.
- `editor/` — Code editor (based on Rosemoe's sora-editor) and tree-sitter support.
- `java/`, `xml/` — Java and XML language servers and tooling.
- `tooling/` — Gradle tooling API, model, and plugin used to build projects.
- `utilities/` — Shared utilities (preferences, templates, XML inflater, UI designer, etc.).
- `logging/` — Logging and log-sending components.
- `event/` — Event bus components.
- `annotation/` — Annotation processors for the IDE.
- `testing/` — Unit, instrumentation, and tooling tests.
- `composite-builds/` — Build logic and bundled build dependencies.

## GitHub Actions

HMX IDE uses GitHub Actions for continuous integration. The workflow `.github/workflows/build.yml` builds debug and release APKs, runs unit tests, tooling API tests, and connected checks, and publishes releases and snapshots.

## Project Goals

- Provide a fast, modern, and lightweight Android coding workspace.
- Keep the build pipeline simple, transparent, and reproducible.
- Deliver reliable Gradle-based Android app development on-device.
- Maintain a clean, extensible architecture for contributors.

## Roadmap

- [ ] Stable Kotlin language server.
- [ ] Asset Studio (Drawable & Icon Maker).
- [ ] String Translator.
- [ ] Enhanced UI Designer capabilities.
- [ ] Improved build performance and caching.

## License

HMX IDE is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

HMX IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

## Contribution

This repository is **private**. Contributions are limited to authorized maintainers. If you are a maintainer, please coordinate changes through the project's internal review process before pushing.
