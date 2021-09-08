package eu.wdaqua.qanary.resolver;

import java.util.List;
import java.util.LinkedList;

import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

import eu.wdaqua.qanary.commons.QanaryMessage;
import eu.wdaqua.qanary.commons.QanaryQuestion;
import eu.wdaqua.qanary.commons.QanaryUtils;
import eu.wdaqua.qanary.component.QanaryComponent;
import eu.wdaqua.qanary.exceptions.SparqlQueryFailed;


@Component
/**
 * This component connected automatically to the Qanary pipeline.
 * The Qanary pipeline endpoint defined in application.properties (spring.boot.admin.url)
 * @see <a href="https://github.com/WDAqua/Qanary/wiki/How-do-I-integrate-a-new-component-in-Qanary%3F" target="_top">Github wiki howto</a>
 */
public class LocationToGermanDistrict extends QanaryComponent {
	private static final Logger logger = LoggerFactory.getLogger(LocationToGermanDistrict.class);

	private final String applicationName;

	// use German as default languag as this component is supposed to find German districts
	private final String defaultLanguage = "de";

	public LocationToGermanDistrict(
			@Value("${spring.application.name}") final String applicationName) {
		this.applicationName = applicationName;
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

		QanaryUtils myQanaryUtils = this.getUtils(myQanaryMessage);

		// STEP 1: get the required data from the Qanary triplestore (the global process memory)

		// if required, then fetch the origin question (here the question is a
		// textual/String question)
		QanaryQuestion<String> myQanaryQuestion = new QanaryQuestion<String>(myQanaryMessage);
		String questionText = myQanaryQuestion.getTextualRepresentation();

		// TODO: get the question language for this question
		// if none can be found use the default
		String selectLanguage = "" //
				+ "PREFIX oa: <http://www.w3.org/ns/openannotation/core/> " //
				+ "PREFIX qa: <http://www.wdaqua.eu/qa#> " //
				+ "SELECT * " // 
				+ "FROM <" + myQanaryMessage.getInGraph().toString() + "> " // the currently used graph
				+ "WHERE { " //
				+ "	   ?annotation	   a qa:AnnotationOfQuestionLanguage ." // as annotated by LD Shuyo
				+ "    ?annotation     oa:hasBody ?language ." //
				+ "}";

		ResultSet langResultset = myQanaryUtils.selectFromTripleStore(selectLanguage);
		String questionLanguage;
		try {
			QuerySolution tupel = langResultset.next();
			String language = tupel.get("language").toString();
			logger.info("Annotated language: {}", language);
			questionLanguage = language;
		} catch (Exception e) {
			logger.warn("No language annotated, using default: {} \n{}", defaultLanguage, e.getMessage());
			questionLanguage = defaultLanguage;
		}

		// get the named entities annotated for this question
		String selectNamedEntities = "" // 
				+ "PREFIX dbr: <http://dbpedia.org/resource/> " //
				+ "PREFIX oa: <http://www.w3.org/ns/openannotation/core/> " //
				+ "PREFIX qa: <http://www.wdaqua.eu/qa#> " //
				+ "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> " //
				+ "SELECT * " // 
				+ "FROM <" + myQanaryMessage.getInGraph().toString() + "> " // the currently used graph
				+ "WHERE { " //
				+ "    ?annotation     oa:hasBody   ?wikidataResource ." // the entity in question
				+ "    ?annotation     qa:score     ?annotationScore ." //
				+ "    ?annotation     oa:hasTarget ?target ." //
				+ "    ?target     oa:hasSource    <" + myQanaryQuestion.getUri().toString() + "> ." // annotated for the current question
				+ "    ?target     oa:hasSelector  ?textSelector ." //
				+ "    ?textSelector   rdf:type    oa:TextPositionSelector ." //
				+ "    ?textSelector   oa:start    ?start ." //
				+ "    ?textSelector   oa:end      ?end ." //
				+ "}";

//		String selectNamedEntities = "" // define your SPARQL SELECT query here
//				+ "PREFIX oa: <http://www.w3.org/ns/openannotation/core/> " //
//				+ "PREFIX qa: <http://www.wdaqua.eu/qa#> " //
//				+ "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> " //
//				+ "PREFIX dbo: <http://dbpedia.org/ontology/> " //
//				+ "SELECT * " // 
//				+ "FROM <" + myQanaryMessage.getInGraph().toString() + "> " // the currently used graph
//				+ "WHERE { " //
//				+ "    ?annotation	a	qa:AnnotationOfInstanceLocation ." //
//				+ "    ?annotation     oa:hasBody	?body ." // 
//				+ "    ?body qa:hasID ?qid . " // the entity in question
//				+ "    ?body dbo:type ?locationType . " // the entity in question
//				+ "    ?annotation     oa:hasTarget ?target . " // the substring with the entity
//				+ "    ?annotation	qa:hasConfidence ?score . " //
//				+ "}";

		ResultSet resultset = myQanaryUtils.selectFromTripleStore(selectNamedEntities);
		List<FoundEntity> foundEntities = new LinkedList<FoundEntity>();
		while (resultset.hasNext()) {
			QuerySolution tupel = resultset.next();
			String triplestoreId = tupel.get("annotation").toString();
			logger.info("triplestoreId: {}", triplestoreId);

			String wikidataResource = tupel.get("wikidataResource").toString();
			logger.info("resource: {}", wikidataResource);

			float score = Float.parseFloat(tupel.get("annotationScore").toString()
					.substring(0,tupel.get("annotationScore").toString().indexOf("^^"))); //float?
			logger.info("score: {}", score);

			int start =  Integer.parseInt(tupel.get("start").toString()
					.substring(0,tupel.get("start").toString().indexOf("^^")));
			int end =  Integer.parseInt(tupel.get("end").toString()
					.substring(0,tupel.get("end").toString().indexOf("^^")));
			logger.info("triplestoreId: {}", triplestoreId);

			String surfaceForm = questionText.substring(start, end);
			logger.info("surfaceForm: {}", surfaceForm);


			// create an object that holds the entity and later the districts
			FoundEntity foundEntity = new FoundEntity(wikidataResource, surfaceForm, score);
			foundEntity.setTriplestoreId(triplestoreId);
			foundEntities.add(foundEntity);
		}

		// STEP 2: compute new knowledge about the given question
		for (FoundEntity foundEntity : foundEntities) {
			logger.info("find related districts for {}", foundEntity.getSurfaceForm());
			// get a list of related districts
			List<FoundEntity> relatedDistricts = this.findRelatedDistricts(foundEntity, questionLanguage);
			logger.info("found {} related districts", relatedDistricts.size());
			// if the entity is not a district itself then remove it from the triplestore
			if (!this.isDistrictOfGermany(foundEntity)) {
				logger.info("entity is no district, removing ...");
				this.removeEntityFromTriplestore(foundEntity);
			}
			if (relatedDistricts.size() == 1) {
				this.addDistrictToTriplestore(relatedDistricts.get(0), myQanaryMessage, myQanaryUtils, "containing");
			} else {
			// add related districts to the triplestore
				for (FoundEntity relatedDistrict : relatedDistricts) {
					logger.info("adding {} to triplestore", relatedDistrict.getSurfaceForm());
					this.addDistrictToTriplestore(relatedDistrict, myQanaryMessage, myQanaryUtils, "located_in");
				}
			}
		}

		// STEP 3: store computed knowledge about the given question into the Qanary triplestore 
		// (the global process memory)


		return myQanaryMessage;
	}

