package eu.wdaqua.qanary.component.simplerealnameofsuperhero.qb;

import eu.wdaqua.qanary.commons.QanaryExceptionNoOrMultipleQuestions;
import eu.wdaqua.qanary.commons.QanaryMessage;
import eu.wdaqua.qanary.commons.QanaryQuestion;
import eu.wdaqua.qanary.commons.QanaryUtils;
import eu.wdaqua.qanary.commons.triplestoreconnectors.QanaryTripleStoreConnector;
import eu.wdaqua.qanary.component.QanaryComponent;
import eu.wdaqua.qanary.exceptions.SparqlQueryFailed;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URISyntaxException;

@Component
/**
 * This component connected automatically to the Qanary pipeline. The Qanary
 * pipeline endpoint defined in application.properties (spring.boot.admin.url).
 * This component is a trivial demo component building a DBpedia query for
 * retrieving the real name of a superhero character (DBpedia resource)
 * identified by a previous component
 *
 * @see <a href=
 *      "https://github.com/WDAqua/Qanary/wiki/How-do-I-integrate-a-new-component-in-Qanary%3F"
 *      target="_top">GitHub wiki howto</a>
 *
 * @see <a href=
 *      "https://github.com/WDAqua/Qanary-question-answering-components/qanary_component-QB-SimpleRealNameOfSuperHero"
 *      target="_top">Intention and usage of this component</a>
 */
public class QueryBuilderSimpleRealNameOfSuperHero extends QanaryComponent {
	private static final Logger logger = LoggerFactory.getLogger(QueryBuilderSimpleRealNameOfSuperHero.class);

	private final String applicationName;

	private String supportedQuestionPrefix = "What is the real name of ";

	public QueryBuilderSimpleRealNameOfSuperHero(@Value("${spring.application.name}") String applicationName) {
		this.applicationName = applicationName;
		logger.info("application name: {}", this.getApplicationName());
	}

	public String getApplicationName() {
		return this.applicationName;
	}

	/**
	 * implement this method encapsulating the functionality of your Qanary
	 * component
	 *
	 * @throws Exception
	 */
	@Override
	public QanaryMessage process(QanaryMessage myQanaryMessage) throws Exception {
		logger.info("process: {}", myQanaryMessage);

		// STEP 1: get the required data
		QanaryUtils myQanaryUtils = this.getUtils(myQanaryMessage);
		QanaryQuestion<String> myQanaryQuestion = this.getQanaryQuestion(myQanaryMessage);
		String myQuestion = myQanaryQuestion.getTextualRepresentation();
		QanaryTripleStoreConnector triplestoreConnector = myQanaryUtils.getQanaryTripleStoreConnector();

		// STEP 2: compute new knowledge about the given question
		// in this simple case we check if the question starts with the phrase that is
		// supported by this simple query builder
		// if the question does not start with the support phrase, then nothing needs to
		// be done.
		if (!isQuestionSupported(myQuestion)) {
			logger.info("nothing to do here as question \"{}\" is not starting with \"{}\".", //
					myQuestion, supportedQuestionPrefix);
			return myQanaryMessage;
		}

		// the SPARQL query to get the annotations of named entities created by another
		// component
		String sparqlGetAnnotation = "" //
				+ "PREFIX dbr: <http://dbpedia.org/resource/> " //
				+ "PREFIX oa: <http://www.w3.org/ns/openannotation/core/> " //
				+ "PREFIX qa: <http://www.wdaqua.eu/qa#> " //
				+ "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> " //
				+ "SELECT * " //
				+ "FROM <" + myQanaryMessage.getInGraph().toString() + "> " //
				+ "WHERE { " //
				+ "    ?annotation     oa:hasBody   ?dbpediaResource ." //
				+ "    ?annotation     qa:score     ?annotationScore ." //
				+ "    ?annotation     oa:hasTarget ?target ." //
				+ "    ?target     oa:hasSource    <" + myQanaryQuestion.getUri().toString() + "> ." //
				+ "    ?target     oa:hasSelector  ?textSelector ." //
				+ "    ?textSelector   rdf:type    oa:TextPositionSelector ." //
				+ "    ?textSelector   oa:start    ?start ." //
				+ "    ?textSelector   oa:end      ?end ." //
				+ "    FILTER(?start = " + supportedQuestionPrefix.length() + ") ." //
				+ "}";

		ResultSet resultset = triplestoreConnector.select(sparqlGetAnnotation);

		while (resultset.hasNext()) {
			QuerySolution tupel = resultset.next();
			int start = tupel.get("start").asLiteral().getInt();
			int end = tupel.get("end").asLiteral().getInt();
			String dbpediaResource = tupel.get("dbpediaResource").toString();
			logger.warn("found matching resource <{}> at ({},{})", dbpediaResource, start, end);

			// create the DBpedia SPARQL select query to compute the answer
			String createdDBpediaQuery = getDBpediaQuery(dbpediaResource);

			// store the created SPARQL select query (which should compute the answer) into
			// the Qanary triplestore
			String insertDataIntoQanaryTriplestoreQuery = getInsertQuery(myQanaryMessage, myQanaryQuestion,
					createdDBpediaQuery);

			// STEP 3: Store new information in the Qanary triplestore
			logger.info("The answer might be computed via: \n{}", createdDBpediaQuery);
			triplestoreConnector.update(insertDataIntoQanaryTriplestoreQuery);
		}

		return myQanaryMessage;
	}

