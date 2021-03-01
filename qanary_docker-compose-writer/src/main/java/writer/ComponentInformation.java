package writer;
/**
 * The ComponentInformation class represents a Qanary question answering component
 * with information that is relevant within the context of deployment using docker-compose.
 */
public class ComponentInformation {

    // minimal parameters
    private final String serviceName; // used as image name and section heading
    private final String serviceVersion; // heading of service section
    private final String imageName; // image of the component

    // additional custom information
    private String imagePrefix; // to include images from repositories
    private boolean environment; // if port or pipeline are set
    private Long port; //
    private String pipelineEndpoint; // to override default component spring boot service endpoint
    private String buildContext; // directory containing Dockerfile

    /**
     * @param serviceName the unique name of the Qanary component within the docker-compose context
     * @param serviceVersion the version of the Qanary component
     * @param imageName the docker image of the Qanary component
     */
    public ComponentInformation(String serviceName, String serviceVersion, String imageName){
       this.serviceName = serviceName;
       this.serviceVersion = serviceVersion;
       this.imageName = imageName;

       this.imagePrefix = "";
       this.environment = false;
       this.port = null;
       this.pipelineEndpoint = null;
    }

    public String getServiceVersion() {
        return this.serviceVersion;
    }

    public String getBuildContext() {
        return this.buildContext;
    }

    public String getServiceName() {
        return this.serviceName;
    }

    public String getImageName() {
        return imageName;
    }

    public Long getPort() {
        return this.port;
    }

    public String getImagePrefix() {
        return this.imagePrefix;
    }

    public String getPipelineEndpoint() {
        return this.pipelineEndpoint;
    }

    public void setImagePrefix(String imagePrefix) {
        this.imagePrefix = imagePrefix;
    }

    public void setBuildContext(String buildContext) {
        this.buildContext = buildContext;
    }

    public void setPipelineEndpoint(String pipelineEndpoint) {
        this.environment = true;
        this.pipelineEndpoint = pipelineEndpoint;
    }

    public void setPort(Long port) {
        this.environment = true;
        this.port = port;
    }

    public boolean requiresEnvironment() {
        return this.environment;
    }
}
