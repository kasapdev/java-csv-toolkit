package dev.kasapdev.csvtoolkit;

import java.util.List;
import java.util.Map;

public final class CsvReaderTest {

    public static void main(String[] args) {
        testSimpleUnquotedFields();
        testQuotedFieldWithComma();
        testEscapedQuoteInsideQuotedField();
        testEmbeddedNewlineInQuotedFieldUnix();
        testEmbeddedNewlineInQuotedFieldWindows();
        testMultipleRows();
        testTrailingNewlineDoesNotProduceExtraRow();
        testNoTrailingNewlineStillParsesLastRow();
        testEmptyFields();
        testEmptyQuotedField();
        testEmptyDocumentProducesNoRows();
        testUnterminatedQuoteThrows();
        testBlankLineInMiddleOfDocument();
        testSingleFieldNoDelimitersAtAll();
        testSingleCommaOnlyDocument();
        testSingleNewlineOnlyDocument();
        testParseWithHeaderBasic();
        testParseWithHeaderPreservesColumnOrder();
        testParseWithHeaderRowWithFewerFieldsOmitsMissingColumns();
        testParseWithHeaderRowWithMoreFieldsDropsExtra();
        testParseWithHeaderOnlyHeaderRowProducesEmptyList();
        testParseWithHeaderEmptyDocumentThrows();
        testParseWithHeaderDuplicateHeaderNameKeepsLastColumn();

        TestKit.finish();
    }

    private static void testParseWithHeaderBasic() {
        List<Map<String, String>> records = CsvReader.parseWithHeader("name,age\nAlice,30\nBob,25");
        TestKit.check("two data rows parsed", records.size() == 2);
        TestKit.check("first record maps header to value", records.get(0).equals(Map.of("name", "Alice", "age", "30")));
        TestKit.check("second record maps header to value", records.get(1).equals(Map.of("name", "Bob", "age", "25")));
    }

    private static void testParseWithHeaderPreservesColumnOrder() {
        List<Map<String, String>> records = CsvReader.parseWithHeader("c,a,b\n1,2,3");
        List<String> keysInOrder = List.copyOf(records.get(0).keySet());
        TestKit.check("record map iterates keys in header column order", keysInOrder.equals(List.of("c", "a", "b")));
    }

    private static void testParseWithHeaderRowWithFewerFieldsOmitsMissingColumns() {
        List<Map<String, String>> records = CsvReader.parseWithHeader("a,b,c\n1,2");
        TestKit.check("row shorter than header omits the unmatched trailing header",
                records.get(0).equals(Map.of("a", "1", "b", "2")));
        TestKit.check("omitted column is absent, not present with a null value",
                !records.get(0).containsKey("c"));
    }

    private static void testParseWithHeaderRowWithMoreFieldsDropsExtra() {
        List<Map<String, String>> records = CsvReader.parseWithHeader("a,b\n1,2,3");
        TestKit.check("row longer than header drops the extra trailing field",
                records.get(0).equals(Map.of("a", "1", "b", "2")));
    }

    private static void testParseWithHeaderOnlyHeaderRowProducesEmptyList() {
        List<Map<String, String>> records = CsvReader.parseWithHeader("a,b,c");
        TestKit.check("a document with only a header row produces zero records", records.isEmpty());
    }

