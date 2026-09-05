package dev.kasapdev.csvtoolkit;

/**
 * Thrown when malformed CSV input is encountered (e.g. an unterminated quoted field).
 */
public class CsvFormatException extends RuntimeException {
    public CsvFormatException(String message) {
        super(message);
    }
}
