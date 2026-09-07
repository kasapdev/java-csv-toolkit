package dev.kasapdev.csvtoolkit;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

public final class CsvStreamReaderTest {

    public static void main(String[] args) {
        testStreamMatchesParseForSimpleRows();
        testStreamHandlesQuotedFieldWithComma();
        testStreamHandlesEscapedQuoteInsideQuotedField();
        testStreamHandlesEmbeddedNewlineUnix();
        testStreamHandlesEmbeddedNewlineWindows();
        testStreamHandlesBlankLineInMiddleOfDocument();
        testStreamHandlesTrailingNewlineDoesNotProduceExtraRow();
        testStreamHandlesNoTrailingNewline();
        testStreamEmptyDocumentProducesNoRows();
        testStreamUnterminatedQuoteThrows();
        testHasNextIsIdempotentUntilConsumed();
        testNextThrowsAfterExhausted();
        testIterableUsableDirectlyInForEachLoop();
        testNonMarkSupportingReaderIsAutoWrapped();
        testGenuineStreamingDoesNotConsumeWholeDocumentUpfront();
        testLargeDocumentStreamingMatchesWholeFileParseExactly();

        TestKit.finish();
    }

    private static void testStreamMatchesParseForSimpleRows() {
        String csv = "a,b\nc,d\ne,f";
        List<List<String>> viaParse = CsvReader.parse(csv);
        List<List<String>> viaStream = collect(CsvReader.stream(new StringReader(csv)));
        TestKit.check("streaming reader matches whole-file parse for simple rows", viaStream.equals(viaParse));
    }

    private static void testStreamHandlesQuotedFieldWithComma() {
        String csv = "\"a,b\",c";
        List<List<String>> rows = collect(CsvReader.stream(new StringReader(csv)));
        TestKit.check("streamed row with quoted comma field parsed", rows.size() == 1);
        TestKit.check("streamed quoted field retains embedded comma", rows.get(0).get(0).equals("a,b"));
        TestKit.check("streamed second field parsed correctly", rows.get(0).get(1).equals("c"));
    }

    private static void testStreamHandlesEscapedQuoteInsideQuotedField() {
        String csv = "\"say \"\"hi\"\"\",b";
        List<List<String>> rows = collect(CsvReader.stream(new StringReader(csv)));
        TestKit.check("streamed escaped double quotes decoded to single quotes",
                rows.get(0).get(0).equals("say \"hi\""));
    }

    private static void testStreamHandlesEmbeddedNewlineUnix() {
        String csv = "\"line1\nline2\",b\nc,d";
        List<List<String>> rows = collect(CsvReader.stream(new StringReader(csv)));
        TestKit.check("streamed: two logical rows despite embedded newline", rows.size() == 2);
        TestKit.check("streamed embedded \\n preserved inside quoted field",
                rows.get(0).get(0).equals("line1\nline2"));
        TestKit.check("streamed second row parsed correctly", rows.get(1).equals(List.of("c", "d")));
    }

    private static void testStreamHandlesEmbeddedNewlineWindows() {
        String csv = "\"line1\r\nline2\",b\r\nc,d";
        List<List<String>> rows = collect(CsvReader.stream(new StringReader(csv)));
        TestKit.check("streamed: two logical rows despite embedded CRLF", rows.size() == 2);
        TestKit.check("streamed embedded \\r\\n preserved inside quoted field",
                rows.get(0).get(0).equals("line1\r\nline2"));
        TestKit.check("streamed row separator CRLF correctly consumed as one terminator",
                rows.get(1).equals(List.of("c", "d")));
    }

    private static void testStreamHandlesBlankLineInMiddleOfDocument() {
        List<List<String>> rows = collect(CsvReader.stream(new StringReader("a,b\n\nc,d")));
        TestKit.check("streamed blank line produces three rows total", rows.size() == 3);
        TestKit.check("streamed blank line parses as a single row with one empty field",
                rows.get(1).equals(List.of("")));
    }

    private static void testStreamHandlesTrailingNewlineDoesNotProduceExtraRow() {
        List<List<String>> rows = collect(CsvReader.stream(new StringReader("a,b\nc,d\n")));
        TestKit.check("streamed trailing newline does not create a phantom row", rows.size() == 2);
    }

