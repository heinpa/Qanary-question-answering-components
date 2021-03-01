package writer.helpers;

import writer.ComponentInformation;
import writer.except.DockerComposeWriterException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

public class PythonServiceHelper implements ServiceHelper {

    private final String directory;
    private final String confPath;

    /**
     * @param directory the top-level component directory, containing app.conf
     */
    public PythonServiceHelper(String directory) {
        this.directory = directory;
        this.confPath = directory+"/app.conf";
    }

    /**
     * Read app.conf of a Qanary component implemented in Python to extract parameters that are required for
     * its docker-compose service section.
     *
     * @return the minimal component information required to create a docker-compose service section.
     * @throws DockerComposeWriterException
     */
    public ComponentInformation getServiceConfiguration() throws DockerComposeWriterException {
        File configurationFile = new File(this.confPath);
        Properties properties = new Properties();

        try (FileReader reader = new FileReader(configurationFile)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new DockerComposeWriterException("The provided file "+this.confPath+" could not be read", e);
        }

        String name = properties.getProperty("servicename"); // used for service name and image
        String version = properties.getProperty("serviceversion");

        return new ComponentInformation(name, version, name);
    }
}
