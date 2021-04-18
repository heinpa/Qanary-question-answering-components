package writer;

import writer.except.DockerComposeWriterException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import writer.helpers.JavaServiceHelper;

import static org.junit.Assert.*;

class JavaServiceHelperTest {

    @Test
    void testCreateDockerComposeFile() throws DockerComposeWriterException {
        DockerComposeWriter dockerComposeWriter = new DockerComposeWriter("/config.properties", "/home/paul/QAS/Qanary-question-answering-components/");
        dockerComposeWriter.writeComposeFile();
    }

    @Disabled
    @Test
    void testGetServiceSectionAsString() {
        // STANDARD USE CASE

        // given minimal parameters
        String name = "test-service";
        String version = "0.1.0";
        String directory = "./qa-test-service";

        // and custom settings
        Long port = 5050L;
        String imagePrefix = "user/";
        String pipelineEndpoint = "http://localhost:8080";

        // hold the information
        ComponentInformation service = new ComponentInformation(name, version, directory);
        service.setImagePrefix(imagePrefix);
        service.setPipelineEndpoint(pipelineEndpoint);
        service.setPort(port);

        assertNotNull(service.getPort());
        assertNotNull(service.getPipelineEndpoint());
        assertNotNull(service.getImagePrefix());

        // instantiate java writer to create service section
        JavaServiceHelper javaServiceHelper = new JavaServiceHelper(directory);
        String expected = "" +
                "  "+name + ":\n" +
                "    image: " + imagePrefix + name + ":" + version + "\n" +
                "    environment: \n" +
                "      - SERVER_PORT="+port+"\n" +
                "      - SPRING_BOOT_ADMIN_URL="+pipelineEndpoint+"\n";

        String actual = javaServiceHelper.getServiceSectionAsString(service);

        assertEquals(expected,actual);


        // MINIMAL PARAMETERS

        // given minimal parameters
        String name1 = "test-service-1";
        String version1 = "0.2.0";
        String directory1 = "./qa-test-service-1";

        // and no custom settings

        // hold the information
        ComponentInformation service1 = new ComponentInformation(name1, version1, directory1);

        assertNull(service1.getPort());
        assertNull(service1.getPipelineEndpoint());
        assertEquals("", service1.getImagePrefix()); // default is not null!

        // instantiate java writer to create service section
        JavaServiceHelper javaServiceHelper1 = new JavaServiceHelper(directory1);
        String expected1 = "" +
                "  "+name1 + ":\n" +
                "    image: "+ name1 + ":" + version1 + "\n";

        String actual1 = javaServiceHelper1.getServiceSectionAsString(service1);

        assertEquals(expected1,actual1);
    }
}
