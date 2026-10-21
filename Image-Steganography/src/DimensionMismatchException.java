/**
 * Exception thrown when comparing two images whose dimensions (width or height) do not match.
 */
public class DimensionMismatchException extends IllegalArgumentException {
    private final int width1;
    private final int height1;
    private final int width2;
    private final int height2;

    public DimensionMismatchException(int width1, int height1, int width2, int height2) {
        super(String.format("Image dimensions do not match: cover image is %dx%d, but stego image is %dx%d.",
                width1, height1, width2, height2));
        this.width1 = width1;
        this.height1 = height1;
        this.width2 = width2;
        this.height2 = height2;
    }

    public int getWidth1() {
        return width1;
    }

    public int getHeight1() {
        return height1;
    }

    public int getWidth2() {
        return width2;
    }

    public int getHeight2() {
        return height2;
    }
}
