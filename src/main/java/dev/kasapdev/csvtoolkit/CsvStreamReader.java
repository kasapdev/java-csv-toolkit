package dev.kasapdev.csvtoolkit;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * A streaming, row-at-a-time CSV reader.
 *
 * <p>Unlike {@link CsvReader#parse(Reader)}, which reads an entire document into memory before
 * returning a {@code List<List<String>>}, {@code CsvStreamReader} pulls characters from its
 * underlying {@link Reader} only as rows are requested, and never holds more than the current
 * row's fields in memory. This makes it suitable for CSV documents too large to comfortably
 * load as a single in-memory list of rows.
 *
 * <p>Applies the exact same character-level dialect rules as {@link CsvReader#parse(Reader)}:
 * quoted fields may contain commas and embedded newlines (both {@code \n} and {@code \r\n})
 * inside quotes, and {@code ""} within a quoted field decodes to a literal {@code "}. Given the
 * same input, {@code CsvStreamReader} and {@link CsvReader#parse(Reader)} produce identical rows
 * in identical order.
 *
 * <p>Obtain an instance via {@link CsvReader#stream(Reader)} or the public constructor, then
 * either drive it directly as an {@link Iterator} or use it in an enhanced {@code for} loop,
 * since it also implements {@link Iterable}:
 *
 * <pre>{@code
 * try (Reader in = new FileReader("large.csv");
 *      CsvStreamReader rows = CsvReader.stream(in)) {
 *     for (List<String> row : rows) {
 *         // process one row at a time; the whole file is never held in memory
 *     }
 * }
 * }</pre>
 *
 * <p>Not thread-safe, and not reusable once exhausted. Closing this reader closes the
 * underlying {@link Reader}.
 */
public final class CsvStreamReader implements Iterator<List<String>>, Iterable<List<String>>, Closeable {

    private final Reader reader;

    private boolean inQuotes = false;
    private boolean rowHasContent = false;
    private boolean fieldStarted = false;
    private boolean exhausted = false;

    private final StringBuilder field = new StringBuilder();
    private List<String> currentRow = new ArrayList<>();

    private List<String> pendingRow;
    private boolean pendingComputed = false;

    /**
     * Wraps a {@link Reader} for row-at-a-time streaming.
     *
     * @param reader the source reader; wrapped in a {@link BufferedReader} automatically if it
     *               doesn't support {@code mark}/{@code reset}
     */
    public CsvStreamReader(Reader reader) {
        this.reader = reader.markSupported() ? reader : new BufferedReader(reader);
    }

    /**
     * Returns whether there is at least one more row available.
     *
     * @return {@code true} if {@link #next()} would return another row
     */
    @Override
    public boolean hasNext() {
        if (!pendingComputed) {
            pendingRow = advance();
            pendingComputed = true;
        }
        return pendingRow != null;
    }

    /**
     * Returns the next row of fields, reading only as many characters from the underlying
     * {@link Reader} as are needed to assemble it.
     *
     * @return the next row, as a list of field values
     * @throws NoSuchElementException if the document has no more rows
     */
    @Override
    public List<String> next() {
        if (!hasNext()) {
            throw new NoSuchElementException("No more CSV rows");
        }
        List<String> row = pendingRow;
        pendingRow = null;
        pendingComputed = false;
        return row;
    }

    /**
     * Returns this reader itself, so it may be used directly as the target of an enhanced
     * {@code for} loop.
     *
     * @return this iterator
     */
    @Override
    public Iterator<List<String>> iterator() {
        return this;
    }

    /**
     * Closes the underlying {@link Reader}.
     *
     * @throws IOException if closing the underlying reader fails
     */
    @Override
    public void close() throws IOException {
        reader.close();
    }

    /**
     * Reads characters one at a time, applying the same character-level state machine as
     * {@link CsvReader#parse(Reader)}, until a full row is assembled, and returns it — or
     * returns {@code null} once the underlying reader is exhausted and no partial row remains
     * to flush.
     */
    private List<String> advance() {
        if (exhausted) {
            return null;
        }
        try {
            int ch;
            while ((ch = reader.read()) != -1) {
                char c = (char) ch;

                if (inQuotes) {
                    if (c == '"') {
                        int next = peekNext();
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
                        int next = peekNext();
                        if (next == '\n') {
                            reader.read();
                        }
                        return completeRow();
                    } else if (c == '\n') {
                        return completeRow();
                    } else {
                        field.append(c);
                        fieldStarted = true;
                        rowHasContent = true;
                    }
                }
            }
        } catch (IOException e) {
            throw new CsvReader.UncheckedCsvException(e);
        }

        // Underlying reader is exhausted.
        exhausted = true;
        if (inQuotes) {
            throw new CsvFormatException("Unterminated quoted field at end of input");
        }
        // Flush the final field/row if the input didn't end with a line terminator.
        if (fieldStarted || field.length() > 0 || rowHasContent || !currentRow.isEmpty()) {
            return completeRow();
        }
        return null;
    }

    /** Finalizes the current field into the current row, then returns it and resets row state. */
    private List<String> completeRow() {
        currentRow.add(field.toString());
        field.setLength(0);
        fieldStarted = false;
        rowHasContent = false;
        List<String> completed = currentRow;
        currentRow = new ArrayList<>();
        return completed;
    }

    /** Peeks at the next character without consuming it, using mark/reset. */
    private int peekNext() throws IOException {
        reader.mark(1);
        int next = reader.read();
        reader.reset();
        return next;
    }
}
