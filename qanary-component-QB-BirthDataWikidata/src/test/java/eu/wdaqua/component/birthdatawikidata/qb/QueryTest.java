package eu.wdaqua.component.birthdatawikidata.qb;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.impl.LiteralLabel;
import org.apache.jena.graph.impl.LiteralLabelFactory;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;

import eu.wdaqua.component.qb.birthdata.wikidata.Application;
import eu.wdaqua.qanary.commons.triplestoreconnectors.QanaryTripleStoreConnector;
import qa.commons.QanaryQueryTest;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = Application.class)
@WebAppConfiguration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QueryTest {

    @Test
    void filenameAnnotationsQueryTest() throws IOException {

        QanaryQueryTest queryTest = new QanaryQueryTest(QanaryQueryTest.COMMON_PREFIXES);

        String expectedGraph = "urn:graph";
        String expectedValue = "FIRST_NAME";

        QuerySolutionMap bindingsForFirstname = new QuerySolutionMap();
        bindingsForFirstname.add("graph", ResourceFactory.createResource(expectedGraph));
        bindingsForFirstname.add("value", ResourceFactory.createStringLiteral(expectedValue));

        String sparqlCheckFirstname = QanaryTripleStoreConnector.readFileFromResourcesWithMap(
                TestConfiguration.FILENAME_ANNOTATIONS, bindingsForFirstname
        );
        assertNotNull(sparqlCheckFirstname);
        assertFalse(sparqlCheckFirstname.isEmpty());
        assertFalse(sparqlCheckFirstname.isBlank());

        queryTest.isSparqlQueryEqual(sparqlCheckFirstname, TestConfiguration.getTestQuery("queries/getAnnotationTest.rq").concat("\n"));
        queryTest.queryContainsGraph(sparqlCheckFirstname, expectedGraph);
        queryTest.queryContainsFilterKeyValuePair(sparqlCheckFirstname, "?wikidataResource", expectedValue);
    }

    @Test
    @Disabled
    void filenameAnnotationsFilteredQueryTest() throws IOException {

        QanaryQueryTest queryTest = new QanaryQueryTest(QanaryQueryTest.COMMON_PREFIXES);

        String expectedGraph = "urn:graph";
        String expectedSource = "urn:source";
        String expectedStart = String.valueOf(5);

        QuerySolutionMap bindingsForAnnotation = new QuerySolutionMap();
        bindingsForAnnotation.add("graph", ResourceFactory.createResource(expectedGraph));
        bindingsForAnnotation.add("hasSource", ResourceFactory.createResource(expectedSource));
        bindingsForAnnotation.add("start", ResourceFactory.createTypedLiteral(expectedStart, XSDDatatype.XSDint));

        String sparqlGetAnnotation = QanaryTripleStoreConnector.readFileFromResourcesWithMap(

                TestConfiguration.FILENAME_ANNOTATIONS_NAMED_ENTITY_FILTERED_FOR_WIKIDATA,
                bindingsForAnnotation
        );
        assertNotNull(sparqlGetAnnotation);
        assertFalse(sparqlGetAnnotation.isEmpty());
        assertFalse(sparqlGetAnnotation.isBlank());

        queryTest.queryContainsGraph(sparqlGetAnnotation, expectedGraph);
        queryTest.queryContainsFilterKeyValuePair(sparqlGetAnnotation, "?start", expectedStart);
        queryTest.queryContainsTriple(sparqlGetAnnotation, "?target", "oa:hasSource", expectedSource);
    }

    @Test
    void questionAnswerFromWikidataByPersonTest() throws IOException {
        QanaryQueryTest queryTest = new QanaryQueryTest(QanaryQueryTest.COMMON_PREFIXES);

        String expectedPerson = "urn:person";

        QuerySolutionMap bindingsForWikidataResultQuery = new QuerySolutionMap();
        bindingsForWikidataResultQuery.add("person", ResourceFactory.createResource(expectedPerson));

        String sparql = QanaryTripleStoreConnector.readFileFromResourcesWithMap(
                TestConfiguration.FILENAME_WIKIDATA_BIRTHDATA_QUERY_PERSON,
                bindingsForWikidataResultQuery
        );
        assertNotNull(sparql);
        assertFalse(sparql.isEmpty());
        assertFalse(sparql.isBlank());

        queryTest.queryContainsTriple(sparql, expectedPerson, "wdt:P735", "?firstname");
        queryTest.queryContainsTriple(sparql, expectedPerson, "wdt:P569", "?birthdate");
        queryTest.queryContainsTriple(sparql, expectedPerson, "wdt:P19", "?birthplace");
        queryTest.queryContainsTriple(sparql, expectedPerson, "wdt:P19", "?specificBirthPlace");

        // exact comparison: 
        // assertEquals(TestConfiguration.getTestQuery("queries/getQuestionAnswerFromWikidataByPersonTest.rq").concat("\n"), sparql);
    }

    @Test
    void wikidataQueryFirstAndLastNameTest() throws IOException {

        QanaryQueryTest queryTest = new QanaryQueryTest(QanaryQueryTest.COMMON_PREFIXES);

        String expectedFirstName = "FIRST_NAME";
        String expectedLastName = "LAST_NAME";
        String expectedLanguage = "en";

        QuerySolutionMap bindingsForWikidataResultQuery = new QuerySolutionMap();
        bindingsForWikidataResultQuery.add("firstnameValue", ResourceFactory.createLangLiteral(expectedFirstName, expectedLanguage));
        bindingsForWikidataResultQuery.add("lastnameValue", ResourceFactory.createLangLiteral(expectedLastName, expectedLanguage));
        String sparql = QanaryTripleStoreConnector.readFileFromResourcesWithMap(
                TestConfiguration.FILENAME_WIKIDATA_BIRTHDATA_QUERY_FIRST_AND_LASTNAME,
                bindingsForWikidataResultQuery
        );
        assertNotNull(sparql);
        assertFalse(sparql.isEmpty());
        assertFalse(sparql.isBlank());

        LiteralLabel expectedFirstNameLiteral = LiteralLabelFactory.create(expectedFirstName, "en");
        LiteralLabel expectedLastNameLiteral = LiteralLabelFactory.create(expectedLastName, "en");
        LiteralLabel expectedLanguageLiteral = LiteralLabelFactory.create(expectedLanguage, XSDDatatype.XSDstring);

        queryTest.queryContainsTriple(sparql, "?firstname", "rdfs:label", expectedFirstNameLiteral);
        queryTest.queryContainsTriple(sparql, "?lastname", "rdfs:label", expectedLastNameLiteral);
        queryTest.queryContainsTriple(sparql, "bd:serviceParam", "wikibase:language", expectedLanguageLiteral);

        // exact comparison: 
        // assertEquals(TestConfiguration.getTestQuery("queries/getQuestionAnswerFromWikidataByFirstnameLastnameTest.rq").concat("\n"), sparql);
    }

}
