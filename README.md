# java-csv-toolkit

[![CI](https://github.com/kasapdev/java-csv-toolkit/actions/workflows/ci.yml/badge.svg)](https://github.com/kasapdev/java-csv-toolkit/actions/workflows/ci.yml) [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE) ![Java](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white)

A zero-dependency, RFC 4180-compliant CSV reader and writer for Java. Correctly handles
quoted fields containing commas, embedded newlines, and escaped (`""`) double quotes, in
both directions — parsing and serializing. Pure Java 17, no external libraries, no build
tool required.

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
```

On Windows, replace `$(find ... -name "*.java")` with an explicit file list, or run the
`find` substitution from Git Bash / WSL.

## Usage

```java
import dev.kasapdev.csvtoolkit.CsvReader;
import dev.kasapdev.csvtoolkit.CsvWriter;

import java.util.List;

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
    }
}
```

## API

### `CsvReader`

- `static List<List<String>> parse(String csv)` — parses CSV text into rows of fields.
- `static List<List<String>> parse(Reader reader)` — same, reading from a `Reader`. Wraps
  the reader in a `BufferedReader` automatically if it doesn't support `mark`/`reset`.
- Throws `CsvFormatException` on an unterminated quoted field.

### `CsvWriter`

- `static String write(List<List<String>> rows)` — serializes rows back to CSV text, using
  `\r\n` row terminators per RFC 4180. Any field containing a comma, double quote, or
  newline is quoted, with internal quotes doubled (`"` -&gt; `""`). `null` fields serialize
  as empty strings.

## License

MIT — see [LICENSE](LICENSE).
