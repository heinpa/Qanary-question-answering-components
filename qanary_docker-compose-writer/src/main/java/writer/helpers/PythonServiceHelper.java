package writer.helpers;

import writer.ServiceInformation;
import writer.except.DockerComposeWriterException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

public class PythonServiceHelper extends ServiceHelper {

    private final String directory;
    private final String confPath;

    public PythonServiceHelper(String directory) {
        this.directory = directory;
        this.confPath = directory+"/app.conf";
    }

    public ServiceInformation getServiceConfiguration() throws DockerComposeWriterException {
        // TODO: verify file path as build context -> relative or absolute?
        File configurationFile = new File(this.confPath);
        Properties properties = new Properties();

        try (FileReader reader = new FileReader(configurationFile)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new DockerComposeWriterException("The provided file "+this.confPath+" could not be read", e);
        }

        // TODO: exception handling for property not found
        String name = properties.getProperty("servicename");
        String version = properties.getProperty("serviceversion");

        return new ServiceInformation(name, version, this.directory);
    }
}
