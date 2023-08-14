package eu.wdaqua.qanary.component.qanswer.qb;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

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

import eu.wdaqua.qanary.commons.QanaryExceptionNoOrMultipleQuestions;
import eu.wdaqua.qanary.commons.QanaryMessage;
import eu.wdaqua.qanary.commons.QanaryQuestion;
import eu.wdaqua.qanary.commons.QanaryUtils;
import eu.wdaqua.qanary.commons.triplestoreconnectors.QanaryTripleStoreConnector;
import eu.wdaqua.qanary.communications.CacheOfRestTemplateResponse;
import eu.wdaqua.qanary.communications.RestTemplateWithCaching;
import eu.wdaqua.qanary.component.QanaryComponent;
import eu.wdaqua.qanary.component.qanswer.qb.messages.QAnswerRequest;
import eu.wdaqua.qanary.component.qanswer.qb.messages.QAnswerResult;
import eu.wdaqua.qanary.exceptions.SparqlQueryFailed;
import net.minidev.json.JSONObject;

@Component
/**
 * This Qanary component retrieves the Named Entities from the Qanary
 * triplestore, replaces entities by the entity URIs, and fetches the SPARQL
 * query candidates for the (enriched) question from the QAnswer API
 *
 * This component connected automatically to the Qanary pipeline. The Qanary
 * pipeline endpoint defined in application.properties (spring.boot.admin.url)
 */
public class QAnswerQueryBuilderAndSparqlResultFetcher extends QanaryComponent {
    private static final Logger logger = LoggerFactory.getLogger(QAnswerQueryBuilderAndSparqlResultFetcher.class);
    private final String applicationName;
    private QanaryUtils myQanaryUtils;
    private float threshold;
    private URI endpoint;
    private RestTemplateWithCaching myRestTemplate;
    private final CacheOfRestTemplateResponse myCacheOfResponses;
    private String langDefault;
    private final List<String> supportedLang;
    private String knowledgeBaseDefault;
    private String userDefault;
    private final String FILENAME_GET_ANNOTATED_ENTITIES = "/queries/get_annotated_entities.rq";

    public QAnswerQueryBuilderAndSparqlResultFetcher(
            float threshold, //
            @Value("${spring.application.name}") final String applicationName, //
            @Qualifier("langDefault") String langDefault, //
            @Qualifier("supportedLang") ArrayList<String> supportedLang, //
            @Qualifier("knowledgeBaseDefault") String knowledgeBaseDefault, //
            @Qualifier("userDefault") String userDefault, //
            @Qualifier("endpointUrl") URI endpoint, //
            RestTemplateWithCaching restTemplate, //
            CacheOfRestTemplateResponse myCacheOfResponses //
    ) throws URISyntaxException {

        assert threshold >= 0 : "threshold has to be >= 0: " + threshold;
        assert !(endpoint == null) : //
                "endpointUrl cannot be null: " + endpoint;
        assert !(langDefault == null || langDefault.trim().isEmpty()) : //
                "langDefault cannot be null or empty: " + langDefault;
        assert (langDefault.length() == 2) : //
                "langDefault is invalid (requires exactly 2 characters, e.g., 'en'), " //
                        + "was " + langDefault + " (length=" + langDefault.length() + ")";
        assert !(knowledgeBaseDefault == null || knowledgeBaseDefault.trim().isEmpty()) : //
                "knowledgeBaseDefault cannot be null or empty: " + knowledgeBaseDefault;
        assert !(userDefault == null || userDefault.trim().isEmpty()) : //
                "userDefault cannot be null or empty: " + userDefault;

        this.threshold = threshold;
        this.endpoint = endpoint;
        this.langDefault = langDefault;
        this.knowledgeBaseDefault = knowledgeBaseDefault;
        this.userDefault = userDefault;
        this.myRestTemplate = restTemplate;
        this.myCacheOfResponses = myCacheOfResponses;
        this.applicationName = applicationName;
        this.supportedLang = supportedLang;

        QanaryTripleStoreConnector.guardNonEmptyFileFromResources(FILENAME_GET_ANNOTATED_ENTITIES);
        
        logger.debug("RestTemplate: {}", restTemplate);

    }

