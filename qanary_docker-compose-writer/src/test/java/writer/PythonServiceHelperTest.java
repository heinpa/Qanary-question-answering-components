package writer;

import org.junit.jupiter.api.Test;
import writer.helpers.PythonServiceHelper;
import writer.except.DockerComposeWriterException;
import java.nio.file.Paths;

import static org.junit.Assert.*;


class PythonServiceHelperTest {

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
        ComponentInformation service = new ComponentInformation(name, version, name);
        service.setImagePrefix(imagePrefix);
        service.setPipelineEndpoint(pipelineEndpoint);
        service.setPort(port);

        assertNotNull(service.getPort());
        assertNotNull(service.getPipelineEndpoint());
        assertNotNull(service.getImagePrefix());

        // instantiate java writer to create service section
        PythonServiceHelper pythonServiceHelper = new PythonServiceHelper(directory);
        String expected = "" +
                "  "+name + ":\n" +
                "    image: " + imagePrefix + name + ":" + version + "\n" +
                "    ports:\n" +
                "      - \""+port+":"+port+"\"\n" +
                "    restart: unless-stopped\n" +
                "    environment: \n" +
                "      - \"SERVER_PORT="+port+"\"\n" +
                "      - \"SPRING_BOOT_ADMIN_URL="+pipelineEndpoint+"\"\n";

        String actual = pythonServiceHelper.getServiceSectionAsString(service);

        assertEquals(expected,actual);
    }

    @Test
    void testGetServiceCoinfiguration() throws DockerComposeWriterException {

        String abs = Paths.get("../").toAbsolutePath().normalize().toString();
        String pythonDirectory = abs+"/qanary_component-Python-QC-EAT-classifier";

        PythonServiceHelper helper = new PythonServiceHelper(pythonDirectory);
        ComponentInformation information = helper.getServiceConfiguration();

        assertEquals("answer_type_classifier", information.getServiceName());
        assertEquals("answer_type_classifier", information.getImageName());
        assertNotNull(information.getServiceVersion());
    }
}