	public List<FoundEntity> findRelatedDistricts(FoundEntity foundEntity, String questionLanguage) {

		String wikidataResource = foundEntity.getWikidataResource();
		List<FoundEntity> relatedDistricts = new LinkedList<FoundEntity>();
		String query = "" //
			+ "PREFIX wdt: <http://www.wikidata.org/prop/direct/> " //
			+ "PREFIX wd: <http://www.wikidata.org/entity/> " //
			+ "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> " //
			+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " //
			+ "SELECT DISTINCT * WHERE { " //
			+ "  VALUES ?location {  " //
			+ "    <"+wikidataResource+"> " //
			+ "  } " //
			+ "  VALUES ?districtConcept { "// there are two district concepts
			+ "	wd:Q149621 " //
			+ "	wd:Q106658 " //
			+ "  } " //
			+ "  ?location wdt:P131+ ?district . " //
			+ "  ?district wdt:P31/wdt:P279* ?districtConcept . " //
			+ "  ?district rdfs:label ?labelLang . " //
			+ "  ?district wdt:P440 ?key . " //
			+ "  FILTER( LANG(?labelLang) = \""+questionLanguage+"\" ) " // 
			+ "} ";

		String wikidataEndpoint = "https://query.wikidata.org/sparql";
		QueryExecution qexec = QueryExecutionFactory.sparqlService(wikidataEndpoint, query);

		try {
			logger.info("executing query {}", query);
			ResultSet results = qexec.execSelect();
			if (results != null) {
				while (results.hasNext()) {
					QuerySolution tupel = results.next();
					
					String districtWikidataResource = tupel.get("district").toString();
					float score = foundEntity.getScore();
					String surfaceForm = tupel.get("labelLang").toString()
						.substring(0,tupel.get("labelLang").toString().indexOf("@"));
					FoundEntity relatedDistrict = new FoundEntity(districtWikidataResource, surfaceForm, score);
					relatedDistrict.setDistrictKey(this.findDistrictKey(relatedDistrict));
					relatedDistrict.setTargetString(foundEntity.getSurfaceForm()); // use the original found substring (might be required for follow up)
					logger.info("adding {} to relatedDistricts", relatedDistrict.getSurfaceForm());
					relatedDistricts.add(relatedDistrict);
				}
			} else {
				logger.info("no results were found");
			}
		} catch (Exception e) {
			logger.warn("could not query wikidata endpoint ");
			e.printStackTrace();
		} finally {
			qexec.close();
		}
		return relatedDistricts;

	}

