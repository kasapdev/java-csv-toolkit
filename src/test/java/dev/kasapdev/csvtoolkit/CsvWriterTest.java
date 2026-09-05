package dev.kasapdev.csvtoolkit;

import java.util.ArrayList;
import java.util.List;

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

        TestKit.finish();
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
