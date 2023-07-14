package eu.wdaqua.qanary.component.ganswerwrapper.qb;

import eu.wdaqua.qanary.commons.QanaryExceptionNoOrMultipleQuestions;
import eu.wdaqua.qanary.commons.QanaryMessage;
import eu.wdaqua.qanary.commons.QanaryQuestion;
import eu.wdaqua.qanary.commons.QanaryUtils;
import eu.wdaqua.qanary.commons.triplestoreconnectors.QanaryTripleStoreConnector;
import eu.wdaqua.qanary.communications.CacheOfRestTemplateResponse;
import eu.wdaqua.qanary.component.QanaryComponent;
import eu.wdaqua.qanary.component.ganswerwrapper.qb.messages.GAnswerRequest;
import eu.wdaqua.qanary.component.ganswerwrapper.qb.messages.GAnswerResult;
import eu.wdaqua.qanary.exceptions.SparqlQueryFailed;
import net.minidev.json.JSONObject;

import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.shiro.util.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
/**
 * This Qanary component fetches the SPARQL query for
 * the enriched question from the GAnswer API
 *
 * This component connected automatically to the Qanary pipeline. The Qanary
 * pipeline endpoint defined in application.properties (spring.boot.admin.url)
 *
 * @see <a href=
 *      "https://github.com/WDAqua/Qanary-question-answering-components/blob/master/qanary-component-QB-TeBaQaWrapper/README.md"
 *      target="_top">README.md</a>
 */ public class GAnswerQueryBuilder extends QanaryComponent {
    private static final Logger logger = LoggerFactory.getLogger(GAnswerQueryBuilder.class);
    private final float threshold;
    private final URI endpoint;
    private final RestTemplate myRestTemplate;
    private final String langDefault;
    private final List<String> supportedLang;
    private final String applicationName;
    private final CacheOfRestTemplateResponse myCacheOfResponses;
    private QanaryUtils myQanaryUtils;

    private String FILENAME_SELECT_ANNOTATION = "/queries/select_annotation.rq";

    public GAnswerQueryBuilder(//
                               float threshold, //
                               @Qualifier("g_answer.langDefault") String langDefault, //
                               @Qualifier("g_answer.endpoint.language.supported") List<String> supportedLang, //
                               @Qualifier("g_answer.endpointUrl") URI endpoint, //
                               @Value("${spring.application.name}") final String applicationName, //
                               RestTemplate restTemplate, //
                               CacheOfRestTemplateResponse myCacheOfResponses //
    ) throws URISyntaxException {

        logger.info("supportedLang: {}", supportedLang);

        assert threshold >= 0 : "threshold has to be >= 0: " + threshold;
        assert !(endpoint == null) : //
                "endpointUrl cannot be null: " + endpoint;
        assert !(langDefault == null || langDefault.trim().isEmpty()) : //
                "langDefault cannot be null or empty: " + langDefault;
        assert (langDefault.length() == 2) : //
                "langDefault is invalid (requires exactly 2 characters, e.g., 'en'), was " + langDefault + " (length=" + langDefault.length() + ")";
        assert !(supportedLang == null || supportedLang.isEmpty()) : //
                "supportedLang cannot be null or empty: " + supportedLang;
        for (int i = 0; i < supportedLang.size(); i++) {
            assert (supportedLang.get(i).length() == 2) : //
                    "supportedLang is invalid (requires exactly 2 characters, e.g., 'en'), was " + supportedLang.get(i) + " (length=" + supportedLang.get(i).length() + ")";
        }

        this.threshold = threshold;
        this.endpoint = endpoint;
        this.langDefault = langDefault;
        this.supportedLang = supportedLang;
        this.myRestTemplate = restTemplate;
        this.applicationName = applicationName;
        this.myCacheOfResponses = myCacheOfResponses;
    }

    public float getThreshold() {
        return threshold;
    }

    public URI getEndpoint() {
        return endpoint;
    }

    public String getLangDefault() {
        return langDefault;
    }

    public List<String> getSupportedLang() {
        return supportedLang;
    }

    /**
     * implement this method encapsulating the functionality of your Qanary
     * component, some helping notes w.r.t. the typical 3 steps of implementing a
     * Qanary component are included in the method (you might remove all of them)
     *
     * @throws SparqlQueryFailed
     */
    @Override
    public QanaryMessage process(QanaryMessage myQanaryMessage) throws Exception {
        logger.info("process: {}", myQanaryMessage);

        myQanaryUtils = this.getUtils(myQanaryMessage);
        // TODO retrieve language from Qanary triplestore via commons method
        String lang = null;

        // STEP 1: get the required data from the Qanary triplestore (the global process
        // memory)
        QanaryQuestion<String> myQanaryQuestion = this.getQanaryQuestion(myQanaryMessage);
        String questionString;

        // added for translation support
        // TODO: look for AnnotationOfQuestionTranslation with @lang
        Map<String, String> questionTranslation = getQuestionTranslation(myQanaryQuestion, myQanaryUtils);
        if (questionTranslation != null) {
            questionString = questionTranslation.get("string");
            lang = questionTranslation.get("lang");
            logger.info("Translation found in triplestore: {}@{}", questionString, lang);
        } else {
            questionString = myQanaryQuestion.getTextualRepresentation();
            logger.info("Using original question string: {}", questionString);
        }

        if (lang == null) {
            lang = langDefault;
        }

        if (isLangSupported(lang) == false) {
            logger.warn("lang ({}) is not supported", lang);
            return myQanaryMessage;
        }

        // STEP 2: enriching of query and fetching data from the gAnswer API
        GAnswerResult result = requestGAnswerWebService(endpoint, questionString, lang);

        if (result == null) {
            logger.error("No result from gAnswer API");
            return myQanaryMessage;
        }

        // STEP 3: add information to Qanary triplestore
        String sparql = getSparqlInsertQuery(myQanaryQuestion, result);
        myQanaryUtils.getQanaryTripleStoreConnector().update(sparql);

        return myQanaryMessage;
    }

    private String loadQueryFromFile(String filename, QuerySolutionMap bindings) throws IOException {
        return QanaryTripleStoreConnector.readFileFromResourcesWithMap(filename, bindings);
    }

    private Map<String, String> getQuestionTranslation(QanaryQuestion qanaryQuestion, QanaryUtils qanaryUtils) {
        try {
            QuerySolutionMap bindingsForSelect = new QuerySolutionMap();
            bindingsForSelect.add("graph", ResourceFactory.createResource(qanaryQuestion.getOutGraph().toASCIIString()));
            bindingsForSelect.add("targetQuestion", ResourceFactory.createResource(qanaryQuestion.getUri().toASCIIString()));

            String sparql = this.loadQueryFromFile(FILENAME_SELECT_ANNOTATION, bindingsForSelect);
            logger.info("SPARQL query: {}", sparql);
            ResultSet resultSet = qanaryUtils.getQanaryTripleStoreConnector().select(sparql);

            if (!resultSet.hasNext()) {
                logger.warn("no matching resource could be found");
            }

            // expecting only one annotated translation
            QuerySolution result = resultSet.next();
            String translatedQuestionString = result.get("translation").asLiteral().getString();
            String translatedQuestionLanguage = result.get("language").asLiteral().getString();

            Map<String, String> questionTranslation = new HashMap<>();
            questionTranslation.put("string", translatedQuestionString);
            questionTranslation.put("lang", translatedQuestionLanguage);

            return questionTranslation;
        } catch (Exception e) {
            logger.warn("Could not get Question Translation:\n{}", e.getMessage());
            return null;
        }
    }

    protected boolean isLangSupported(String lang) {
        for (int i = 0; i < supportedLang.size(); i++) {
            if (supportedLang.get(i).equals(lang)) {
                return true;
            }
        }
        return false;
    }

    protected GAnswerResult requestGAnswerWebService(URI uri, String questionString, String lang) throws URISyntaxException {
        GAnswerRequest gAnswerRequest = new GAnswerRequest(uri, questionString, lang);
        long requestBefore = myCacheOfResponses.getNumberOfExecutedRequests();

        logger.debug("URL: {}", gAnswerRequest.getGAnswerQuestionUrlAsString());
        HttpEntity<JSONObject> response = myRestTemplate.getForEntity(new URI(gAnswerRequest.getGAnswerQuestionUrlAsString()), JSONObject.class);

        Assert.notNull(response);
        Assert.notNull(response.getBody());

        if (myCacheOfResponses.getNumberOfExecutedRequests() - requestBefore == 0) {
            logger.warn("request was cached: {}", gAnswerRequest);
        } else {
            logger.info("request was actually executed: {}", gAnswerRequest);
        }

        if (response.getBody().equals("{}")) {
            return null;
        } else {
            return new GAnswerResult(response.getBody(), gAnswerRequest.getQuestion(), gAnswerRequest.getGAnswerEndpointUrl(), gAnswerRequest.getLanguage());
        }
    }

    private String cleanStringForSparqlQuery(String myString) {
        return myString.replaceAll("\"", "\\\"").replaceAll("\n", "");
    }

    /**
     * creates the SPARQL query for inserting the data into Qanary triplestore
     * <p>
     * the data can be retrieved via SPARQL 1.1 from the Qanary triplestore using
     * QanaryTripleStoreConnector.insertAnnotationOfAnswerSPARQL from qanary.commons
     * which is providing a predefined query template, s.t., the created data is
     * conform with the expectations of other Qanary components
     *
     * @param myQanaryQuestion
     * @param result
     * @return
     * @throws QanaryExceptionNoOrMultipleQuestions
     * @throws URISyntaxException
     * @throws SparqlQueryFailed
     * @throws IOException
     */
    protected String getSparqlInsertQuery(QanaryQuestion<String> myQanaryQuestion, GAnswerResult result) throws QanaryExceptionNoOrMultipleQuestions, URISyntaxException, SparqlQueryFailed, IOException {

        String answerSparql = cleanStringForSparqlQuery(result.getSparql());

        // define here the parameters for the SPARQL INSERT query
        QuerySolutionMap bindings = new QuerySolutionMap();
        // use here the variable names defined in method insertAnnotationOfAnswerSPARQL
        bindings.add("graph", ResourceFactory.createResource(myQanaryQuestion.getOutGraph().toASCIIString()));
        bindings.add("targetQuestion", ResourceFactory.createResource(myQanaryQuestion.getUri().toASCIIString()));
        bindings.add("selectQueryThatShouldComputeTheAnswer", ResourceFactory.createStringLiteral(answerSparql));
        bindings.add("confidence", ResourceFactory.createTypedLiteral(result.getConfidence()));
        bindings.add("application", ResourceFactory.createResource("urn:qanary:" + this.applicationName));

        // get the template of the INSERT query
        String sparql = QanaryTripleStoreConnector.insertAnnotationOfAnswerSPARQL(bindings);
        logger.info("SPARQL insert for adding data to Qanary triplestore: {}", sparql);

        return sparql;
    }
}
