package eu.wdaqua.qanary.component.qanswer.qbe;

import eu.wdaqua.qanary.component.QanaryComponentConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

import java.net.URI;
import java.net.URISyntaxException;

@SpringBootApplication
@ComponentScan(basePackages = {"eu.wdaqua.qanary"})
/**
 * basic class for wrapping functionality to a Qanary component note: there is
 * no need to change something here
 */
public class Application {

    @Autowired
    public QanaryComponentConfiguration qanaryComponentConfiguration;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    float threshold(@Value("${qanswer.qbe.namedentities.threshold:0.5}") float threshold) {
        return threshold;
    }

    @Bean(name = "langDefault")
    String langDefault(@Value("${qanswer.endpoint.language.default:en}") String langDefault) {
        return langDefault;
    }

    @Bean(name = "knowledgeBaseDefault")
    String knowledgeBaseDefault(
            @Value("${qanswer.endpoint.knowledgebase.default:wikidata}") String knowledgeBaseDefault) {
        return knowledgeBaseDefault;
    }

    @Bean(name = "userDefault")
    String userDefault(
            @Value("${qanswer.endpoint.user.default:open}") String userDefault) {
        return userDefault;
    }

    @Bean(name = "endpointUrl")
    URI endpointUrl(@Value("${qanswer.endpoint.url}") String endpointUrl) throws URISyntaxException {
        return new URI(endpointUrl);
    }

    @Bean
    public OpenAPI customOpenAPI() {
        String appVersion = getClass().getPackage().getImplementationVersion();
        return new OpenAPI().info(new Info() //
            .title("NED DBpediaSpotlight component") //
            .version(appVersion) //
            .description("This is a sample Foobar server created using springdocs - "
            + "a library for OpenAPI 3 with spring boot.")
            .termsOfService("http://swagger.io/terms/") //
            .license(new License().name("Apache 2.0").url("http://springdoc.org")) //
        );
    }
}
