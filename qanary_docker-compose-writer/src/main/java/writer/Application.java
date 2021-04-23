package writer;

import java.nio.file.Paths;
import writer.except.DockerComposeWriterException;

public class Application {

    public static void main(String[] args) throws DockerComposeWriterException {
        String abs = Paths.get("../").toAbsolutePath().normalize().toString();
        String config = "/config.properties";
        String components = abs;
        DockerComposeWriter dockerComposeWriter = new DockerComposeWriter(config, components);
        dockerComposeWriter.writeComposeFile();
    }
}
