package writer;

// holds information needed for services in docker-compose.yml
public class ServiceInformation {

    // minimal parameters
    private final String serviceName; // used as image name and section heading
    private final String serviceVersion; // used as image tag
    private final String buildContext; // directory containing Dockerfile

    // additional custom information
    private String imagePrefix; // to include images from repositories
    private boolean environment; // if port or pipeline are set
    private Long port; //
    private String pipelineEndpoint; // to override default component spring boot service endpoint

    public ServiceInformation(String serviceName, String serviceVersion, String buildContext){
       this.serviceName = serviceName;
       this.serviceVersion = serviceVersion;
       this.buildContext = buildContext;

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