    public float getThreshold() {
        return threshold;
    }

    public URI getEndpoint() {
        return endpoint;
    }

    /**
     * starts the annotation process
     *
     * @throws SparqlQueryFailed
     */
    @Override
    public QanaryMessage process(QanaryMessage myQanaryMessage) throws Exception {
        logger.info("process: {}", myQanaryMessage);

        // --------------------------------------------------------------------
        // STEP 1: fetch required data from the triplestore
        // --------------------------------------------------------------------

        // typical helpers 		
        myQanaryUtils = this.getUtils();
        QanaryQuestion<String> myQanaryQuestion = this.getQanaryQuestion();

        String lang = null;
        // TODO: I am not sure what the desired functionality for the actual component would be
        // but for my thesis, looking for Question.getLanguage() does *not* provide the desired
        // result:
        //  it returns the *original* language,
        //  but I want to only work with the *translation* language
        // how would this information be passed in a normal usecase?
        //
        // For my thesis it's enough to rely on setting 'langDefault' to the desired target lang
//        try {
//          lang = myQanaryQuestion.getLanguage(); // this does not work 
//          logger.info("Using language: {}", lang);
//        } catch (Exception e) {
//          lang = langDefault;
//          logger.warn("Using langDefault: {}:\n{}", lang, e.getMessage());
//        } 

        // only work with the language specified in configuration
        lang = langDefault;

        if (isLangSupported(lang) == false) {
            logger.warn("lang ({}) is not supported", lang);
            return myQanaryMessage;
        }

        String questionString = "";
        //TODO: consider: this should not be allowed to happen, because it breaks the functionality
        //TODO: BUT if there is no language annotation, the default language is used (en) 
        //-> which would then look for a translation ... 
        //so these two methods should be consolidated in some way 
        try {
          questionString = myQanaryQuestion.getTextualRepresentation(lang);
          logger.info("Using specific textual representation for language {}: {}", lang, questionString);
        } catch (Exception e) {
          logger.warn("Could not retrieve specific textual representation for language {}:\n{}", e.getMessage());
          questionString = myQanaryQuestion.getTextualRepresentation();
        }

        //TODO: where would the values even come from? 
        String knowledgeBaseId = null;
        String user = null;

        if (knowledgeBaseId == null) {
            knowledgeBaseId = knowledgeBaseDefault;
        }

        if (user == null) {
            user = userDefault;
        }

        List<NamedEntity> retrievedNamedEntities = getNamedEntitiesOfQuestion(myQanaryQuestion,
                myQanaryQuestion.getInGraph());

        // --------------------------------------------------------------------
        // STEP 2: compute new knowledge about the given question
        // --------------------------------------------------------------------
        String questionStringWithResources = computeQuestionStringWithReplacedResources(questionString,
                retrievedNamedEntities, threshold);
        
        QAnswerResult result = requestQAnswerWebService(endpoint, questionStringWithResources, lang, knowledgeBaseId, user);

        // --------------------------------------------------------------------
        // STEP 3: store computed knowledge about the given question into the Qanary triplestore 
        // (the global process memory)
        // --------------------------------------------------------------------
        String sparql = getSparqlInsertQuery(myQanaryQuestion, result);
        logger.debug("created SPARQL query: {}", sparql);
        QanaryTripleStoreConnector connector = myQanaryUtils.getQanaryTripleStoreConnector();
        connector.update(sparql);

        return myQanaryMessage;
    }

      protected boolean isLangSupported(String lang) {
          for (int i = 0; i < supportedLang.size(); i++) {
              if (supportedLang.get(i).equals(lang)) {
                  return true;
              }
          }
          return false;
      }