    private static void testParseWithHeaderEmptyDocumentThrows() {
        boolean threw;
        try {
            CsvReader.parseWithHeader("");
            threw = false;
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        TestKit.check("parsing an empty document with header mode throws IllegalArgumentException", threw);
    }

    private static void testParseWithHeaderDuplicateHeaderNameKeepsLastColumn() {
        List<Map<String, String>> records = CsvReader.parseWithHeader("a,a\n1,2");
        TestKit.check("duplicate header name resolves to the rightmost column's value",
                records.get(0).equals(Map.of("a", "2")));
    }

    private static void testBlankLineInMiddleOfDocument() {
        List<List<String>> rows = CsvReader.parse("a,b\n\nc,d");
        TestKit.check("blank line produces three rows total", rows.size() == 3);
        TestKit.check("first row correct", rows.get(0).equals(List.of("a", "b")));
        TestKit.check("blank line parses as a single row with one empty field", rows.get(1).equals(List.of("")));
        TestKit.check("third row correct", rows.get(2).equals(List.of("c", "d")));
    }

    private static void testSingleFieldNoDelimitersAtAll() {
        List<List<String>> rows = CsvReader.parse("hello");
        TestKit.check("a document with no comma or newline parses as one row", rows.size() == 1);
        TestKit.check("that one row has exactly one field", rows.get(0).equals(List.of("hello")));
    }

    private static void testSingleCommaOnlyDocument() {
        List<List<String>> rows = CsvReader.parse(",");
        TestKit.check("a lone comma parses as one row", rows.size() == 1);
        TestKit.check("that row has two empty fields", rows.get(0).equals(List.of("", "")));
    }

    private static void testSingleNewlineOnlyDocument() {
        List<List<String>> rows = CsvReader.parse("\n");
        TestKit.check("a lone newline produces exactly one row, not two", rows.size() == 1);
        TestKit.check("that row has a single empty field", rows.get(0).equals(List.of("")));
    }

    private static void testSimpleUnquotedFields() {
        List<List<String>> rows = CsvReader.parse("a,b,c");
        TestKit.check("single row parsed", rows.size() == 1);
        TestKit.check("simple unquoted fields parsed correctly", rows.get(0).equals(List.of("a", "b", "c")));
    }

    private static void testQuotedFieldWithComma() {
        List<List<String>> rows = CsvReader.parse("\"a,b\",c");
        TestKit.check("row with quoted comma field parsed", rows.size() == 1);
        TestKit.check("quoted field retains embedded comma", rows.get(0).get(0).equals("a,b"));
        TestKit.check("second field parsed correctly", rows.get(0).get(1).equals("c"));
    }

    private static void testEscapedQuoteInsideQuotedField() {
        List<List<String>> rows = CsvReader.parse("\"say \"\"hi\"\"\",b");
        TestKit.check("row with escaped quotes parsed", rows.size() == 1);
        TestKit.check("escaped double quotes decoded to single quotes", rows.get(0).get(0).equals("say \"hi\""));
    }

    private static void testEmbeddedNewlineInQuotedFieldUnix() {
        List<List<String>> rows = CsvReader.parse("\"line1\nline2\",b\nc,d");
        TestKit.check("two logical rows despite embedded newline", rows.size() == 2);
        TestKit.check("embedded \\n preserved inside quoted field", rows.get(0).get(0).equals("line1\nline2"));
        TestKit.check("second row parsed correctly", rows.get(1).equals(List.of("c", "d")));
    }

    private static void testEmbeddedNewlineInQuotedFieldWindows() {
        List<List<String>> rows = CsvReader.parse("\"line1\r\nline2\",b\r\nc,d");
        TestKit.check("two logical rows despite embedded CRLF", rows.size() == 2);
        TestKit.check("embedded \\r\\n preserved inside quoted field", rows.get(0).get(0).equals("line1\r\nline2"));
        TestKit.check("row separator CRLF correctly consumed as one terminator", rows.get(1).equals(List.of("c", "d")));
    }

    private static void testMultipleRows() {
        List<List<String>> rows = CsvReader.parse("a,b\nc,d\ne,f");
        TestKit.check("three rows parsed", rows.size() == 3);
        TestKit.check("row 1 correct", rows.get(0).equals(List.of("a", "b")));
        TestKit.check("row 2 correct", rows.get(1).equals(List.of("c", "d")));
        TestKit.check("row 3 correct", rows.get(2).equals(List.of("e", "f")));
    }

    private static void testTrailingNewlineDoesNotProduceExtraRow() {
        List<List<String>> rows = CsvReader.parse("a,b\nc,d\n");
        TestKit.check("trailing newline does not create a phantom row", rows.size() == 2);
    }

    private static void testNoTrailingNewlineStillParsesLastRow() {
        List<List<String>> rows = CsvReader.parse("a,b\nc,d");
        TestKit.check("last row without trailing newline is still captured", rows.size() == 2);
        TestKit.check("last row content correct", rows.get(1).equals(List.of("c", "d")));
    }

    private static void testEmptyFields() {
        List<List<String>> rows = CsvReader.parse("a,,c");
        TestKit.check("empty middle field preserved", rows.get(0).equals(List.of("a", "", "c")));
    }

    private static void testEmptyQuotedField() {
        List<List<String>> rows = CsvReader.parse("a,\"\",c");
        TestKit.check("empty quoted field parses to empty string", rows.get(0).equals(List.of("a", "", "c")));
    }

    private static void testEmptyDocumentProducesNoRows() {
        List<List<String>> rows = CsvReader.parse("");
        TestKit.check("empty document produces zero rows", rows.isEmpty());
    }

    private static void testUnterminatedQuoteThrows() {
        boolean threw;
        try {
            CsvReader.parse("\"unterminated,field");
            threw = false;
        } catch (CsvFormatException e) {
            threw = true;
        }
        TestKit.check("unterminated quoted field throws CsvFormatException", threw);
    }
}
