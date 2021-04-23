package writer;

import org.apache.commons.io.FileUtils;

import writer.except.DockerComposeWriterException;
import writer.helpers.JavaServiceHelper;
import writer.helpers.PythonServiceHelper;
import writer.helpers.ServiceHelper;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class DockerComposeWriter {

    private static final Logger logger = Logger.getLogger("DockerComposeWriter");

    private final String dockerComposePath;
    private final String rootPath;
    private Long basePort;
    private final boolean includePipelineService;
    private final boolean includeConfigUiService;
    private final String excludedDirectories;
    private final String imagePrefix;

    private final String pipelineImage;
    private final String pipelineHost;
    private final String pipelinePort;
    private final String configUiImage;

    public DockerComposeWriter(String configFile, String rootPath) throws DockerComposeWriterException {
        try {
            logger.log(Level.INFO, "configuration file: {0}", configFile);

            // read the configuration file
            InputStream inputStream = this.getClass().getResourceAsStream(configFile);
            assert inputStream != null;
            // load the properties for the docker-compose writer
            Properties properties = new Properties();
            properties.load(inputStream);

            this.dockerComposePath = "docker-compose.yml";
            this.rootPath = rootPath;
            this.basePort = Long.parseLong(properties.getProperty("basePort"));
            this.includePipelineService = Boolean.parseBoolean(properties.getProperty("includePipelineService"));
            this.pipelineImage = properties.getProperty("pipelineImage");
            this.pipelineHost = properties.getProperty("pipelineHost");
            this.pipelinePort = properties.getProperty("pipelinePort");
            this.includeConfigUiService = Boolean.parseBoolean(properties.getProperty("includeConfigUiService"));
            this.configUiImage = properties.getProperty("configUiImage");
            this.imagePrefix = properties.getProperty("imagePrefix");
            this.excludedDirectories = properties.getProperty("excludedDirectories");

        } catch (IOException e) {
            throw new DockerComposeWriterException("Writer could not be configured", e);
        }
    }

    /**
     * Create a section for a qa pipeline with specifications from the configuration file.
     *
     * @return docker-compose service section for qa pipeline
     */
    public String getPipelineSection() {
        return "  pipeline:\n" +
                "    image: " + this.pipelineImage + "\n" +
                "    ports:\n" +
                "      - \"" + this.pipelinePort + ":" + this.pipelinePort + "\"\n" +
                "    environment:\n" +
                "      - \"SERVER_PORT=" + this.pipelinePort + "\"\n" +
                "    restart: unless-stopped\n";
    }

    /**
     * Create a section for the configuration ui with specifications from the configuration file.
     *
     * @return docker-compose service section for configuration ui
     */
    public String getConfigUiSection() {
        return "  config-ui:\n" +
                "    image: " + this.configUiImage + "\n" +
                "    ports:\n" +
                "      - \"" + this.basePort + ":5000\"\n" +
                "    environment:\n" +
                "      - \"REACT_APP_HOST=" + this.pipelineHost + "\"\n" +
                "      - \"REACT_APP_PORT=" + this.pipelinePort + "\"\n" +
                "    restart: unless-stopped\n";
    }

    /**
     * Create or override the specified docker-compose file with minimal configuration: <br>
     * - version <br>
     * - service section heading <br>
     * - pipeline section if specified <br>
     * - configuration ui section is specified <br>
     *
     * @throws DockerComposeWriterException
     */
    private void createDockerComposeFile() throws DockerComposeWriterException {
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

    /**
     * Retrieve the section (String) for a specific component.
     *
     * @param serviceHelper containing appropriate methods for the component type (e.g. Java)
     * @param port port on which the component will be published
     * @param imagePrefix docker hub user prefix
     * @param pipelineHost host of the qa pipeline service
     * @param pipelinePort port of the qa pipeline service
     * @return docker-compose service section
     */
    public String getServiceSection(ServiceHelper serviceHelper, Long port, String imagePrefix, String pipelineHost, String pipelinePort) {
        try {
            // let service helper extract component-specific minimal configuration
            ComponentInformation service = serviceHelper.getServiceConfiguration();
            // set custom information
            service.setPort(port);
            service.setImagePrefix(imagePrefix);
            String pipelineEndpoint = pipelineHost + ":" + pipelinePort;
            service.setPipelineEndpoint(pipelineEndpoint);
            return serviceHelper.getServiceSectionAsString(service);
        } catch (DockerComposeWriterException e) {
            logger.log(Level.WARNING, "could not find the specified configuration for component directory", e);
        }
        // if something went wrong don't write to docker-compose file
        return "";
    }

    /**
     * Initiate the writing process of the docker-compose file: Create the file, iterate over the directories and
     * populate the file with the appropriate service sections based on configurations made in `config.properties`.
     *
     * @throws DockerComposeWriterException
     */
    public void writeComposeFile() throws DockerComposeWriterException {
        // set up the docker-compose file with minimal configuration
        this.createDockerComposeFile();
        Long newPort = this.basePort;
        // the names of directories to be read
        List<String> directories = this.filterExcludedDirectories(
                this.getAllDirectoriesFromPath(this.rootPath), this.excludedDirectories); 
        // iterate of the subdirectories
        for (String directory : directories) {
            directory = this.rootPath+"/"+directory;
            logger.log(Level.INFO, "Reading directory: {0}", directory);
            String serviceSection = "";
            // determine the component type
            ComponentType directoryType = this.identifyComponentType(directory);
            // extract the relevant component settings based on the component type
            switch (directoryType) {
                case PYTHON:
                    logger.log(Level.INFO, "writing python section for {0}", directory);
                    PythonServiceHelper pythonServiceHelper = new PythonServiceHelper(directory);
                    // create service section for python component
                    serviceSection = this.getServiceSection(pythonServiceHelper, newPort,
                            this.imagePrefix, this.pipelineHost, this.pipelinePort);
                    newPort++;
                    break;
                case JAVA:
                    logger.log(Level.INFO, "writing java section for {0}", directory);
                    JavaServiceHelper javaServiceHelper = new JavaServiceHelper(directory);
                    // create service section for java component
                    serviceSection = this.getServiceSection(javaServiceHelper, newPort,
                            this.imagePrefix, this.pipelineHost, this.pipelinePort);
                    newPort++;
                    break;
                default:
                    // not all subdirectories are components
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

    /**
     * Retrieve a list of all subdirectory names for a given directory.
     *
     * @param path root directory containing subdirectories
     * @return a list or subdirectories
     */
    public List<String> getAllDirectoriesFromPath(String path) {
        File root = new File(path);
        // only list directories, not files
        FilenameFilter filter = (dir, name) -> new File(dir, name).isDirectory();
        List<String> directories = new ArrayList<>(Arrays.asList(root.list(filter)));
        logger.log(Level.INFO, "Root contains {0} directories", directories.size());
        return directories;
    }

    /**
     * Remove names from a list of directories matching a regular expression.
     *
     * @param directories the list of directories to be filtered through
     * @param regex an expression describing names to be excluded
     * @return a filtered list of directory names
     */
    public List<String> filterExcludedDirectories(List<String> directories, String regex) {
        // list directories matching the provided expression
        List<String> excluded = directories.stream().filter(dir -> dir.matches(regex)).collect(Collectors.toList());
        logger.log(Level.INFO, "Excluding {0} directories", excluded.size());
        // create a new list without the excluded directories
        return directories.stream().filter(dir -> !excluded.contains(dir)).collect(Collectors.toList());
    }

    /**
     * Identify the component type (e.g. Java) depending on the directory structure.
     *
     * @param directory the path to the component directory
     * @return the component type assigned to the directory
     */
    public ComponentType identifyComponentType(String directory) throws DockerComposeWriterException {
        File root = new File(directory);
        try {
            // search the directory recursively until it can be identified
            Collection<?> files = FileUtils.listFiles(root, null, true);
            for (Object o : files) {
                File file = (File) o;
                // if the directory contains .java files it is assumed to be a java component
                if (file.getName().matches(".*\\.java$"))
                    return ComponentType.JAVA;
                // if the directory contains .py files it is assumed to be a python component
                else if (file.getName().matches(".*\\.py$"))
                    return ComponentType.PYTHON;
            }
        } catch (Exception e) {
            throw new DockerComposeWriterException("Could not read the provided directory", e);
        }
        // if the directory does not contain identifying elements
        return ComponentType.DEFAULT;
    }
}
