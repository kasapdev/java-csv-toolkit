package dev.kasapdev.csvtoolkit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

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