    protected QAnswerResult requestQAnswerWebService(URI uri, String questionString, String lang,
                                                     String knowledgeBaseId, String user) throws URISyntaxException, MalformedURLException {

        QAnswerRequest qAnswerRequest = new QAnswerRequest(uri, questionString, lang, knowledgeBaseId, user);
        long requestBefore = myCacheOfResponses.getNumberOfExecutedRequests();

        logger.debug("URL: {}", qAnswerRequest.getQAnswerQuestionUrlAsString());
        HttpEntity<JSONObject> response = myRestTemplate.getForEntity(
            new URI(qAnswerRequest.getQAnswerQuestionUrlAsString()), JSONObject.class);

        Assert.notNull(response);
        Assert.notNull(response.getBody());

        if (myCacheOfResponses.getNumberOfExecutedRequests() - requestBefore == 0) {
          logger.warn("request was cached: {}", qAnswerRequest);
        } else {
          logger.info("request was actually executed: {}", qAnswerRequest);
        }

        if (response.getBody().equals("{}")) {
          return null;
        } else {
          return new QAnswerResult(
              response.getBody(), qAnswerRequest.getQuestion(), 
              qAnswerRequest.getQanswerEndpointUrl(), qAnswerRequest.getLanguage(),
              qAnswerRequest.getKnowledgeBaseId(), qAnswerRequest.getUser());
        }

    }

    /**
     * computed list of named entities that are already recognized
     *
     * @param myQanaryQuestion
     * @param inGraph
     * @return
     * @throws Exception
     */
    protected List<NamedEntity> getNamedEntitiesOfQuestion(QanaryQuestion<String> myQanaryQuestion, URI inGraph)
            throws Exception {
        LinkedList<NamedEntity> namedEntities = new LinkedList<>();

        QuerySolutionMap bindingsForSelectAnnotations = new QuerySolutionMap();
		bindingsForSelectAnnotations.add("GRAPH", ResourceFactory.createResource(myQanaryQuestion.getOutGraph().toASCIIString()));
		bindingsForSelectAnnotations.add("QUESTION_URI", ResourceFactory.createResource(myQanaryQuestion.getUri().toASCIIString()));

		// get the template of the SELECT query
		String sparqlGetAnnotations = this.loadQueryFromFile(FILENAME_GET_ANNOTATED_ENTITIES, bindingsForSelectAnnotations);
		logger.info("SPARQL query: {}", sparqlGetAnnotations);        
        
        boolean ignored = false;
        Float score;
        int start;
        int end;
        QuerySolution tupel;

        QanaryTripleStoreConnector connector = myQanaryUtils.getQanaryTripleStoreConnector();
        ResultSet resultset = connector.select(sparqlGetAnnotations);
        while (resultset.hasNext()) {
            tupel = resultset.next();
            start = tupel.get("start").asLiteral().getInt();
            end = tupel.get("end").asLiteral().getInt();
            score = null;

            if (tupel.contains("annotationScore")) {
                score = tupel.get("annotationScore").asLiteral().getFloat();
            }
            URI entityResource = new URI(tupel.get("entityResource").asResource().getURI());

            if (score == null || score >= threshold) {
                namedEntities.add(new NamedEntity(entityResource, start, end, score));
                ignored = false;
            } else {
                ignored = true;
            }
            logger.info("found entity in Qanary triplestore: position=({},{}) (score={}>={}) ignored={}", start, end,
                    score, threshold, ignored);
        }

        logger.info("Result list ({} items) of getNamedEntitiesOfQuestion for question \"{}\".", namedEntities.size(),
                myQanaryQuestion.getTextualRepresentation());
        if (namedEntities.size() == 0) {
            logger.warn("no named entities exist for '{}'", myQanaryQuestion.getTextualRepresentation());
        } else {
            for (NamedEntity namedEntity : namedEntities) {
                logger.info("found namedEntity: {}", namedEntity.toString());
            }
        }
        return namedEntities;
    }
    
	private String loadQueryFromFile(String filenameWithRelativePath, QuerySolutionMap bindings) throws IOException  {
		return QanaryTripleStoreConnector.readFileFromResourcesWithMap(filenameWithRelativePath, bindings);
	}    

