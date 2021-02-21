package writer;

import org.apache.commons.io.FileUtils;
import writer.except.DockerComposeWriterException;
import writer.helpers.JavaServiceHelper;
import writer.helpers.PythonServiceHelper;
import writer.helpers.ServiceHelper;

import java.io.*;
import java.util.Collection;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DockerComposeWriter {

    private static final Logger logger = Logger.getLogger("DockerComposeWriter");

    private String dockerComposePath;
    private Long basePort;
    private boolean includePipelineService;
    private boolean includeConfigUiService;
    private String imagePrefix;

    private String pipelineImage;
    private String pipelineHost;
    private String pipelinePort;
    private String configUiImage;

    public DockerComposeWriter(String configFile) throws DockerComposeWriterException {
        File configuration = new File(configFile);
        this.configureWriter(configuration);
    }

    /**
     * fetches configuration from config.properties
     * <pre>


          </pre>
     * @param configuration
     * @throws DockerComposeWriterException
     */
    private void configureWriter(File configuration) throws DockerComposeWriterException {

        try (FileReader reader = new FileReader(configuration)) {
            Properties properties = new Properties();
            properties.load(reader);

            this.dockerComposePath = "./docker-compose.yml";
            this.basePort = Long.parseLong(properties.getProperty("basePort"));
            this.includePipelineService = Boolean.parseBoolean(properties.getProperty("includePipelineService"));
            this.pipelineImage = properties.getProperty("pipelineImage");
            this.pipelineHost = properties.getProperty("pipelineHost");
            this.pipelinePort = properties.getProperty("pipelinePort");
            this.includeConfigUiService = Boolean.parseBoolean(properties.getProperty("includeConfigUiService"));
            this.configUiImage = properties.getProperty("configUiImage");
            this.imagePrefix = properties.getProperty("imagePrefix");

            logger.log(Level.INFO, "Configuration:{0}\n", properties);

        } catch (IOException e) {
            throw new DockerComposeWriterException("Writer could not be configured", e);
        }
    }

    public String getPipelineSection() {
        return "  pipeline:\n" +
                "    image: " + this.pipelineImage + "\n" +
                "    ports:\n" +
                "      - \"" + this.pipelinePort + "\"\n" +
                "    environment:\n" +
                "      - \"SERVER_PORT=" + this.pipelinePort + "\"\n";
    }

    public String getConfigUiSection() {
        return "  config-ui:\n" +
                "    image: " + this.configUiImage + "\n" +
                "    ports:\n" +
                "      - \"" + this.basePort + ":5000\"\n" +
                "    environment:\n" +
                "      - \"REACT_APP_HOST=webengineering.ins.hs-anhalt.de\"\n" +
                "      - \"REACT_APP_PORT=" + this.pipelinePort + "\"\n";
    }


    /*
    create docker-compose file if it does not already exist
    an existing file will be replaced
     */
    private void createDockerComposeFile() throws DockerComposeWriterException {
        // todo: make pipeline and config ui more configurable?
        try (FileWriter writer = new FileWriter(new File(this.dockerComposePath))) {
            String heading = "" +
                    "version: '3.5'\n" +
                    "services:\n";
            if (this.includePipelineService) {
                heading += this.getPipelineSection();
            }

            if (this.includeConfigUiService) {
                heading += this.getConfigUiSection();
                // increment base port
                this.basePort++;
            }
            writer.write(heading);
            logger.log(Level.INFO, "writing to file {0}", this.dockerComposePath);
        } catch (IOException e) {
            throw new DockerComposeWriterException("please set a valid path for qanaryComponentsPath in config.properties!");
        }
    }

    public String getServiceSection(ServiceHelper serviceHelper, Long port) {
        try {
            ServiceInformation service = serviceHelper.getServiceConfiguration();
            // set custom information
            service.setPort(port);
            service.setImagePrefix(this.imagePrefix);
            String pipelineEndpoint = this.pipelineHost + ":" + this.pipelinePort;
            service.setPipelineEndpoint(pipelineEndpoint);
            return serviceHelper.getServiceSectionAsString(service);
        } catch (DockerComposeWriterException e) {
            logger.log(Level.WARNING, "could not find the specified configuration for component directory");
        }
        return "";
    }

    public void writeComposeFile() throws DockerComposeWriterException {
        this.createDockerComposeFile();
        Long newPort = this.basePort;
        // todo: make a decision what pipeline endpoint to use: defined external or newly included?
        String[] directories = this.getAllDirectoriesFromPath(".");
        for (String directory : directories) {
            logger.log(Level.INFO, "Reading directory: {0}", directory);
            String serviceSection = "";
            ComponentType directoryType = this.identifyComponentType(directory);
            switch (directoryType) {
                case PYTHON:
                    logger.log(Level.INFO, "writing python section for {0}", directory);
                    PythonServiceHelper pythonServiceHelper = new PythonServiceHelper(directory);
                    serviceSection = this.getServiceSection(pythonServiceHelper, newPort);
                    newPort++;
                    break;
                case JAVA:
                    logger.log(Level.INFO, "writing java section for {0}", directory);
                    JavaServiceHelper javaServiceHelper = new JavaServiceHelper(directory);
                    serviceSection = this.getServiceSection(javaServiceHelper, newPort);
                    newPort++;
                    break;
                default:
                    logger.log(Level.INFO, "not a component: {0}", directory);
            }
            this.appendToDockerComposeFile(serviceSection);
        }
    }


    public void appendToDockerComposeFile(String serviceSection) throws DockerComposeWriterException {
        try (FileWriter writer = new FileWriter(this.dockerComposePath, true)) {
            writer.write(serviceSection);
        } catch (IOException e) {
            throw new DockerComposeWriterException("Could not write to docker-compose file", e);
        }
    }

    public String[] getAllDirectoriesFromPath(String path) {
        // TODO: default is '.' -> set?
        File root = new File(path);
        FilenameFilter filter = (dir, name) -> new File(dir, name).isDirectory();
        String[] directories = root.list(filter);
//        assert directories != null;
        // TODO: how to handle null
        // TODO: specify excluded directories (starting with '.')
        logger.log(Level.INFO, "Found {0} directories", directories.length);
        return directories;
    }

    public ComponentType identifyComponentType(String path) {
        File root = new File(path);
        try {
            Collection<?> files = FileUtils.listFiles(root, null, true);
            for (Object o : files) {
                File file = (File) o;
                if (file.getName().equals("pom.xml"))
                    return ComponentType.JAVA;
                else if (file.getName().equals("app.conf"))
                    return ComponentType.PYTHON;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ComponentType.DEFAULT;
    }
}
