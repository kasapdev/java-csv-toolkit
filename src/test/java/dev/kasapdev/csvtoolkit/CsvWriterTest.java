package dev.kasapdev.csvtoolkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CsvWriterTest {

    public static void main(String[] args) {
        testPlainFieldNotQuoted();
        testFieldWithCommaIsQuoted();
        testFieldWithQuoteIsQuotedAndEscaped();
        testFieldWithNewlineIsQuoted();
        testNullFieldTreatedAsEmpty();
        testWriteMultipleRowsUsesCrlf();
        testRoundTripWithCommasQuotesAndEmbeddedNewlines();
        testRoundTripSimpleTable();
        testWritingEmptyRowListProducesEmptyString();
        testWritingRowWithZeroFieldsProducesJustTerminator();
        testWriteWithHeaderBasic();
        testWriteWithHeaderMissingKeyWritesEmptyField();
        testWriteWithHeaderIgnoresKeysNotInHeader();
        testWriteWithHeaderThenParseWithHeaderRoundTrips();

        TestKit.finish();
    }

    private static void testWriteWithHeaderBasic() {
        List<String> header = List.of("name", "age");
        List<Map<String, String>> records = List.of(Map.of("name", "Alice", "age", "30"));
        String csv = CsvWriter.writeWithHeader(header, records);
        TestKit.check("header row is written first, followed by one row per record",
                csv.equals("name,age\r\nAlice,30\r\n"));
    }

    private static void testWriteWithHeaderMissingKeyWritesEmptyField() {
        List<String> header = List.of("a", "b", "c");
        List<Map<String, String>> records = List.of(Map.of("a", "1", "c", "3"));
        String csv = CsvWriter.writeWithHeader(header, records);
        TestKit.check("a record missing a header key writes an empty field in that column",
                csv.equals("a,b,c\r\n1,,3\r\n"));
    }

    private static void testWriteWithHeaderIgnoresKeysNotInHeader() {
        List<String> header = List.of("a", "b");
        List<Map<String, String>> records = List.of(Map.of("a", "1", "b", "2", "z", "ignored"));
        String csv = CsvWriter.writeWithHeader(header, records);
        TestKit.check("a record key absent from the header is ignored rather than appended",
                csv.equals("a,b\r\n1,2\r\n"));
    }

    private static void testWriteWithHeaderThenParseWithHeaderRoundTrips() {
        List<String> header = List.of("Name", "Notes");
        List<Map<String, String>> original = List.of(
                Map.of("Name", "Ada, Lovelace", "Notes", "Said \"hello, world\"\nnew line too"));

        String csv = CsvWriter.writeWithHeader(header, original);
        List<Map<String, String>> parsedBack = CsvReader.parseWithHeader(csv);

        TestKit.check("header round trip preserves commas, quotes, and embedded newlines",
                parsedBack.equals(original));
    }

    private static void testWritingEmptyRowListProducesEmptyString() {
        TestKit.check("writing an empty list of rows produces an empty string", CsvWriter.write(List.of()).equals(""));
    }

    private static void testWritingRowWithZeroFieldsProducesJustTerminator() {
        // A row with zero fields writes as just the line terminator (no field content at all).
        // Note this is not perfectly symmetric with CsvReader: reading "\r\n" back parses it as
        // one row containing a single empty field, since the reader always emits at least one
        // field per non-empty line. That is CsvReader's own documented flush behavior, not a
        // defect in either component considered alone.
        List<List<String>> rows = new ArrayList<>();
        rows.add(new ArrayList<>());
        String csv = CsvWriter.write(rows);
        TestKit.check("a row with zero fields writes as just the CRLF terminator", csv.equals("\r\n"));
    }

    private static void testPlainFieldNotQuoted() {
        TestKit.check("plain field is not quoted", CsvWriter.escapeField("hello").equals("hello"));
    }

    private static void testFieldWithCommaIsQuoted() {
        TestKit.check("field with comma is quoted", CsvWriter.escapeField("a,b").equals("\"a,b\""));
    }

    private static void testFieldWithQuoteIsQuotedAndEscaped() {
        TestKit.check("field with quote is quoted and internal quote doubled",
                CsvWriter.escapeField("say \"hi\"").equals("\"say \"\"hi\"\"\""));
    }

    private static void testFieldWithNewlineIsQuoted() {
        TestKit.check("field with newline is quoted", CsvWriter.escapeField("line1\nline2").equals("\"line1\nline2\""));
        TestKit.check("field with carriage return is quoted", CsvWriter.escapeField("a\rb").equals("\"a\rb\""));
    }

    private static void testNullFieldTreatedAsEmpty() {
        TestKit.check("null field serializes to empty string", CsvWriter.escapeField(null).equals(""));
    }

    private static void testWriteMultipleRowsUsesCrlf() {
        List<List<String>> rows = List.of(List.of("a", "b"), List.of("c", "d"));
        String csv = CsvWriter.write(rows);
        TestKit.check("output uses CRLF row terminators", csv.equals("a,b\r\nc,d\r\n"));
    }

    private static void testRoundTripWithCommasQuotesAndEmbeddedNewlines() {
        List<List<String>> original = new ArrayList<>();
        original.add(List.of("Name", "Notes", "Score"));
        original.add(List.of("Ada, Lovelace", "Said \"hello, world\"", "100"));
        original.add(List.of("Grace", "Line one\nLine two\nLine three", "95"));
        original.add(List.of("Edge\rCase", "Trailing quote at end\"", "0"));
        original.add(List.of("", "empty first field above", "-1"));

        String csv = CsvWriter.write(original);
        List<List<String>> parsedBack = CsvReader.parse(csv);

        TestKit.check("round trip preserves row count", parsedBack.size() == original.size());
        TestKit.check("round trip preserves exact data including commas/quotes/newlines",
                parsedBack.equals(original));
    }

    private static void testRoundTripSimpleTable() {
        List<List<String>> original = List.of(
                List.of("id", "name", "active"),
                List.of("1", "Alice", "true"),
                List.of("2", "Bob", "false")
        );
        String csv = CsvWriter.write(original);
        List<List<String>> parsedBack = CsvReader.parse(csv);
        TestKit.check("simple table round trip matches exactly", parsedBack.equals(original));
    }
}
