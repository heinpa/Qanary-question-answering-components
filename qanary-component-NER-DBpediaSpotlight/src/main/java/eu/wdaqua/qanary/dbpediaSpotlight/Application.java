package eu.wdaqua.qanary.dbpediaSpotlight;

import com.google.gson.JsonArray;
import eu.wdaqua.qanary.component.QanaryComponent;
import eu.wdaqua.qanary.exceptions.DBpediaSpotlightServiceNotAvailable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * Created by didier on 27.03.16.
 */
@SpringBootApplication
@EnableAutoConfiguration
@ComponentScan("eu.wdaqua.qanary.component")
public class Application {
	private static final Logger logger = LoggerFactory.getLogger(Application.class);

	/**
	 * this method is needed to make the QanaryComponent in this project known to
	 * the QanaryServiceController in the qanary_component-template
	 */
	@Bean
	public QanaryComponent qanaryComponent( //
			@Value("${spring.application.name}") final String applicationName, //
			DBpediaSpotlightConfiguration dBpediaSpotlightConfiguration, //
			DBpediaSpotlightServiceFetcher dBpediaSpotlightServiceFetcher //
	) {
		return new DBpediaSpotlightNER(applicationName, dBpediaSpotlightConfiguration, dBpediaSpotlightServiceFetcher);
	}

	@Bean
	public DBpediaSpotlightConfiguration dBpediaSpotlightConfiguration( // 
			@Value("${dbpediaspotlight.test-question}") String testQuestion, // 
			@Value("${dbpediaspotlight.confidence.minimum}") float confidenceMinimum, // 
			@Value("${dbpediaspotlight.endpoint:https://api.dbpedia-spotlight.org/en/annotate}") String endpoint, // 
			DBpediaSpotlightServiceFetcher dBpediaSpotlightServiceFetcher //
	) throws DBpediaSpotlightServiceNotAvailable {
		this.checkSpotlightServiceAvailability(testQuestion, endpoint, confidenceMinimum,
				dBpediaSpotlightServiceFetcher);
		logger.debug("endpoint: {}", endpoint);
		return new DBpediaSpotlightConfiguration(confidenceMinimum, endpoint);
	}

	@Bean
	DBpediaSpotlightServiceFetcher dBpediaSpotlightServiceFetcher() {
		return new DBpediaSpotlightServiceFetcher();
	}

	private void checkSpotlightServiceAvailability(String testQuestion, String endpoint, float confidenceMinimum,
			DBpediaSpotlightServiceFetcher dBpediaSpotlightServiceFetcher) throws DBpediaSpotlightServiceNotAvailable {
		String err;
		try {
			JsonArray response = dBpediaSpotlightServiceFetcher.getJsonFromService(testQuestion, endpoint,
					confidenceMinimum);
			return;
		} catch (Exception e) {
			err = e.getLocalizedMessage();
		}
		throw new DBpediaSpotlightServiceNotAvailable("No response from endpoint " + endpoint + "!\n" + err);
	}

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}