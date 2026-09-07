package dev.kasapdev.csvtoolkit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An RFC 4180-compliant CSV reader.
 *
 * <p>Handles quoted fields containing commas, embedded newlines (both {@code \n} and
 * {@code \r\n}) inside quotes, and {@code ""} as an escaped literal quote within a quoted
 * field. Unquoted fields are read verbatim up to the next comma or line terminator.
 */
public final class CsvReader {

    private CsvReader() {
    }

    /**
     * Parses an entire CSV document from a string.
     *
     * @param csv the CSV text
     * @return a list of rows, each a list of field values
     */
    public static List<List<String>> parse(String csv) {
        try {
            return parse(new StringReader(csv));
        } catch (IOException e) {
            // StringReader never throws IOException in practice.
            throw new UncheckedCsvException(e);
        }
    }

    /**
     * Parses an entire CSV document from a {@link Reader}.
     *
     * @param reader the source reader; not closed by this method
     * @return a list of rows, each a list of field values
     * @throws IOException if the underlying reader fails
     */
    public static List<List<String>> parse(Reader reader) throws IOException {
        if (!reader.markSupported()) {
            reader = new BufferedReader(reader);
        }
        List<List<String>> rows = new ArrayList<>();
        List<String> currentRow = new ArrayList<>();
        StringBuilder field = new StringBuilder();

        boolean inQuotes = false;
        boolean rowHasContent = false;
        boolean fieldStarted = false;

        int ch;
        while ((ch = reader.read()) != -1) {
            char c = (char) ch;

            if (inQuotes) {
                if (c == '"') {
                    int next = peekNext(reader);
                    if (next == '"') {
                        // Escaped quote: consume the peeked char and append a literal quote.
                        reader.read();
                        field.append('"');
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else {
                if (c == '"' && field.length() == 0 && !fieldStarted) {
                    inQuotes = true;
                    fieldStarted = true;
                    rowHasContent = true;
                } else if (c == ',') {
                    currentRow.add(field.toString());
                    field.setLength(0);
                    fieldStarted = false;
                    rowHasContent = true;
                } else if (c == '\r') {
                    // Look ahead for \n to treat \r\n as a single line terminator.
                    int next = peekNext(reader);
                    if (next == '\n') {
                        reader.read();
                    }
                    currentRow.add(field.toString());
                    field.setLength(0);
                    fieldStarted = false;
                    rows.add(currentRow);
                    currentRow = new ArrayList<>();
                    rowHasContent = false;
                } else if (c == '\n') {
                    currentRow.add(field.toString());
                    field.setLength(0);
                    fieldStarted = false;
                    rows.add(currentRow);
                    currentRow = new ArrayList<>();
                    rowHasContent = false;
                } else {
                    field.append(c);
                    fieldStarted = true;
                    rowHasContent = true;
                }
            }
        }

        // Flush the final field/row if the input didn't end with a line terminator.
        if (fieldStarted || field.length() > 0 || rowHasContent || !currentRow.isEmpty()) {
            currentRow.add(field.toString());
            rows.add(currentRow);
        }

        if (inQuotes) {
            throw new CsvFormatException("Unterminated quoted field at end of input");
        }

        return rows;
    }

    /**
     * Returns a streaming, row-at-a-time view over a CSV document read incrementally from a
     * {@link Reader}, without loading the whole document into memory.
     *
     * <p>This is an alternative to {@link #parse(Reader)} for large documents: rather than
     * returning a fully materialized {@code List<List<String>>}, it returns a
     * {@link CsvStreamReader} that pulls characters from {@code reader} only as rows are
     * requested. Applies the identical dialect rules as {@link #parse(Reader)} and produces
     * identical rows given identical input.
     *
     * @param reader the source reader; not closed by this method — close the returned
     *               {@link CsvStreamReader} (which closes {@code reader} in turn) when done
     * @return a lazily-evaluated iterator/iterable over the document's rows
     */
    public static CsvStreamReader stream(Reader reader) {
        return new CsvStreamReader(reader);
    }

    /**
     * Parses CSV text using its first row as a header, returning each subsequent row as an
     * insertion-ordered {@code Map<String, String>} keyed by header name.
     *
     * <p>If a data row has fewer fields than there are headers, the unmatched trailing headers
     * are simply omitted from that row's map. If a data row has more fields than there are
     * headers, the extra trailing fields are dropped. Duplicate header names are resolved by
     * keeping the value under the rightmost matching column, per normal {@code Map} put
     * semantics.
     *
     * @param csv the CSV text; its first row is treated as column headers
     * @return one map per data row (excluding the header row itself), in row order
     * @throws IllegalArgumentException if the document is empty (no header row present)
     */
    public static List<Map<String, String>> parseWithHeader(String csv) {
        try {
            return parseWithHeader(new StringReader(csv));
        } catch (IOException e) {
            // StringReader never throws IOException in practice.
            throw new UncheckedCsvException(e);
        }
    }

    /**
     * Same as {@link #parseWithHeader(String)}, reading from a {@link Reader}.
     *
     * @param reader the source reader; not closed by this method
     * @return one map per data row (excluding the header row itself), in row order
     * @throws IOException if the underlying reader fails
     * @throws IllegalArgumentException if the document is empty (no header row present)
     */
    public static List<Map<String, String>> parseWithHeader(Reader reader) throws IOException {
        List<List<String>> rows = parse(reader);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Cannot parse with header: document has no rows");
        }
        List<String> header = rows.get(0);
        List<Map<String, String>> records = new ArrayList<>();
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            int columns = Math.min(header.size(), row.size());
            Map<String, String> record = new LinkedHashMap<>();
            for (int i = 0; i < columns; i++) {
                record.put(header.get(i), row.get(i));
            }
            records.add(record);
        }
        return records;
    }

    /**
     * Peeks at the next character without consuming it, using mark/reset. Callers of
     * {@link #parse(Reader)} are guaranteed a mark-supporting reader since it wraps any
     * non-mark-supporting reader in a {@link BufferedReader} first.
     */
    private static int peekNext(Reader reader) throws IOException {
        reader.mark(1);
        int next = reader.read();
        reader.reset();
        return next;
    }

    /** Wraps an unexpected IOException from a source that should never throw one. */
    static final class UncheckedCsvException extends RuntimeException {
        UncheckedCsvException(IOException cause) {
            super(cause);
        }
    }
}
