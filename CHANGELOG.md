# Changelog

All notable changes to this project will be documented in this file.

The changelog format is based on [Keep a Changelog] and [CommonMark].
This project adheres to [Semantic Versioning].

## [0.0.11] - 2026-10-09

### Added in 0.0.11

- Java Migration Guide (`java/Migration.md`) covering the version 3.x to 4.0 SDK changes
- Python snippet tests (`python/tests`) running every snippet end to end against a temporary SQLite repository
- GitHub Actions workflows running the Python snippet tests on Linux, macOS and Windows

### Changed in 0.0.11

- Python snippets exit with status 1 and report to stderr when a Senzing error stops them
- `resources/output/` is now part of the repository so snippets writing with-info output work on a fresh clone
- `add_queue.py` uses a producer thread instead of separate processes, matching the Java and C# snippets
- `signal_handler.py` waits with a sleep loop instead of `signal.pause()`, which isn't available on Windows

### Fixed in 0.0.11

- `redo_with_info_continuous.py` raised a `TypeError` instead of exiting when a Senzing error occurred
- `add_queue.py` loaded no records but exited successfully on macOS, Windows and Python 3.14+, and could stop early if the
  queue was momentarily empty

## [0.0.10] - 2025-08-11

### Added in 0.0.10

- New Python snippets

## [0.0.9] - 2025-07-23

### Changed in 0.0.9

- Modify method names changed in SDK
- Additional snippets

## [0.0.8] - 2025-06-20

### Changed in 0.0.8

- Small improvements to Python snippets

## [0.0.7] - 2025-06-03

### Changed in 0.0.7

- Small improvements to Python snippets

## [0.0.6]

### Changed in 0.0.6

- Improved Python add_data_sources.py

## [0.0.5]

### Changed in 0.0.5

- Modified configuration examples for new szconfig and szconfigmanager pattern

## [0.0.4]

### Added to 0.0.4

- C# examples
- Updated Java examples to move declarations to the bottom

## [0.0.3]

### Added to 0.0.3

- Java examples

## [0.0.2]

### Changed in 0.0.2

- Modify Python imports to use senzing and senzing_core

### Added to 0.0.2

- Couple of new examples

## [0.0.1]

### Added to 0.0.1

- Initial for V4

[CommonMark]: https://commonmark.org/
[Keep a Changelog]: https://keepachangelog.com/
[Semantic Versioning]: https://semver.org/