    /**
     * create a QAnswer-compatible format of the question
     *
     * @param questionString
     * @param retrievedNamedEntities
     * @param threshold
     * @return
     */
    protected String computeQuestionStringWithReplacedResources(String questionString,
                                                                List<NamedEntity> retrievedNamedEntities, float threshold) {
        Collections.reverse(retrievedNamedEntities); // list should contain last found entities first
        String questionStringOriginal = questionString;
        int run = 0;
        String first;
        String second, secondSafe;
        String entity;

        for (NamedEntity myNamedEntity : retrievedNamedEntities) {
            // replace String by URL
            if (myNamedEntity.getScore() >= threshold) {
                first = questionString.substring(0, myNamedEntity.getStartPosition());
                second = questionString.substring(myNamedEntity.getEndPosition());
                entity = questionString.substring(myNamedEntity.getStartPosition(), myNamedEntity.getEndPosition());

                // ensure that the next character in the second part is a whitespace to prevent
                // problems with the inserted URIs
                if (!second.startsWith(" ") && !second.isEmpty()) {
                    secondSafe = " " + second;
                } else {
                    secondSafe = second;
                }
                questionString = first + myNamedEntity.getNamedEntityResource().toASCIIString() + secondSafe;

                logger.debug("{}. replace of '{}' at ({},{}) results in: {}, first:|{}|, second:|{}|", run, entity,
                        myNamedEntity.getStartPosition(), myNamedEntity.getEndPosition(), questionString, first,
                        second);
                run++;
            }
        }
        logger.info("Question original: {}", questionStringOriginal);
        logger.info("Question changed : {}", questionString);

        return questionString;
    }

