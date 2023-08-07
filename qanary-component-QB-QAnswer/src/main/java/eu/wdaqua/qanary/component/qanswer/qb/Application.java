package eu.wdaqua.qanary.component.qanswer.qb;

import eu.wdaqua.qanary.communications.CacheOfRestTemplateResponse;
import eu.wdaqua.qanary.communications.RestTemplateWithCaching;
import eu.wdaqua.qanary.component.QanaryComponent;
import eu.wdaqua.qanary.component.QanaryComponentConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.ArrayList;

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

	  @Autowired
	  CacheOfRestTemplateResponse myCacheOfResponses;

    @Autowired
    RestTemplateWithCaching restTemplateWithCaching;

    @Bean(name = "threshold")
    float threshold(@Value("${qanswer.qb.namedentities.threshold:0.5}") float threshold) {
        return threshold;
    }

    @Bean(name = "langDefault")
    String langDefault(@Value("${qanswer.endpoint.language.default:en}") String langDefault) {
        return langDefault;
    }

    @Bean(name = "supportedLang")
    ArrayList<String> supportedLang(@Value("${qanswer.endpoint.language.supported:en}") ArrayList<String> supportedLang) {
        return supportedLang;
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

    /**
    * this method is needed to make the QanaryComponent in this project known
    * to the QanaryServiceController in the qanary_component-template
    * 
    * @return
     * @throws URISyntaxException
    */
    @Bean
    public QanaryComponent qanaryComponent(
        @Qualifier("threshold") float threshold,
        @Value("${spring.application.name}") final String applicationName,
        @Qualifier("langDefault") String langDefault,
        @Qualifier("supportedLang") ArrayList<String> supportedLang, 
        @Qualifier("knowledgeBaseDefault") String knowledgeBaseDefault,
        @Qualifier("userDefault") String userDefault,
        @Qualifier("endpointUrl") URI endpoint,
        RestTemplateWithCaching restTemplate,
        CacheOfRestTemplateResponse cacheOfRestTemplateResponse) throws URISyntaxException {
      return new QAnswerQueryBuilderAndSparqlResultFetcher(
          threshold, 
          applicationName,
          langDefault,
          supportedLang,
          knowledgeBaseDefault,
          userDefault,
          endpoint,
          restTemplate,
          cacheOfRestTemplateResponse
          );
    }
}
