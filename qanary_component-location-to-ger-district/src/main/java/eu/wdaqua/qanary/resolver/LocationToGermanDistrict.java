package eu.wdaqua.qanary.resolver;

import java.util.List;
import java.util.LinkedList;
import java.util.Arrays;

import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import eu.wdaqua.qanary.commons.QanaryMessage;
import eu.wdaqua.qanary.commons.QanaryQuestion;
import eu.wdaqua.qanary.commons.QanaryUtils;
import eu.wdaqua.qanary.component.QanaryComponent;
import eu.wdaqua.qanary.exceptions.SparqlQueryFailed;

enum LocationType {
	DISTRICT, STATE
}


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
	private final List<String> supportedLanguages = Arrays.asList("en");

	public LocationToGermanDistrict(
			@Value("${spring.application.name}") final String applicationName) {
		this.applicationName = applicationName;
	}

	/**
	 * encapsulates the functionality of LocationToGermanDistrict
	 * component	 
	 *
	 * @throws SparqlQueryFailed
	 */
	@Override
	public QanaryMessage process(QanaryMessage myQanaryMessage) throws Exception {
		logger.info("process: {}", myQanaryMessage);

		QanaryUtils myQanaryUtils = this.getUtils(myQanaryMessage);

		// if required fetch the origin question (here the question is a
		// textual/String question)
		QanaryQuestion<String> myQanaryQuestion = new QanaryQuestion<String>(myQanaryMessage);
		String questionText = myQanaryQuestion.getTextualRepresentation();

		// get the question language for this question
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
		String questionLanguage = null;
		try {
			QuerySolution tupel = langResultset.next();
			String language = tupel.get("language").toString();
			if (this.supportedLanguages.contains(language)) {
				questionLanguage = language;
				logger.info("Annotated language {} is supported!", language);
			} else {
				logger.info("Annotated language {} is not supported!", language);
			}
		} catch (Exception e) {
			logger.warn("No language was annotated! \n{}", e.getMessage());
		}

		// don't continue processing if the language is not suppoted or cannot be found
		if (questionLanguage == null) {
			return myQanaryMessage;
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
				+ "    ?annotation     qa:score     ?annotationScore ." // confidence 
				+ "    ?annotation     oa:hasTarget ?target ." //
				+ "    ?target     oa:hasSource    <" + myQanaryQuestion.getUri().toString() + "> ." // annotated for the current question
				+ "    ?target     oa:hasSelector  ?textSelector ." //
				+ "    ?textSelector   rdf:type    oa:TextPositionSelector ." //
				+ "    ?textSelector   oa:start    ?start ." //
				+ "    ?textSelector   oa:end      ?end ." //
				+ "}";

		ResultSet resultset = myQanaryUtils.selectFromTripleStore(selectNamedEntities);
		List<FoundEntity> foundEntities = new LinkedList<FoundEntity>();

		// prepare identified entities for processing
		while (resultset.hasNext()) {
			QuerySolution tupel = resultset.next();
			String triplestoreId = tupel.get("annotation").toString();
			logger.info("triplestoreId: {}", triplestoreId);

			String wikidataResource = tupel.get("wikidataResource").toString();
			logger.info("resource: {}", wikidataResource);

			float score = Float.parseFloat(tupel.get("annotationScore").toString()
					.substring(0,tupel.get("annotationScore").toString().indexOf("^^"))); //float?
			logger.info("score: {}", score);

			// find the part (substring) of the question string that was annotated 
			// i.e. the name of a location 
			int start =  Integer.parseInt(tupel.get("start").toString()
					.substring(0,tupel.get("start").toString().indexOf("^^")));
			int end =  Integer.parseInt(tupel.get("end").toString()
					.substring(0,tupel.get("end").toString().indexOf("^^")));
			String target = questionText.substring(start, end);
			logger.info("target in question string: {}", target);

			// create an object that holds the entity and additional information
			FoundEntity foundEntity = new FoundEntity(wikidataResource, target, score);
			foundEntity.setTriplestoreId(triplestoreId);
			foundEntities.add(foundEntity);
		}

		// iterate over all identified entities to find locations in Germany.
		
		// if a location is not a district or federal state itself then check if it
		// contains one or multiple districts (in case of a region) or if it is 
		// located in a district (like specific places/streets etc.)
		for (FoundEntity foundEntity : foundEntities) {

			// get a list of related districts
			logger.info("find related districts for {}", foundEntity.getTargetString());
			List<FoundEntity> relatedDistricts = this.findRelatedDistricts(foundEntity, questionLanguage);
			logger.info("found {} related districts", relatedDistricts.size());
			// get the language-specific name of the entity
			try {
				String surfaceForm = this.findSurfaceForm(foundEntity, questionLanguage);
				foundEntity.setSurfaceForm(surfaceForm);
			} catch (Exception e) {
				foundEntity.setSurfaceForm(foundEntity.getTargetString());
				logger.error(e.getLocalizedMessage());
			}

			// if the entity itself is a fedral state of Germany annotate it as possible solution
			if (this.isFederalStateOfGermnay(foundEntity)) {
				logger.info("{} ({}) is a German federal state", foundEntity.getSurfaceForm(), foundEntity.getQID());
				foundEntity.setLocationType(LocationType.STATE);
				try {
					int key = this.findRegionalKey(foundEntity); // federal states are identified with a regional key
					foundEntity.setKey(key);
					this.addDistrictToTriplestore(foundEntity, myQanaryMessage, myQanaryUtils, "containing");
				} catch (Exception e) {
					// don't add locations without key as they cannot be used later
					// TODO: this should be optional for new application contexts
					logger.error("information for is missing for {} ({}). Not added to resutls", foundEntity.getSurfaceForm(), foundEntity.getQID());
					logger.debug(e.getLocalizedMessage());
				}
			// if the entity itself is a district of Germany annotate it as possible solution
			} else if (this.isDistrictOfGermany(foundEntity)) {
				logger.info("{} ({}) is a German district", foundEntity.getSurfaceForm(), foundEntity.getQID());
				foundEntity.setLocationType(LocationType.DISTRICT);
				try {
					int key = this.findDistrictKey(foundEntity); // districts are identified with a district key
					foundEntity.setKey(key);
					this.addDistrictToTriplestore(foundEntity, myQanaryMessage, myQanaryUtils, "containing");
				} catch (Exception e) {
					logger.error("information for is missing for {} ({}). Not added to resutls",foundEntity.getSurfaceForm(), foundEntity.getQID());
					logger.debug(e.getLocalizedMessage());
				}
			} else {
				// if the entity is not a district itself then remove it from the triplestore
				logger.info("{} ({}) is not a district!", foundEntity.getSurfaceForm(), foundEntity.getQID());
				// TODO: enable once implemented
				// this.removeEntityFromTriplestore(foundEntity);
			}
			// if only one related district is found assume that it contains the target 
			if (relatedDistricts.size() == 1) {
				this.addDistrictToTriplestore(relatedDistricts.get(0), myQanaryMessage, myQanaryUtils, "containing");
			// if multiple are found they are likely located in the target region
			} else {
				for (FoundEntity relatedDistrict : relatedDistricts) {
					logger.info("adding {} to triplestore", relatedDistrict.getWikidataResource());
					this.addDistrictToTriplestore(relatedDistrict, myQanaryMessage, myQanaryUtils, "located_in");
				}
			}
		}
		return myQanaryMessage;
	}

	/*
	 * find German districts related to a given location. They can either contain the location
	 * (in case of a region) or be lcoated in a location (places, streets, etc.).
	 * Names for related districts are selected based on the question language.
	 *
	 * @param foundEntity
	 * @param questionLanguage
	 * @return 
	 */
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
			// + "	wd:Q149621 " // a more generalized district concept; does not guarantee key
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
					FoundEntity relatedDistrict = new FoundEntity(districtWikidataResource, foundEntity.getTargetString(), score);
					relatedDistrict.setKey(this.findDistrictKey(relatedDistrict));
					relatedDistrict.setSurfaceForm(surfaceForm);
					relatedDistrict.setLocationType(LocationType.DISTRICT);
					logger.info("adding {} ({}) to relatedDistricts", relatedDistrict.getSurfaceForm(), relatedDistrict.getQID());
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

	/*
	 * find the language specific name (label) of a location.
	 *
	 * @param foundEntity
	 * @param questionLanguage
	 * @return 
	 */
	public String findSurfaceForm(FoundEntity foundEntity, String questionLanguage) {
		String wikidataResource = foundEntity.getWikidataResource();
		String wikidataGetQuery = ""
			+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " //
			+ "SELECT ?labelLang WHERE { " //
			+ "   <"+wikidataResource+"> rdfs:label ?labelLang . " //
			+ "  FILTER( LANG(?labelLang) = \""+questionLanguage+"\" ) " // 
			+ "} ";

		String wikidataEndpoint = "https://query.wikidata.org/sparql";
		QueryExecution qexec = QueryExecutionFactory.sparqlService(wikidataEndpoint, wikidataGetQuery);
		ResultSet result = qexec.execSelect();
		QuerySolution tupel = result.next();
		String surfaceForm = tupel.get("labelLang").toString()
			.substring(0,tupel.get("labelLang").toString().indexOf("@"));
		return surfaceForm;
	}

	/*
	 * find the regional key of a German federal state.
	 *
	 * @param foundEntity
	 * @return
	 */
	public int findRegionalKey(FoundEntity foundEntity) {
		String wikidataResource = foundEntity.getWikidataResource();
		String wikidataGetQuery = ""
			+ "PREFIX wdt: <http://www.wikidata.org/prop/direct/> " //
			+ "SELECT ?key WHERE { " //
			+ "  <"+wikidataResource+"> wdt:P1388 ?key . " //
			+ "} ";

		String wikidataEndpoint = "https://query.wikidata.org/sparql";
		QueryExecution qexec = QueryExecutionFactory.sparqlService(wikidataEndpoint, wikidataGetQuery);
		ResultSet result = qexec.execSelect();
		QuerySolution tupel = result.next();
		int key = Integer.parseInt(tupel.get("key").toString());  // check?
		logger.info("found key: {}", key);
		return key;
	}

	/*
	 * find the key of a German district.
	 *
	 * @param foundEntity
	 * @return
	 */
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

	/*
	 * determine if an entity is a federal state of Germany.
	 *
	 * @param foundEntity
	 * @return
	 */
	public boolean isFederalStateOfGermnay (FoundEntity foundEntity) {
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
			+ "    wd:Q1221156" // denotes a federal state of Germany as opposed to more generic definition
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

	/*
	 * determine if an entity is a district of Germany.
	 *
	 * @param foundEntity
	 * @return 
	 */
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
			+ "    wd:Q42744322 " // uran municipality of Germany
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

	/**
	 * create and execute an INSERT query to add an identified location entity to the triplestore.
	 *
	 * @param district
	 * @param myQanaryMessage
	 * @param myQanaryUtils
	 * @param targetRelation
	 * @throws SparqlQueryFailed
	 */
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
				+ "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> " //
				+ "PREFIX owl: <http://www.w3.org/2002/07/owl#> " //
				+ "INSERT { " //
				+ "GRAPH <"+myQanaryMessage.getInGraph().toString()+"> { " //
				+ "  ?a a qa:AnnotationOfInstanceLocation . " //
				+ "  ?a oa:hasBody [ " //
				+ "    dbo:type ?type; "  // district or federal state of Germany
				+ "    rdfs:label ?label; " // language specific label of the location
				+ "	   qa:hasID ?qid; " // unique district/regional key
				+ "    owl:sameAs ?uri " // wikidata URI
				+ "  ] . " //
				+ "  ?a qa:hasConfidence ?score . " // confidence 
				+ "  ?a oa:hasTarget [ " // part of the question identified to be a location
				+ "    rdfs:label ?target ; " // specific substring 
				+ "    qa:targetRelation ?targetRelation " // located_in or containing
				+ "  ] . " //
				+ "  ?a oa:annotatedBy ?component  . " // 
				+ "  ?a oa:annotatedAt ?time . " // 
				+ "} " //
				+ "} " //
				+ "WHERE { " // 
				+ "  BIND (IRI(str(RAND())) AS ?a) . " //
				+ "  BIND ("+district.getLocationType()+" AS ?type) . " //
				+ "  BIND (\""+district.getSurfaceForm()+"\"^^xsd:string AS ?label) . " // 
				+ "  BIND (\""+district.getKey()+"\"^^xsd:string: AS ?qid) . " // 
				+ "  BIND (\""+district.getScore()+"\"^^xsd:string AS ?score) . " //
				+ "  BIND (\""+district.getTargetString()+"\"^^xsd:string AS ?target) ." //
				+ "  BIND (\""+targetRelation+"\"^^xsd:string AS ?targetRelation) . " // 
				+ "  BIND (<"+district.getWikidataResource()+"> AS ?uri) . " //
				+ "  BIND (<urn:qanary:"+this.applicationName+"> AS ?component) . " //
				+ "  BIND (now() AS ?time) . " //
				+ "}";

		myQanaryUtils.updateTripleStore(sparqlUpdateQuery, myQanaryMessage.getEndpoint());
	}

	public void removeEntityFromTriplestore(FoundEntity foundEntity) {
		// remove entities that are not locations
		// TODO: remove entities from triplestore
	}
}