    private String cleanStringForSparqlQuery(String myString) {
        return myString.replaceAll("\"", "\\\"").replaceAll("\n", "").replaceAll("\t", "");
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
    protected String getSparqlInsertQuery(QanaryQuestion<String> myQanaryQuestion, QAnswerResult result) 
            throws QanaryExceptionNoOrMultipleQuestions, URISyntaxException, SparqlQueryFailed, IOException {

      String query = cleanStringForSparqlQuery(result.getSparql());

      // define here the parameters for the SPARQL INSERT query
      QuerySolutionMap bindings = new QuerySolutionMap();
      // use here the variable names defined in method insertAnnotationOfAnswerSPARQL
      bindings.add("graph", ResourceFactory.createResource(myQanaryQuestion.getOutGraph().toASCIIString()));
      bindings.add("targetQuestion", ResourceFactory.createResource(myQanaryQuestion.getUri().toASCIIString()));
      bindings.add("selectQueryThatShouldComputeTheAnswer", ResourceFactory.createStringLiteral(query));
      bindings.add("confidence", ResourceFactory.createTypedLiteral(result.getConfidence()));
      bindings.add("application", ResourceFactory.createResource("urn:qanary:" + this.applicationName));

      // get the template of the INSERT query
      String sparql = QanaryTripleStoreConnector.insertAnnotationOfAnswerSPARQL(bindings);
      logger.info("SPARQL insert for adding data to Qanary triplestore: {}", sparql);

      return sparql;
    }







// TODO: this method needs to be refactored! 
//      separate AnnotationOfImprovedQuestion from other annotations
//      use commons templates for answer annotations
//    /**
//     * creates the SPARQL query for inserting the data into Qanary triplestore
//     * <p>
//     * data can be retrieved via SPARQL 1.1 from the Qanary triplestore using:
//     *
//     * <pre>
//     *
//     * SELECT * FROM <YOURGRAPHURI> WHERE {
//     * ?s ?p ?o ;
//     * a ?type.
//     * VALUES ?t {
//     * qa:AnnotationOfAnswerSPARQL qa:SparqlQuery
//     * qa:AnnotationOfImprovedQuestion qa:ImprovedQuestion
//     * qa:AnnotationAnswer qa:Answer
//     * qa:AnnotationOfAnswerType qa:AnswerType
//     * }
//     * }
//     * ORDER BY ?type
//     * </pre>
//     *
//     * @param outgraph
//     * @param result
//     * @return
//     * @throws QanaryExceptionNoOrMultipleQuestions
//     * @throws URISyntaxException
//     * @throws SparqlQueryFailed
//     */
//    String getSparqlInsertQuery(URI outgraph, QAnswerResult result)
//            throws QanaryExceptionNoOrMultipleQuestions, URISyntaxException, SparqlQueryFailed {
//
//        // the computed answer's SPARQL query needs to be cleaned
//        String improvedQuestion = cleanStringForSparqlQuery(result.getQuestion());
//
//        int counter = 0; // starts at 0
//        String annotationsToBeInserted = "";
//        String bindForInsert = "";
//        for (String answer : result.getValues()) {
//
//            logger.debug("{}. QAnswer query candidate: {}", counter, answer.toString());
//
//            annotationsToBeInserted += "" //
//                    + "\n" //
//                    + "  ?annotationSPARQL" + counter + " a 	qa:AnnotationOfAnswerSPARQL ; \n" //
//                    + " 		oa:hasTarget    ?question ; \n" //
//                    + " 		oa:hasBody      ?sparql" + counter + " ; \n" //
//                    + " 		oa:annotatedBy  ?service ; \n" //
//                    + " 		oa:annotatedAt  ?time ; \n" //
//                    + " 		qa:score        \"" + answer.get("confidence").getAsDouble() + "\"^^xsd:double . \n" //
//                    //
//                    + "  ?sparql" + counter + " a              qa:SparqlQuery ; \n" //
//                    + "         qa:hasPosition  \"" + counter + "\"^^xsd:nonNegativeInteger ; \n" //
//                    + "         rdf:value       \"\"\"" + answer.get("query").getAsString() + "\"\"\"^^xsd:string . \n"; //
//            bindForInsert += "  BIND (IRI(str(RAND())) AS ?annotationSPARQL" + counter + ") . \n" //
//                    + "  BIND (IRI(str(RAND())) AS ?sparql" + counter + ") . \n"; //
//
//            counter++;
//        }
//
//        String sparql = "" //
//                + "PREFIX qa: <http://www.wdaqua.eu/qa#> \n" //
//                + "PREFIX oa: <http://www.w3.org/ns/openannotation/core/> \n" //
//                + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> \n" //
//                + "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n" //
//                + "INSERT { \n" //
//                + "GRAPH <" + outgraph.toASCIIString() + "> { \n" //
//                + annotationsToBeInserted //
//                //
//                // improved question
//                + "  ?annotationImprovedQuestion  a 	qa:AnnotationOfImprovedQuestion ; \n" //
//                + " 		oa:hasTarget    ?question ; \n" //
//                + " 		oa:hasBody      ?improvedQuestion ; \n" //
//                + " 		oa:annotatedBy  ?service ; \n" //
//                + " 		oa:annotatedAt  ?time ; \n" //
//                + " 		qa:score        ?score . \n" //
//                //
//                + "  ?improvedQuestion a    qa:ImprovedQuestion ; \n " //
//                + "         rdf:value 		?improvedQuestionText . \n " //
//                + "  }\n" // end: GRAPH
//                + "}\n" // end: insert
//                + "WHERE { \n" //
//                + bindForInsert //
//                //
//                + "  BIND (IRI(str(RAND())) AS ?annotationImprovedQuestion) . \n" //
//                + "  BIND (IRI(str(RAND())) AS ?improvedQuestion) . \n" //
//                //
//                + "  BIND (now() AS ?time) . \n" //
//                + "  BIND (<" + outgraph.toASCIIString() + "> AS ?question) . \n" //
//                + "  BIND (<urn:qanary:" + this.applicationName + "> AS ?service ) . \n" //
//                + "  BIND (\"\"\"" + improvedQuestion + "\"\"\"^^xsd:string  AS ?improvedQuestionText ) . \n" //
//                //
//                + "} \n"; // end: where
//
//        logger.debug("SPARQL insert for adding data to Qanary triplestore:\n{}", sparql);
//        return sparql;
//    }

}
