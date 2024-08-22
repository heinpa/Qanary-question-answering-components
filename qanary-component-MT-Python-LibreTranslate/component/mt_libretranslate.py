import logging
import os
import requests
from flask import Blueprint, jsonify, request
from qanary_helpers.qanary_queries import get_text_question_in_graph, insert_into_triplestore
from qanary_helpers.language_queries import get_translated_texts_in_triplestore, get_texts_with_detected_language_in_triplestore, question_text_with_language, create_annotation_of_question_translation
from utils.lang_utils import translation_options
from fastapi import APIRouter, Request
from fastapi.responses import JSONResponse


logging.basicConfig(format="%(asctime)s - %(message)s", level=logging.INFO)

router = APIRouter()

SERVICE_NAME_COMPONENT = os.environ["SERVICE_NAME_COMPONENT"]

TRANSLATE_ENDPOINT = os.environ["TRANSLATE_ENDPOINT"]
LANGUAGES_ENDPOINT = os.environ["LANGUAGES_ENDPOINT"]


def translate_input(text: str, source_lang: str, target_lang: str):

    req_json = {
        'q': text,
        'source': source_lang,
        'target': target_lang
    }
    headers = {
        'Content-Type': 'application/x-www-form-urlencoded'
    }
    response = requests.request("POST", TRANSLATE_ENDPOINT, headers=headers, data=req_json)
    logging.info(f"got response json: {response.json()}")
    result = response.json().get('translatedText')
    error = response.json().get('error')
    logging.info(f"result: \"{result}\"")
    translation = result.replace("\"", "\\\"") #keep quotation marks that are part of the translation
    if error:
        return ""
    else:
        return translation


def find_source_texts_in_triplestore(triplestore_endpoint: str, graph_uri: str, lang: str) -> list[question_text_with_language]:
    source_texts = []

    # check if supported languages have been determined already (LD)
    # (use filters)
    # if so, use the target uris to find the question text to translate
    ld_source_texts = get_texts_with_detected_language_in_triplestore(triplestore_endpoint, graph_uri, lang)
    source_texts.extend(ld_source_texts)

    # check if there are translations into the relevant language (MT)
    # (use filters)
    # if so, use the translation texts
    mt_source_texts = get_translated_texts_in_triplestore(triplestore_endpoint, graph_uri, lang)
    source_texts.extend(mt_source_texts)

    # TODO: what if nothing found? 
    if len(source_texts) == 0:
        logging.warning(f"No source texts with language {lang} could be found In the triplestore!")

    return source_texts


def translate_to_one(text: str, source_lang: str, target_lang: str):
    translation = translate_input(text, source_lang, target_lang)
    return {target_lang: translation}


def translate_to_all(text: str, source_lang: str):
    translations = list()
    for target_lang in translation_options[source_lang]:
        translation = translate_input(text, source_lang, target_lang)
        translations.append({
            target_lang: translation
        })
    return translations



@router.post("/annotatequestion", description="", tags=["Qanary"])
async def qanary_service(request: Request):
    """the POST endpoint required for a Qanary service"""

    request_json = await request.json()

    triplestore_endpoint = request_json["values"]["urn:qanary#endpoint"]
    triplestore_ingraph = request_json["values"]["urn:qanary#inGraph"]
    triplestore_outgraph = request_json["values"]["urn:qanary#outGraph"]
    logging.info("endpoint: %s, inGraph: %s, outGraph: %s" % \
                 (triplestore_endpoint, triplestore_ingraph, triplestore_outgraph))

    text_question_in_graph = get_text_question_in_graph(triplestore_endpoint=triplestore_endpoint, graph=triplestore_ingraph)
    question_text = text_question_in_graph[0]['text']
    logging.info(f'Original question text: {question_text}')

    # Collect texts to be translated (group by source language)

    for source_lang in translation_options.keys():
        source_texts = find_source_texts_in_triplestore(
            triplestore_endpoint=triplestore_endpoint,
            graph_uri=triplestore_ingraph,
            lang=source_lang
        )

        # translate source texts into specified target languages
        for target_lang in translation_options[source_lang]:
            for source_text in source_texts:
                translation = translate_input(source_text.get_text(), source_lang, target_lang)
                if len(translation.strip()) > 0:
                    SPARQLqueryAnnotationOfQuestionTranslation = create_annotation_of_question_translation(
                        graph_uri=triplestore_ingraph,
                        question_uri=source_text.get_uri(),
                        translation=translation,
                        translation_language=target_lang,
                        app_name=SERVICE_NAME_COMPONENT
                    )
                    insert_into_triplestore(triplestore_endpoint, SPARQLqueryAnnotationOfQuestionTranslation)
                else:
                    logging.error(f"result is empty string!")

    return JSONResponse(request_json)


def check_connection():
    logging.info(f"checking connection to {TRANSLATE_ENDPOINT}")
    error = "(No error message available)" #empty error message
    success = "The test translation was successful"
    try:
        # TODO: test with supported language? 
        t, error = translate_input("eingabe zum testen", "de", "en")
        logging.info(f"got translation: {t}")
        assert len(t) > 0
        return True, success
    except Exception:
        logging.info(f"test failed with {error}")
        return False, error


def get_languages():
    languages = requests.request("GET", LANGUAGES_ENDPOINT).json()
    valid_sources = [(f"{language['name']} ({language['code']})") for language in languages if 'en' in language['targets'] and 'en' not in language['code']]
    return valid_sources