    private static void testStreamHandlesNoTrailingNewline() {
        List<List<String>> rows = collect(CsvReader.stream(new StringReader("a,b\nc,d")));
        TestKit.check("streamed last row without trailing newline is still captured", rows.size() == 2);
        TestKit.check("streamed last row content correct", rows.get(1).equals(List.of("c", "d")));
    }

    private static void testStreamEmptyDocumentProducesNoRows() {
        List<List<String>> rows = collect(CsvReader.stream(new StringReader("")));
        TestKit.check("streaming an empty document produces zero rows", rows.isEmpty());
    }

    private static void testStreamUnterminatedQuoteThrows() {
        boolean threw;
        try {
            collect(CsvReader.stream(new StringReader("\"unterminated,field")));
            threw = false;
        } catch (CsvFormatException e) {
            threw = true;
        }
        TestKit.check("streaming an unterminated quoted field throws CsvFormatException", threw);
    }

    private static void testHasNextIsIdempotentUntilConsumed() {
        CsvStreamReader stream = CsvReader.stream(new StringReader("a,b\nc,d"));
        boolean first = stream.hasNext();
        boolean second = stream.hasNext();
        List<String> row = stream.next();
        TestKit.check("hasNext() can be called repeatedly without advancing", first && second);
        TestKit.check("next() after repeated hasNext() still returns the correct first row",
                row.equals(List.of("a", "b")));
        TestKit.check("hasNext() true for second row", stream.hasNext());
        TestKit.check("next() returns second row", stream.next().equals(List.of("c", "d")));
        TestKit.check("hasNext() false once document is exhausted", !stream.hasNext());
    }

    private static void testNextThrowsAfterExhausted() {
        CsvStreamReader stream = CsvReader.stream(new StringReader("a"));
        stream.next();
        boolean threw;
        try {
            stream.next();
            threw = false;
        } catch (NoSuchElementException e) {
            threw = true;
        }
        TestKit.check("next() throws NoSuchElementException once the document is exhausted", threw);
    }

    private static void testIterableUsableDirectlyInForEachLoop() {
        List<List<String>> collected = new ArrayList<>();
        for (List<String> row : CsvReader.stream(new StringReader("a,b\nc,d\ne,f"))) {
            collected.add(row);
        }
        TestKit.check("CsvStreamReader can be used directly as the target of a for-each loop",
                collected.equals(List.of(List.of("a", "b"), List.of("c", "d"), List.of("e", "f"))));
    }

    private static void testNonMarkSupportingReaderIsAutoWrapped() {
        Reader noMarkSupport = new Reader() {
            private final StringReader delegate = new StringReader("a,\"b,c\"\nd,e");

            @Override
            public int read(char[] cbuf, int off, int len) throws IOException {
                return delegate.read(cbuf, off, len);
            }

            @Override
            public boolean markSupported() {
                return false;
            }

            @Override
            public void close() {
                delegate.close();
            }
        };
        List<List<String>> rows = collect(new CsvStreamReader(noMarkSupport));
        TestKit.check("a reader that doesn't support mark/reset is auto-wrapped and still parses correctly",
                rows.equals(List.of(List.of("a", "b,c"), List.of("d", "e"))));
    }

    /**
     * Proves that {@link CsvStreamReader} genuinely streams rather than buffering the whole
     * document: it wraps a mark-capable {@link Reader} that tracks exactly how many characters
     * have actually been consumed (accounting for mark/reset lookahead), then asserts that after
     * pulling just the first row, only a small prefix of a much larger document has been read.
     */
    private static void testGenuineStreamingDoesNotConsumeWholeDocumentUpfront() {
        StringBuilder csv = new StringBuilder();
        for (int i = 0; i < 5_000; i++) {
            if (i > 0) {
                csv.append('\n');
            }
            csv.append("field-a-").append(i).append(",field-b-").append(i).append(",field-c-").append(i);
        }
        String fullDocument = csv.toString();
        TrackingReader tracking = new TrackingReader(fullDocument);

        CsvStreamReader stream = new CsvStreamReader(tracking);
        List<String> firstRow = stream.next();

        TestKit.check("first streamed row is correct", firstRow.equals(List.of("field-a-0", "field-b-0", "field-c-0")));
        TestKit.check("reading only the first row consumed a small prefix of the document, not the whole thing",
                tracking.position() < fullDocument.length() / 10);
        TestKit.check("reading only the first row consumed at least its own characters",
                tracking.position() >= firstRow.get(0).length());
    }

