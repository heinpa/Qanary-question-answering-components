package writer.helpers;

import writer.ComponentInformation;
import writer.except.DockerComposeWriterException;

/**
 * The ServiceHelper interface provides the basic functionality required for any helper class.
 * <br>
 * Helper classes are supposed to read certain parameters
 * depending on the individual implementation of a Qanary component
 */
public interface ServiceHelper {

    ComponentInformation getServiceConfiguration() throws DockerComposeWriterException;

    /**
     * Use extracted component information to create the docker-compose service section for a Qanary component.
     *
     * @param componentInformation represents the Qanary component
     * @return the service section for the specific Qanary component do be appended to docker-compose.yml
     */
    default String getServiceSectionAsString(ComponentInformation componentInformation) {
        // minimal attributes
        String section = "" +
                "  "+ componentInformation.getServiceName() + ":\n" +
                "    image: " + componentInformation.getImagePrefix() + componentInformation.getImageName() + ":" + componentInformation.getServiceVersion() + "\n" +
                "    network_mode: host\n" +
                "    ports: \n" +
                "      - \""+ componentInformation.getPort() + "\"\n" + 
                "    restart: unless-stopped\n";

        // if additional environment variables are required
        if (componentInformation.requiresEnvironment()) {
            String environment = "    environment: \n";
            if (componentInformation.getPort() != null) {
                environment += "      - \"SERVER_PORT="+ componentInformation.getPort()+"\"\n";
            }
            if (componentInformation.getPipelineEndpoint() != null) {
                environment += "      - \"SPRING_BOOT_ADMIN_URL="+ componentInformation.getPipelineEndpoint()+"\"\n";
            }
            // append to section
            section += environment;
        }
        return section;
    }
}