	public boolean isQuestionSupported(String myQuestion) {
		return myQuestion.startsWith(supportedQuestionPrefix);
	}

	/**
	 * returns the SPARQL SELECT query that could retrieve the correct answer (real
	 * name of a superhero in DBpedia)
	 * 
	 * @param dbpediaResource
	 * @return
	 */
	public String getDBpediaQuery(String dbpediaResource) {
		return "" //
				+ "PREFIX dbr: <http://dbpedia.org/resource/> \n" //
				+ "PREFIX dct: <http://purl.org/dc/terms/> \n" //
				+ "PREFIX foaf: <http://xmlns.com/foaf/0.1/> \n" //
				+ "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n" //
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> \n" //
				+ "SELECT * WHERE { \n" //
				+ "  ?resource foaf:name ?answer . \n" // real name of superhero
				+ "  ?resource rdfs:label ?label . \n" // get the character name of the superhero
				+ "  FILTER(LANG(?label) = \"en\") . \n" // only English names
				+ "  ?resource dct:subject dbr:Category:Superheroes_with_alter_egos . \n" // only superheros
				+ "  FILTER(! strStarts(LCASE(?label), LCASE(?answer))). \n" // filter starting with the same name
				+ "  VALUES ?resource { <" + dbpediaResource + "> } . \n" // only for this specific resource
				+ "} \n" //
				+ "ORDER BY ?resource";
	}

	/**
	 * returns a SPARQL INSERT query creating a new annotation that is adding the
	 * SPARQL query to the Qanary triplestore
	 * 
	 * @param myQanaryMessage
	 * @param myQanaryQuestion
	 * @param createdDBpediaQuery
	 * @return
	 * @throws SparqlQueryFailed
	 * @throws URISyntaxException
	 * @throws QanaryExceptionNoOrMultipleQuestions
	 */
	public String getInsertQuery(QanaryMessage myQanaryMessage, QanaryQuestion<String> myQanaryQuestion,
			String createdDBpediaQuery)
			throws SparqlQueryFailed, URISyntaxException, QanaryExceptionNoOrMultipleQuestions {
		return "" //
				+ "PREFIX dbr: <http://dbpedia.org/resource/>" //
				+ "PREFIX oa: <http://www.w3.org/ns/openannotation/core/>" //
				+ "PREFIX qa: <http://www.wdaqua.eu/qa#>" //
				+ "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>" //
				+ "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>" //
				+ "" //
				+ "INSERT { " //
				+ "GRAPH <" + myQanaryMessage.getOutGraph().toString() + ">  {" //
				+ "        ?newAnnotation rdf:type qa:AnnotationOfAnswerSPARQL ." //
				+ "        ?newAnnotation oa:hasTarget <" + myQanaryQuestion.getUri().toString() + "> ." //
				+ "        ?newAnnotation oa:hasBody \""
				+ createdDBpediaQuery.replace("\"", "\\\"").replace("\n", "\\n") + "\"^^xsd:string ." //
				// as it is rule based, a high confidence is expressed
				+ "        ?newAnnotation qa:score \"1.0\"^^xsd:float ."
				+ "        ?newAnnotation oa:annotatedAt ?time ." //
				+ "        ?newAnnotation oa:annotatedBy <urn:qanary:" + this.getApplicationName() + "> ." //
				+ "    }" //
				+ "}" //
				+ "WHERE {" //
				+ "    BIND (IRI(str(RAND())) AS ?newAnnotation) ." // a new ID
				+ "    BIND (now() as ?time) . " // timestamp
				+ "}";
	}

}