    /**
     * Generates a ~50,000-row CSV (mixing plain fields, comma-containing quoted fields,
     * newline-containing quoted fields, and escaped-quote fields), writes it to a real temp
     * file, then reads it back two ways — once through {@link CsvReader#parse(Reader)} (whole
     * file into memory) and once through {@link CsvStreamReader} driven off a fresh
     * {@link FileReader} on the same file — and asserts the two results are identical
     * row-for-row. This is the correctness-parity check between the batch and streaming APIs,
     * exercised over real file I/O rather than a pre-loaded String.
     */
    private static void testLargeDocumentStreamingMatchesWholeFileParseExactly() {
        List<List<String>> model = generateLargeModel(50_000);
        String csv = CsvWriter.write(model);

        File tempFile;
        try {
            tempFile = File.createTempFile("csv-stream-toolkit-test", ".csv");
            tempFile.deleteOnExit();
            try (FileWriter writer = new FileWriter(tempFile)) {
                writer.write(csv);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write temp CSV file for streaming test", e);
        }

        List<List<String>> viaWholeFileParse;
        List<List<String>> viaStreaming = new ArrayList<>();
        try {
            try (FileReader wholeFileReader = new FileReader(tempFile)) {
                viaWholeFileParse = CsvReader.parse(wholeFileReader);
            }
            try (FileReader streamingFileReader = new FileReader(tempFile);
                 CsvStreamReader stream = CsvReader.stream(streamingFileReader)) {
                for (List<String> row : stream) {
                    viaStreaming.add(row);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read temp CSV file for streaming test", e);
        } finally {
            tempFile.delete();
        }

        TestKit.check("large-document streaming read has the same row count as whole-file parse (50000 rows)",
                viaStreaming.size() == 50_000 && viaWholeFileParse.size() == 50_000);
        TestKit.check("large-document streaming read matches whole-file parse row-for-row",
                viaStreaming.equals(viaWholeFileParse));
        TestKit.check("large-document streaming read matches the original in-memory model exactly",
                viaStreaming.equals(model));
    }

    private static List<List<String>> generateLargeModel(int rowCount) {
        List<List<String>> model = new ArrayList<>(rowCount);
        for (int i = 0; i < rowCount; i++) {
            String col2;
            if (i % 7 == 0) {
                col2 = "multi\nline value " + i;
            } else if (i % 5 == 0) {
                col2 = "has,a,comma " + i;
            } else if (i % 11 == 0) {
                col2 = "says \"hello\" " + i;
            } else if (i % 13 == 0) {
                col2 = "windows\r\nline " + i;
            } else {
                col2 = "plain-value-" + i;
            }
            model.add(List.of("id-" + i, col2, "trailing-col-" + i));
        }
        return model;
    }

    private static List<List<String>> collect(CsvStreamReader stream) {
        List<List<String>> rows = new ArrayList<>();
        while (stream.hasNext()) {
            rows.add(stream.next());
        }
        return rows;
    }

    /**
     * A mark-capable {@link Reader} over an in-memory {@code String} that records exactly how
     * far it has actually advanced through the content (net of any mark/reset lookahead), so
     * tests can assert precisely how many characters a caller consumed.
     */
    private static final class TrackingReader extends Reader {
        private final String content;
        private int pos = 0;
        private int markPos = -1;

        TrackingReader(String content) {
            this.content = content;
        }

        @Override
        public int read(char[] cbuf, int off, int len) {
            if (pos >= content.length()) {
                return -1;
            }
            int n = Math.min(len, content.length() - pos);
            content.getChars(pos, pos + n, cbuf, off);
            pos += n;
            return n;
        }

        @Override
        public boolean markSupported() {
            return true;
        }

        @Override
        public void mark(int readAheadLimit) {
            markPos = pos;
        }

        @Override
        public void reset() {
            if (markPos < 0) {
                throw new IllegalStateException("mark not set");
            }
            pos = markPos;
        }

        @Override
        public void close() {
            // no-op
        }

        int position() {
            return pos;
        }
    }
}
