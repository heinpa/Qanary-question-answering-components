package writer.except;

public class DockerComposeWriterException extends Exception {
    public DockerComposeWriterException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public DockerComposeWriterException(String msg) {
        super(msg);
    }
}
