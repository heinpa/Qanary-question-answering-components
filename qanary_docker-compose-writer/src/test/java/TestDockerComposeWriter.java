import org.junit.Assert;
import org.junit.jupiter.api.Test;
import writer.ComponentType;
import writer.except.DockerComposeWriterException;
import writer.helpers.PythonServiceHelper;
import writer.ServiceInformation;
import writer.DockerComposeWriter;

class TestDockerComposeWriter {

    @Test
    void testGetServiceConfigurationFromFile(){
//        PythonServiceHelper writer = new PythonServiceHelper();
//        String filePath = "./qanary_component-Python-QC-EAT-classifier";
//        ServiceInformation information = writer.getServiceConfigurationFromFile(filePath);
//
//        Assert.assertEquals("answer_type_classifier", information.getServiceName());
//        Assert.assertEquals("0.1.0", information.getServiceVersion());
//        Assert.assertEquals("./qanary_component-Python-QC-EAT-classifier", information.getBuildContext());

    }

    @Test
    void testGetServiceConfigurationFromJavaFile(){
//        String filePath = "./qanary_component-NER-TextRazor";
//        // todo: port
//        JavaServiceWriter writer = new JavaServiceWriter(filePath, );
//        // todo: get string section
//
//        Assert.assertEquals("ner-text-razor", information.getServiceName());
//        Assert.assertEquals("2.0.0", information.getServiceVersion());
//        Assert.assertEquals("./qanary_component-NER-TextRazor", information.getBuildContext());

    }

    @Test
    void testCreateSerciveSection() {
//        // TODO: consider set ups
//        DockerComposeWriter dockerComposeWriter = new DockerComposeWriter();
//        ServiceInformation information= new ServiceInformation("test-service", "0.1.0", "./test_service");
//
//        String serviceSection = dockerComposeWriter.createServiceSection(information, 8080);
//        String expected = "" +
//                "  test-service:\n" +
//                "    image: test-service:0.1.0\n" +
//                "    entrypoint: [\"java\", \"-jar\", \"/qanary-service.jar\", \"--server.port=8080\", \"--spring.boot.admin.url=http://0.0.0.0:8080\"]\n" +
//                "    network_mode: host\n";
//
//        Assert.assertEquals(expected, serviceSection);
//
    }

    @Test
    void testGetAllDirectoriesFromPath() throws DockerComposeWriterException {

        DockerComposeWriter writer = new DockerComposeWriter("qanary_docker-compose-writer/src/main/config.properties");
        String filePath = ".";
        String[] directories = writer.getAllDirectoriesFromPath(filePath);
        Assert.assertNotEquals(0, directories.length);

    }

    @Test
    void testIdentityComponentType() throws DockerComposeWriterException {
        DockerComposeWriter writer = new DockerComposeWriter("qanary_docker-compose-writer/src/main/config.properties");
        String pythonComponent = "./qanary_component-Python-QC-EAT-classifier";
        String javaComponent = "./qanary_component-NER-TextRazor";
        String noComponent = "./qanary_docker-compose-writer";

        ComponentType pythonType = writer.identifyComponentType(pythonComponent);
        ComponentType javaType = writer.identifyComponentType(javaComponent);
        ComponentType defaultType = writer.identifyComponentType(noComponent);

        Assert.assertEquals(ComponentType.PYTHON, pythonType);
        Assert.assertEquals(ComponentType.JAVA, javaType);
        // TODO: improve component type identification
        Assert.assertEquals(ComponentType.DEFAULT, defaultType);
    }
}
