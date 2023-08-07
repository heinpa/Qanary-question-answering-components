package eu.wdaqua.qanary.component.qanswer.qb.messages;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import io.swagger.v3.oas.annotations.Hidden;
import net.minidev.json.JSONObject;

public class QAnswerResult {
    private static final Logger logger = LoggerFactory.getLogger(QAnswerResult.class);
    @Hidden
    public final URI RESOURCETYPEURI;
    @Hidden
    public final URI BOOLEANTYPEURI;
    @Hidden
    public final URI STRINGTYPEURI;
    @Hidden
    private com.google.gson.JsonParser jsonParser;
    private URI endpoint;
    private String knowledgebaseId;
    private String user;
    private String language;
    private String question;
    private String sparql;
    private List<String> values;
    private double confidence;

    public QAnswerResult(JSONObject json, String question, URI endpoint, String language, String knowledgebaseId, String user)
            throws URISyntaxException {

        logger.debug("result: {}", json.toJSONString());

        JsonArray parsedJsonArray = JsonParser.parseString(json.toJSONString()).getAsJsonObject().getAsJsonArray("queries")
                .getAsJsonArray();

        this.question = question;
        this.language = language;
        this.knowledgebaseId = knowledgebaseId;
        this.user = user;
        this.endpoint = endpoint;

        this.RESOURCETYPEURI = new URI("http://www.w3.org/2001/XMLSchema#anyURI");
        this.BOOLEANTYPEURI = new URI("http://www.w3.org/2001/XMLSchema#boolean");
        this.STRINGTYPEURI = new URI("http://www.w3.org/2001/XMLSchema#string");

        initData(parsedJsonArray);
    }

    /**
     * init the fields while parsing the JSON data
     *
     * @param answers
     * @throws URISyntaxException
     * @throws NoLiteralFieldFoundException
     */
    private void initData(JsonArray answers) throws URISyntaxException {
        logger.debug("responseQuestion: {}", answers);

        logger.debug("0. sparql: {}", answers.get(0).getAsString());
        this.sparql = answers.get(0).getAsString();
    }

    public List<String> getValues() {
        return values;
    }

    public String getKnowledgebaseId() {
        return knowledgebaseId;
    }

    public String getUser() {
        return user;
    }

    public String getLanguage() {
        return language;
    }

    public URI getEndpoint() {
        return endpoint;
    }

    public String getQuestion() {
        return question;
    }

    public String getSparql() {
        return sparql;
    }

    public double getConfidence() {
        return confidence;
    }

}
