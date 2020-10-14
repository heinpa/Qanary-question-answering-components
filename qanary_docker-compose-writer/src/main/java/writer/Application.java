package writer;

public class Application {

    public static void main(String[] args) throws DockerComposeWriterException {
        DockerComposeWriter dockerComposeWriter = new DockerComposeWriter(43710);
        dockerComposeWriter.createDockerComposeFile();
    }
}
