package dev.kasapdev.csvtoolkit;

import java.util.List;

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
