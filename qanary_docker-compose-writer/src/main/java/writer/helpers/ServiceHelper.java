package writer.helpers;

import writer.ServiceInformation;
import writer.except.DockerComposeWriterException;

public abstract class ServiceHelper {

    public abstract ServiceInformation getServiceConfiguration() throws DockerComposeWriterException;

    // represents the standard service section
    // todo: validate if this is ok as a standard
    public String getServiceSectionAsString(ServiceInformation information) {
        // minimal section
        String section = "" +
                "  "+ information.getServiceName() + ":\n" +
                "    image: " + information.getImagePrefix() + information.getServiceName() + ":" + information.getServiceVersion() + "\n";

        // if additional environment variables are required
        if (information.requiresEnvironment()) {
            String environment = "    environment: \n";
            if (information.getPort() != null) {
                environment += "      - \"SERVER_PORT="+information.getPort()+"\"\n";
            }
            if (information.getPipelineEndpoint() != null) {
                environment += "      - \"SPRING_BOOT_ADMIN_URL="+information.getPipelineEndpoint()+"\"\n";
            }
            // append to section
            section += environment;
        }
        return section;
    }
}
