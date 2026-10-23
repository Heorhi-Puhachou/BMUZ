package io.github.heorhipuhachou.bmuz.exchange;

public class ByTxtFormatException extends RuntimeException {

    public ByTxtFormatException(int lineNumber, String message) {
        super("BY.txt line " + lineNumber + ": " + message);
    }
}
