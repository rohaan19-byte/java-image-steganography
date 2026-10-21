/**
 * Exception thrown when the payload to embed exceeds the maximum usable capacity of the cover image.
 */
public class InsufficientCapacityException extends IllegalArgumentException {
    private final long requestedBytes;
    private final long maxUsableBytes;

    public InsufficientCapacityException(long requestedBytes, long maxUsableBytes) {
        super(String.format("Payload size (%d bytes) exceeds maximum image capacity (%d bytes).",
                requestedBytes, maxUsableBytes));
        this.requestedBytes = requestedBytes;
        this.maxUsableBytes = maxUsableBytes;
    }

    public long getRequestedBytes() {
        return requestedBytes;
    }

    public long getMaxUsableBytes() {
        return maxUsableBytes;
    }
}
