package writer;

public class Application {

    public static void main(String[] args) {
        DockerComposeWriter dockerComposeWriter = new DockerComposeWriter();
        dockerComposeWriter.createDockerComposeFile();
    }
}
