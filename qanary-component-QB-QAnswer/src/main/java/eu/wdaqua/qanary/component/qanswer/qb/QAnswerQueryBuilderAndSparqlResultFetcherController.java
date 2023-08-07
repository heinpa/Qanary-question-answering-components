package eu.wdaqua.qanary.component.qanswer.qb;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import eu.wdaqua.qanary.commons.QanaryExceptionNoOrMultipleQuestions;
import eu.wdaqua.qanary.component.qanswer.qb.messages.QAnswerQanaryWrapperResult;
import eu.wdaqua.qanary.component.qanswer.qb.messages.QAnswerRequest;
import eu.wdaqua.qanary.component.qanswer.qb.messages.QAnswerResult;
import eu.wdaqua.qanary.exceptions.SparqlQueryFailed;
import io.swagger.v3.oas.annotations.Operation;

/**
 * controller to fetch the results from QAnswer. It includes a cache if
 * configured in the environment, e.g., via the application.properties (see the
 * Qanary wiki).
 *
 * @author anbo
 */
@Controller
public class QAnswerQueryBuilderAndSparqlResultFetcherController {
	protected static final String DEMO = "/demo";
	protected static final String API = "/api";
	private static final Logger logger = LoggerFactory
			.getLogger(QAnswerQueryBuilderAndSparqlResultFetcherController.class);
	private QAnswerQueryBuilderAndSparqlResultFetcher myQAnswerQueryBuilderAndExecutor;
	private URI endpoint;
	private String langFallback;
	private String knowledgeBaseDefault;
	private String userDefault;

	public QAnswerQueryBuilderAndSparqlResultFetcherController( //
			QAnswerQueryBuilderAndSparqlResultFetcher myQAnswerQueryBuilderAndExecutor, //
			@Qualifier("langDefault") String langDefault, //
			@Qualifier("knowledgeBaseDefault") String knowledgeBaseDefault, //
			@Qualifier("userDefault") String userDefault, //
			@Qualifier("endpointUrl") URI endpoint, //
			@Value("${server.port}") String serverPort, //
			@Value("${springdoc.api-docs.path}") String swaggerApiDocsPath, //
			@Value("${springdoc.swagger-ui.path}") String swaggerUiPath //
	) {
		this.myQAnswerQueryBuilderAndExecutor = myQAnswerQueryBuilderAndExecutor;
		this.endpoint = endpoint;
		this.langFallback = langDefault;
		this.knowledgeBaseDefault = knowledgeBaseDefault;
		this.userDefault = userDefault;

		logger.info("Service API docs available at http://0.0.0.0:{}{}", serverPort, swaggerApiDocsPath);
		logger.info("Service API docs UI available at http://0.0.0.0:{}{}", serverPort, swaggerUiPath);
	}

	/**
	 * POST interface for requesting the data
	 *
	 * <pre>
	 	{
	 		"qanswerEndpointUrl": "https://qanswer-core1.univ-st-etienne.fr/api/qa/full",
	 		"questionString": "What is the capital of Germany?",
	 		"lang": "en",
	 		"knowledgeBaseId": "wikidata"
	 	}
	 * </pre>
	 * 
	 * OR
	 *
	 * <pre>
	 	{
	 		"question": "What is the capital of Germany?"
	 	}
	 * </pre>
	 * 
	 * OR
	 *
	 * <pre>
	 	{
	 		"question": "population of http://www.wikidata.org/entity/Q142"
	 	}
	 * </pre>
	 *
	 * @param request
	 * @return
	 * @throws URISyntaxException
	 * @throws NoLiteralFieldFoundException
	 * @throws MalformedURLException
	 */

	@PostMapping(value = QAnswerQueryBuilderAndSparqlResultFetcherController.API, produces = "application/json")
	@ResponseBody
	@Operation(summary = "Send a request to the QAnswer API and return the (cached) result here", //
			operationId = "requestQAnswerWebService", //
			description = "Only the question parameter is required. " //
					+ "Examples: \"What is the capital of Germany?\",  " //
					+ "\"What is the capital of http://www.wikidata.org/entity/Q183?\", " //
					+ "\"Person born in France\", " //
					+ "\"Person born in http://www.wikidata.org/entity/Q142?\", " //
					+ "\"Is Berlin the capital of Germany\" " //
	)
	public QAnswerResult requestQAnswerWebService(@RequestBody QAnswerRequest request)
			throws URISyntaxException, MalformedURLException {
		logger.info("requestQAnswerWebService: {} ", request);
		request.replaceNullValuesWithDefaultValues(this.getEndpoint(), this.getLangFallback(),
				this.getKnowledgeBaseDefault(), this.getUserDefault());
		QAnswerResult result = myQAnswerQueryBuilderAndExecutor.requestQAnswerWebService( //
				request.getQanswerEndpointUrl(), //
				request.getQuestion(), //
				request.getLanguage(), //
				request.getKnowledgeBaseId(), //
				request.getUser() //
		);
		logger.info("processed question: {}", result.getQuestion());
		return result;
	}

	/**
	 * wrapper for call to business logics
	 *
	 * @param qanaryApiUri
	 * @param questionString
	 * @param lang
	 * @param knowledgeBaseId
	 * @param user
	 * @return
	 * @throws MalformedURLException
	 * @throws URISyntaxException
	 */
	protected QAnswerResult requestQAnswerWebService(URI qanaryApiUri, String questionString, String lang,
			String knowledgeBaseId, String user) throws MalformedURLException, URISyntaxException {
		return myQAnswerQueryBuilderAndExecutor.requestQAnswerWebService(qanaryApiUri, questionString, lang,
				knowledgeBaseId, user);
	}

// TODO: change implementation or remove feature!
//		this does not work with the current implementation of getSparqlInsertQuery,
//		because it relies on an instance of QanaryQuestion, 
//		which is not created outside of a pipeline context!
//	@PostMapping(value = QAnswerQueryBuilderAndSparqlResultFetcherController.DEMO, produces = "application/json")
//	@ResponseBody
//	@Operation(summary = "Send a request to the QAnswer API and create the component's SPARQL query", //
//			operationId = "requestQAnswerWebService", //
//			description = "Only the question parameter is required. " //
//					+ "Examples: \"What is the capital of Germany?\",  " //
//					+ "\"What is the capital of http://www.wikidata.org/entity/Q183?\", " //
//					+ "\"Person born in France\", " //
//					+ "\"Person born in http://www.wikidata.org/entity/Q142?\", " //
//					+ "\"Is Berlin the capital of Germany\" " //
//	)
//	public QAnswerQanaryWrapperResult requestDemoResultWebService(@RequestBody QAnswerRequest request)
//			throws URISyntaxException, MalformedURLException, QanaryExceptionNoOrMultipleQuestions, SparqlQueryFailed {
//		logger.info("requestDemoResultWebService: {} ", request);
//		request.replaceNullValuesWithDefaultValues(this.getEndpoint(), this.getLangFallback(),
//				this.getKnowledgeBaseDefault(), this.getUserDefault());
//		QAnswerResult result = this.requestQAnswerWebService(request);
//		String sparqQuery = myQAnswerQueryBuilderAndExecutor.getSparqlInsertQuery(endpoint, result);
//		logger.info("received sparqQuery: {}", sparqQuery);
//		return new QAnswerQanaryWrapperResult(result, sparqQuery);
//	}
	
	

	public URI getEndpoint() {
		return endpoint;
	}

	public String getLangFallback() {
		return langFallback;
	}

	public String getKnowledgeBaseDefault() {
		return knowledgeBaseDefault;
	}

	public String getUserDefault() {
		return userDefault;
	}

}
