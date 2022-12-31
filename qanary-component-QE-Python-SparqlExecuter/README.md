# QE Python SparqlExecuter

## Description

A simple component that executes a SPARQL query on a given endpoint and returns the results.

Docker Hub image: `qanary/qanary-component-qe-python-sparqlexecuter`

## Input specification

```ttl
@prefix qa: <http://www.wdaqua.eu/qa#> .
@prefix oa: <http://www.w3.org/ns/openannotation/core/> .

<urn:qanary:input> a qa:AnnotationOfAnswerSPARQL ;
   oa:hasBody "sparql query over dbpedia or wikidata" ;
   oa:annotatedAt "2001-10-26T21:32:52"^^xsd:dateTime .
```

## Output specification

```ttl
@prefix qa: <http://www.wdaqua.eu/qa#> .
@prefix oa: <http://www.w3.org/ns/openannotation/core/> .
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

<urn:qanary:output> a qa:AnnotationOfAnswerJson ;
    oa:hasTarget <urn:qanary:myQuestionUri> ;
    oa:hasBody ?answer ;
    oa:annotatedBy <urn:qanary:applicationName> ;
    oa:annotatedAt "2001-10-26T21:32:52"^^xsd:dateTime .
?answer a qa:AnswerJson ;
    rdf:value "jsonString"^^xsd:string  .
qa:AnswerJson rdfs:subClassOf qa:Answer .
```

## Usage

1. Clone the Git repository of the collected Qanary components:

```bash
git clone https://github.com/WDAqua/Qanary-question-answering-components.git
```

2. Switch to the component's directory:

```bash
cd Qanary-question-answering-components/qanary-component-QE-Python-SparqlExecuter
```

3. Set the environment variables in the `.env` file

```bash
SPRING_BOOT_ADMIN_URL=http://qanary-host-url:40111
SPRING_BOOT_ADMIN_USERNAME=admin
SPRING_BOOT_ADMIN_PASSWORD=admin
SERVER_HOST=component-host-url
SERVER_PORT=40124
SERVICE_NAME_COMPONENT=QE-Python-SPARQLExecuter
SERVICE_DESCRIPTION_COMPONENT=Executes a SPARQL query generated by the previous components
SPARQL_ENDPOINT=https://dbpedia.org/sparql
PRODUCTION=True
```

The parameters description:

* `SPRING_BOOT_ADMIN_URL` -- URL of the Qanary pipeline (see Step 1 and Step 2 of the [tutorial](https://github.com/WDAqua/Qanary/wiki/Qanary-tutorial:-How-to-build-a-trivial-Question-Answering-pipeline))
* `SPRING_BOOT_ADMIN_USERNAME` -- the admin username of the Qanary pipeline
* `SPRING_BOOT_ADMIN_PASSWORD` -- the admin password of the Qanary pipeline
* `SERVER_HOST` -- the host of your Qanary component without protocol prefix (e.g., `http://`). It has to be visible to the Qanary pipeline (i.e., a callback from the Qanary pipeline can be executed).
* `SERVER_PORT` -- the port of your Qanary component (has to be visible to the Qanary pipeline)
* `SERVICE_NAME_COMPONENT` -- the name of your Qanary component (for better identification)
* `SERVICE_DESCRIPTION_COMPONENT` -- the description of your Qanary component
* `SPARQL_ENDPOINT` -- the SPARQL endpoint to be used for the query execution
* `PRODUCTION` -- the flag that indicates whether the component is running in production mode

4. Build the Docker image: 

```bash
docker-compose build .
```

5. Run the the component with docker-compose:

```bash
docker-compose up
```