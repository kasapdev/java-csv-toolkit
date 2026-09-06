package dev.kasapdev.csvtoolkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An RFC 4180-compliant CSV writer.
 *
 * <p>Serializes rows of fields back into CSV text. A field is quoted whenever it contains a
 * comma, a double quote, or a newline ({@code \n} or {@code \r}); internal double quotes are
 * escaped by doubling them ({@code "} -&gt; {@code ""}). Rows are terminated with {@code \r\n},
 * per RFC 4180.
 */
public final class CsvWriter {

    private CsvWriter() {
    }

    /**
     * Serializes a table of rows into CSV text.
     *
     * @param rows the rows to serialize; each row is a list of field values (nulls are
     *             treated as empty strings)
     * @return the CSV text, with each row terminated by {@code \r\n}
     */
    public static String write(List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(escapeField(row.get(i)));
            }
            sb.append("\r\n");
        }
        return sb.toString();
    }

    /**
     * Serializes a header row followed by one row per record map, matching the header's
     * column order.
     *
     * <p>For each record, every header name is looked up in turn; a header with no
     * corresponding entry in a given record writes as an empty field, the same as a
     * {@code null} value does in {@link #write(List)}. Any entries in a record whose key is
     * not among the headers are ignored.
     *
     * @param header  the column names, written as the first row, in order
     * @param records the data rows, each keyed by (a subset of) the header names
     * @return the CSV text: the header row followed by one row per record, each terminated by
     *         {@code \r\n}
     */
    public static String writeWithHeader(List<String> header, List<Map<String, String>> records) {
        List<List<String>> rows = new ArrayList<>(records.size() + 1);
        rows.add(header);
        for (Map<String, String> record : records) {
            List<String> row = new ArrayList<>(header.size());
            for (String key : header) {
                row.add(record.get(key));
            }
            rows.add(row);
        }
        return write(rows);
    }

    /**
     * Escapes a single field value for safe inclusion in CSV output, quoting it if necessary.
     */
    static String escapeField(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuoting = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!needsQuoting) {
            return value;
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
