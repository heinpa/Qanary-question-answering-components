package writer;

import writer.except.DockerComposeWriterException;

import javax.xml.parsers.ParserConfigurationException;

public class Application {

    public static void main(String[] args) throws DockerComposeWriterException, ParserConfigurationException {
        DockerComposeWriter dockerComposeWriter = new DockerComposeWriter("qanary_docker-compose-writer/src/main/config.properties");
        dockerComposeWriter.writeComposeFile();
    }
}
