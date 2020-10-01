package writer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class DockerComposeWriter {

    private static Logger logger = Logger.getLogger("DockerComposeWriter");

    private String dockerComposeFilePath;
    private String qanaryProjectPomLocation;
    private String qanaryPath;

    public DockerComposeWriter() {
        try {
            File jarFile = new File(this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            this.qanaryPath = jarFile.getParentFile().getParentFile().getParentFile().getCanonicalPath()+"/";
            this.dockerComposeFilePath = this.qanaryPath+"docker-compose.yml";
            this.qanaryProjectPomLocation = this.qanaryPath+"pom.xml";

        } catch (URISyntaxException | IOException e) {
            e.printStackTrace();
        }
    }

    public void createDockerComposeFile() throws DockerComposeWriterException {
        try {
            File file = new File(this.dockerComposeFilePath);
            if (file.createNewFile()) {
               logger.log(Level.INFO, "created compose file: {0}", this.dockerComposeFilePath);
            } else {
                logger.log(Level.INFO, "file already exists: {0}", this.dockerComposeFilePath);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            FileWriter writer = new FileWriter(this.dockerComposeFilePath);
            String head = "version: '3'\nservices:\n";
            writer.write(head);
            writer.close();

        } catch (IOException e) {
            throw new DockerComposeWriterException(e.getMessage());
        }

        this.iterateComponents();
        logger.log(Level.INFO, "Wrote components to file: {0}", this.dockerComposeFilePath);

    }

    private Map<String, String> addPort(Map<String, String> componentAtrr, String componentPropertiesFilePath) {
        Properties properties = new Properties();

        try {
            properties.load(new FileInputStream(componentPropertiesFilePath));
            String port = properties.get("server.port").toString();
            componentAtrr.put("port", port);
            return componentAtrr;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private String getVersion(Element root) throws DockerComposeWriterVersionException {
        try {
            Node versionNode = root.getElementsByTagName("version").item(0);
            return versionNode.getTextContent();

        } catch (NullPointerException e) {
            throw new DockerComposeWriterVersionException();
        }
    }

    private String getDockerImageName(Element root) throws DockerComposeWriterImageException {
        try {
            Node dockerImageNameNode = ((Element)root.getElementsByTagName("properties").item(0)).getElementsByTagName("docker.image.name").item(0);
            return dockerImageNameNode.getTextContent();

        } catch (NullPointerException e) {
            throw new DockerComposeWriterImageException();
        }
    }

    private Map<String, String> addPropertiesFromPom(Map<String, String> componentAttr, String componentPomFilePath) throws DockerComposeWriterException {
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = null;

        try {
            builder = builderFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new DockerComposeWriterException(e.getMessage());
        }

        try {
            Document document = builder.parse(new FileInputStream(componentPomFilePath));
            Element root = document.getDocumentElement();

            String version;
            String dockerImageName;

            version = this.getVersion(root);
            dockerImageName = this.getDockerImageName(root);

            componentAttr.put("image", dockerImageName);
            componentAttr.put("component-version", version);
            return componentAttr;

        } catch (SAXException | IOException e) {
            throw new DockerComposeWriterException(e.getMessage());
        }
    }

    private Map<String, String> addPropertiesFromExternalResources(Map<String, String> componentAttr, String componentPomFilePath,  String componentPropertiesFilePath) {
        try {
            componentAttr = this.addPropertiesFromPom(componentAttr, componentPomFilePath);
            componentAttr = this.addPort(componentAttr, componentPropertiesFilePath);
            return componentAttr;
        } catch (DockerComposeWriterException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void iterateComponents() throws DockerComposeWriterException {
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;

        try {
            builder = builderFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
            throw new DockerComposeWriterException(e.getMessage());
        }

        try {
            Document document = builder.parse(new FileInputStream(this.qanaryProjectPomLocation));
            Element root = document.getDocumentElement();
            Element profiles = (Element)root.getElementsByTagName("profiles").item(0);
            Element modules = (Element)profiles.getElementsByTagName("modules").item(0);
            NodeList components = modules.getElementsByTagName("module");
            int basePort = 10000;

            for(int i = 0; i < components.getLength(); ++i) {
                String component = components.item(i).getTextContent();
                if (!component.equals("qanary_docker-compose-writer")) {
                    Map<String, String> componentAttr = new HashMap<>();
                    String componentPomFilePath = this.qanaryPath+component+ "/pom.xml";
                    String componentPropertiesFilePath = this.qanaryPath+component+ "/src/main/resources/config/application.properties";

                    if (this.addPropertiesFromExternalResources(componentAttr, componentPomFilePath, componentPropertiesFilePath) != null) {
                        componentAttr.put("newPort", basePort+i+"");
                        this.appendToDockerComposeFile(componentAttr);
                    } else {
                        logger.log(Level.WARNING, "Invalid properties for {0}", component);
                    }
                }
            }
        } catch (IOException | SAXException e) {
            e.printStackTrace();
        }
    }

    private void appendToDockerComposeFile(Map<String, String> componentAttr) {
        try {
            FileWriter writer = new FileWriter(this.dockerComposeFilePath, true);
            String version = componentAttr.get("component-version");
            String image = componentAttr.get("image");
            String port = componentAttr.get("port");
            String newPort = componentAttr.get("newPort");
            String dockerCompose = "" +
                    "  " + image + ":\n" +
                    "    image: " + image + ":" + version + "\n" +
                    "    ports: \n" +
                    "      - \"" + newPort + ":" + port + "\"\n";
            writer.write(dockerCompose);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
