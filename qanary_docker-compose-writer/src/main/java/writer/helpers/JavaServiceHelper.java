package writer.helpers;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import writer.ServiceInformation;
import writer.except.DockerComposeWriterException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.FileInputStream;
import java.io.IOException;

public class JavaServiceHelper extends ServiceHelper{

    private final String directory;
    private final String pomPath;

    public JavaServiceHelper(String directory) {
        this.directory = directory;
        this.pomPath = directory+"/pom.xml";
    }

    // get minimal service information with no custom settings
    public ServiceInformation getServiceConfiguration() throws DockerComposeWriterException {
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;

        try {
            builder = builderFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new DockerComposeWriterException("Parser could not be set up", e);
        }
        assert builder != null;

        try {
            Document document = builder.parse(new FileInputStream(this.pomPath));
            Element root = document.getDocumentElement();

            // get version and image name from xml
            String version = this.getVersion(root);
            String dockerImageName = this.getDockerImageName(root);

            // only return the service information
            return new ServiceInformation(dockerImageName, version, this.directory);

        } catch (SAXException | IOException e) {
            throw new DockerComposeWriterException("The provided file "+this.pomPath+" could not be read", e);
        }
    }

    private String getVersion(Element root) throws DockerComposeWriterException {
        String version;
        // get text content of pom version node
        try {
            Node versionNode = root.getElementsByTagName("version").item(0);
            version = versionNode.getTextContent();
        } catch (NullPointerException e) {
            // if pom contains no version node
            throw new DockerComposeWriterException("No version could be found in "+this.pomPath, e);
        }
        // ensure the version is not empty
        if (!version.equals("")) {
            return version;
        } else {
            // if version is emtpy
            throw new DockerComposeWriterException("The Version defined in "+this.pomPath+" is empty");
        }
    }

    private String getDockerImageName(Element root) throws DockerComposeWriterException {
        String imageName;
        // get text content of pom docker.image.name node
        try {
            Node dockerImageNameNode = ((Element)root.getElementsByTagName("properties").item(0)).getElementsByTagName("docker.image.name").item(0);
            imageName= dockerImageNameNode.getTextContent();
        } catch (NullPointerException e) {
            // if pom contains no docker image node
            throw new DockerComposeWriterException("No image name could be found in "+this.pomPath, e);
        }
        if (!imageName.equals("")) {
            return imageName;
        } else {
            // if image name is empty
            throw new DockerComposeWriterException("The image name defined in "+this.pomPath+" is empty");
        }
    }

}
