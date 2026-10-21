/**
 * Exception thrown when reading an image that lacks a valid steganography framing header,
 * contains an unrecognized magic header, or has a corrupted/excessive length field.
 */
public class InvalidStegoHeaderException extends IllegalArgumentException {
    public InvalidStegoHeaderException(String message) {
        super(message);
    }
}
