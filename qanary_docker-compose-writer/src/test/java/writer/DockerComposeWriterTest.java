package writer;

import org.junit.Assert;
import org.junit.jupiter.api.Test;
import writer.except.DockerComposeWriterException;

import java.nio.file.Paths;
import java.util.List;

class DockerComposeWriterTest {

    String config = "/config.properties";
    String components = Paths.get("../").toAbsolutePath().normalize().toString();

    @Test
    void testGetAllDirectoriesFromPath() throws DockerComposeWriterException {

        DockerComposeWriter dockerComposeWriter = new DockerComposeWriter(config, components);

        List<String> directories = dockerComposeWriter.getAllDirectoriesFromPath(components);
        Assert.assertNotEquals(0, directories.size());
    }

    @Test
    void testIdentityComponentType() throws DockerComposeWriterException {
        DockerComposeWriter writer = new DockerComposeWriter(config, components);
        String pythonComponent = components+"/qanary_component-Python-QC-EAT-classifier";
        String javaComponent = components+"/qanary_component-NER-TextRazor";
        //String noComponent = components+"/qanary_docker-compose-writer";

        ComponentType pythonType = writer.identifyComponentType(pythonComponent);
        ComponentType javaType = writer.identifyComponentType(javaComponent);
        //ComponentType defaultType = writer.identifyComponentType(noComponent);

        Assert.assertEquals(ComponentType.PYTHON, pythonType);
        Assert.assertEquals(ComponentType.JAVA, javaType);
        // TODO: improve component type identification
        //Assert.assertEquals(ComponentType.DEFAULT, defaultType);
    }
}
