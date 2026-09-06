# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [1.2.0] - 2026-09-06

### Added

- `CsvReader.parseWithHeader(String|Reader)` — parses CSV text using its first row as column
  headers, returning one insertion-ordered `Map<String, String>` per remaining row. Short rows
  omit unmatched trailing headers, long rows drop extra trailing fields, and duplicate header
  names resolve to the rightmost column.
- `CsvWriter.writeWithHeader(List<String>, List<Map<String, String>>)` — the inverse: writes a
  header row followed by one row per record map, looking up each header name in turn.

## [1.1.0] - 2026-09-06

### Added

- Test coverage for edge cases in `CsvReader` and `CsvWriter`:
  - A blank line in the middle of a document parses as a row with a
    single empty-string field, not zero fields.
  - A document with no comma or newline at all parses as one row with a
    single field.
  - A document that is just a lone comma parses as one row with two
    empty fields.
  - A document that is just a lone newline produces exactly one row (not
    a phantom second row), containing a single empty field.
  - Writing an empty list of rows produces an empty string.
  - Writing a row with zero fields produces just the CRLF terminator,
    with a note documenting the (expected, non-defective) asymmetry
    versus reading that terminator back as a one-empty-field row.

No behavioral changes were needed — all new edge-case tests passed against
the existing implementation.
