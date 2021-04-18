package writer;

import writer.except.DockerComposeWriterException;

public class Application {

    public static void main(String[] args) throws DockerComposeWriterException {
        DockerComposeWriter dockerComposeWriter = new DockerComposeWriter("/config.properties", "../../../../../");
        // TODO: set path to classpath?
        dockerComposeWriter.writeComposeFile();
    }
}