	public int findDistrictKey(FoundEntity foundEntity) {
		String wikidataResource = foundEntity.getWikidataResource();
		String wikidataGetQuery = ""
			+ "PREFIX wdt: <http://www.wikidata.org/prop/direct/> " //
			+ "SELECT ?key WHERE { " //
			+ "  <"+wikidataResource+"> wdt:P440 ?key . " //
			+ "} ";

		String wikidataEndpoint = "https://query.wikidata.org/sparql";
		QueryExecution qexec = QueryExecutionFactory.sparqlService(wikidataEndpoint, wikidataGetQuery);
		ResultSet result = qexec.execSelect();
		QuerySolution tupel = result.next();
		int key = Integer.parseInt(tupel.get("key").toString());  // check?
		logger.info("found key: {}", key);
		return key;
	}

	public boolean isDistrictOfGermany (FoundEntity foundEntity) {
		String wikidataResource = foundEntity.getWikidataResource();
		String wikidataAskQuery = ""
			+ "PREFIX wdt: <http://www.wikidata.org/prop/direct/> "
			+ "PREFIX wd: <http://www.wikidata.org/entity/> "
			+ "ASK "
			+ "WHERE { "
			+ "  VALUES ?location {  "
			+ "    <"+wikidataResource+"> "
			+ "  } "
			+ "  VALUES ?districtConcept { "
			+ "    wd:Q106658 " // denotes a district of Germany as opposed to more generic definition
			+ "  } "
			+ "  ?location wdt:P31/wdt:P279* ?districtConcept .  "
			+ "} ";

		String wikidataEndpoint = "https://query.wikidata.org/sparql";
		QueryExecution qexec = QueryExecutionFactory.sparqlService(wikidataEndpoint, wikidataAskQuery);

		try {
			boolean result = qexec.execAsk();
			return result;
		} catch (Exception e) {
			logger.warn("could not query wikidata endpoint {}", e.getMessage());
		} finally {
			qexec.close();
		}
		return false;
	}

	public void addDistrictToTriplestore(
			FoundEntity district,
			QanaryMessage myQanaryMessage,
			QanaryUtils myQanaryUtils,
			String targetRelation) throws SparqlQueryFailed {

		logger.info("store data in graph {} of Qanary triplestore endpoint {}", //
				myQanaryMessage.getValues().get(myQanaryMessage.getOutGraph()), //
				myQanaryMessage.getValues().get(myQanaryMessage.getEndpoint()));
		
		// push data to the Qanary triplestore
		String sparqlUpdateQuery = "" //
				+ "PREFIX oa: <http://www.w3.org/ns/openannotation/core/> " //
				+ "PREFIX qa: <http://www.wdaqua.eu/qa#> " //
				+ "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> " //
				+ "PREFIX dbr: <https://dbpedia.org/resource/> " //
  				+ "PREFIX dbo: <http://dbpedia.org/ontology/> " //
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " //
				+ "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> \n" //
				+ "INSERT { " //
				+ "GRAPH <"+myQanaryMessage.getInGraph().toString()+"> { " //
				+ "  ?a a qa:AnnotationOfInstanceLocation . " //
				+ "  ?a oa:hasBody [ " //
				+ "    dbo:type ?type; "  // 
				+ "    rdfs:label ?label; " // 
				+ "	   qa:hasID ?qid " // 
				+ "  ] . " //
				+ "  ?a qa:hasConfidence ?score . " // 
				+ "  ?a oa:hasTarget [ " //
				+ "    rdfs:label ?target ; " // 
				+ "    qa:targetRelation ?targetRelation " // 
				+ "  ] . " //
				+ "  ?a oa:annotatedBy ?component  . " //
				+ "  ?a oa:annotatedAt ?time . " //
				+ "} " //
				+ "} " //
				+ "WHERE { " // 
				+ "  BIND (IRI(str(RAND())) AS ?a) . " //
				+ "  BIND (dbr:Districts_of_Germany AS ?type) . " //
				+ "  BIND (\""+district.getSurfaceForm()+"\"^^xsd:string AS ?label) . " // TODO: type
				+ "  BIND (\""+district.getDistrictKey()+"\"^^xsd:string: AS ?qid) . " // 
				+ "  BIND (\""+district.getScore()+"\"^^xsd:string AS ?score) . " //
				+ "  BIND (\""+district.getTargetString()+"\"^^xsd:string AS ?target) ." //
				+ "  BIND (\""+targetRelation+"\"^^xsd:string AS ?targetRelation) . " // 
				//TODO: this might be misleading as target is the original surface form and not
				// the current district as it would be needed to connect numbers to specific places
				+ "  BIND (<urn:qanary:"+this.applicationName+"> AS ?component) . " //
				+ "  BIND (now() AS ?time) . " //
				+ "}";

		myQanaryUtils.updateTripleStore(sparqlUpdateQuery, myQanaryMessage.getEndpoint());

	}

	public void removeEntityFromTriplestore(FoundEntity foundEntity) {
		// TODO: update triplestore

	}
	
}
