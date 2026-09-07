# java-csv-toolkit

[![CI](https://github.com/kasapdev/java-csv-toolkit/actions/workflows/ci.yml/badge.svg)](https://github.com/kasapdev/java-csv-toolkit/actions/workflows/ci.yml) [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE) ![Java](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white)

A zero-dependency, RFC 4180-compliant CSV reader and writer for Java. Correctly handles
quoted fields containing commas, embedded newlines, and escaped (`""`) double quotes, in
both directions — parsing and serializing. Also includes a streaming, row-at-a-time reader
for documents too large to load into memory at once. Pure Java 17, no external libraries,
no build tool required.

## Build & Run

```bash
JAVAC="/path/to/jdk/bin/javac"
JAVA="/path/to/jdk/bin/java"

# Compile the library
"$JAVAC" -d out $(find src/main/java -name "*.java")

# Compile the tests against the compiled library
"$JAVAC" -cp out -d out $(find src/test/java -name "*.java")

# Run the tests
"$JAVA" -cp out dev.kasapdev.csvtoolkit.CsvReaderTest
"$JAVA" -cp out dev.kasapdev.csvtoolkit.CsvWriterTest
"$JAVA" -cp out dev.kasapdev.csvtoolkit.CsvStreamReaderTest
```

On Windows, replace `$(find ... -name "*.java")` with an explicit file list, or run the
`find` substitution from Git Bash / WSL.

## Usage

```java
import dev.kasapdev.csvtoolkit.CsvReader;
import dev.kasapdev.csvtoolkit.CsvWriter;

import java.util.List;
import java.util.Map;

public class Example {
    public static void main(String[] args) {
        List<List<String>> table = List.of(
                List.of("Name", "Notes"),
                List.of("Ada, Lovelace", "Said \"hello, world\"\nnew line too")
        );

        String csv = CsvWriter.write(table);
        System.out.print(csv);

        List<List<String>> parsedBack = CsvReader.parse(csv);
        System.out.println(parsedBack.equals(table)); // true

        // Header-aware mode: read rows as Map<String, String> keyed by column name.
        List<Map<String, String>> people = CsvReader.parseWithHeader("name,age\nAda,36\nGrace,85");
        System.out.println(people.get(0).get("name")); // "Ada"

        String backToCsv = CsvWriter.writeWithHeader(List.of("name", "age"), people);
        System.out.print(backToCsv);
    }
}
```

A more realistic example — reading a CSV file from disk with a header row and summing a column:

```java
import dev.kasapdev.csvtoolkit.CsvReader;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Map;

public class SumOrders {
    public static void main(String[] args) throws IOException {
        try (Reader in = new FileReader("orders.csv")) {
            List<Map<String, String>> orders = CsvReader.parseWithHeader(in);
            double total = 0;
            for (Map<String, String> order : orders) {
                total += Double.parseDouble(order.get("amount"));
            }
            System.out.println("Total: " + total);
        }
    }
}
```

## Streaming CSV Reader

For CSV documents too large to comfortably parse into a single in-memory `List<List<String>>`,
`CsvReader.stream(Reader)` returns a `CsvStreamReader`: a row-at-a-time iterator that pulls
characters from the underlying `Reader` only as rows are requested, and never buffers more than
the current row. It applies the exact same RFC 4180 dialect rules as `CsvReader.parse(Reader)` —
quoted fields, embedded commas/newlines, and escaped (`""`) quotes are handled identically.

```java
import dev.kasapdev.csvtoolkit.CsvReader;
import dev.kasapdev.csvtoolkit.CsvStreamReader;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.List;

public class StreamLargeFile {
    public static void main(String[] args) throws IOException {
        try (Reader in = new FileReader("huge-export.csv");
             CsvStreamReader rows = CsvReader.stream(in)) {
            long rowCount = 0;
            for (List<String> row : rows) {
                // Each row is processed and discarded immediately — the whole file is
                // never held in memory at once.
                rowCount++;
            }
            System.out.println("Processed " + rowCount + " rows");
        }
    }
}
```

`CsvStreamReader` implements both `Iterator<List<String>>` (so `hasNext()`/`next()` work
directly) and `Iterable<List<String>>` (so it can be the target of an enhanced `for` loop, as
above). It also implements `Closeable`; closing it closes the underlying `Reader`.

## API

### `CsvReader`

- `static List<List<String>> parse(String csv)` — parses CSV text into rows of fields.
- `static List<List<String>> parse(Reader reader)` — same, reading from a `Reader`. Wraps
  the reader in a `BufferedReader` automatically if it doesn't support `mark`/`reset`.
- `static List<Map<String, String>> parseWithHeader(String csv)` — parses CSV text using its
  first row as column headers, returning one insertion-ordered `Map<String, String>` per
  remaining row. A short row omits its unmatched trailing headers; a long row drops its extra
  trailing fields; a duplicate header name keeps the rightmost column's value. Throws
  `IllegalArgumentException` if the document has no rows at all.
- `static List<Map<String, String>> parseWithHeader(Reader reader)` — same, reading from a
  `Reader`.
- `static CsvStreamReader stream(Reader reader)` — returns a row-at-a-time streaming view over
  the document, reading incrementally instead of loading the whole document into memory. See
  [Streaming CSV Reader](#streaming-csv-reader).
- Throws `CsvFormatException` on an unterminated quoted field.

### `CsvStreamReader`

Implements `Iterator<List<String>>`, `Iterable<List<String>>`, and `Closeable`.

- `CsvStreamReader(Reader reader)` — wraps a `Reader` for row-at-a-time streaming. Wraps the
  reader in a `BufferedReader` automatically if it doesn't support `mark`/`reset`.
- `boolean hasNext()` — whether another row is available; safe to call repeatedly without
  advancing.
- `List<String> next()` — returns the next row, reading only as many characters as needed to
  assemble it. Throws `NoSuchElementException` once the document is exhausted.
- `Iterator<List<String>> iterator()` — returns `this`, so the instance can be used directly as
  the target of an enhanced `for` loop.
- `void close()` — closes the underlying `Reader`.
- Throws `CsvFormatException` on an unterminated quoted field, same as `CsvReader`.

### `CsvWriter`

- `static String write(List<List<String>> rows)` — serializes rows back to CSV text, using
  `\r\n` row terminators per RFC 4180. Any field containing a comma, double quote, or
  newline is quoted, with internal quotes doubled (`"` -&gt; `""`). `null` fields serialize
  as empty strings.
- `static String writeWithHeader(List<String> header, List<Map<String, String>> records)` —
  writes the header row followed by one row per record, looking up each header name in the
  record's map; a missing key writes an empty field and keys not in the header are ignored.

## License

MIT — see [LICENSE](LICENSE).
