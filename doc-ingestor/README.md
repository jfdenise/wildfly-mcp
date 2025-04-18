# Documentation generator, ingestor, tester

## Dump the model

sh dump-model.sh

## Generate documentation and questions

mvn exec:java -Dgenerate-doc=true

## Generate more questions with LLM

This takes long time!!!!

mvn exec:java -Dgenerate-llm=true

## Ingest doc and produce cli questions metadata for testing 

mvn exec:java

## Test semantic search only

mvn exec:java -Dquestion=questions

## Test semantic search only for team questions

mvn exec:java -Dquestion=team-questions

## Test semantic search and ask Mistral AI LLM to compute a reply for each question

This takes long time!!!!

mvn exec:java -Dquestion=questions -Dinvoke-llm=true

## Test semantic search and ask Mistral AI LLM to compute a reply for each team question

mvn exec:java -Dquestion=team-questions -Dinvoke-llm=true

## Anayze Mistral AI LLM replies

mvn exec:java -Danalyze-llm-replies=true

## Latest results 2025-04-07

* Generated from doc
* Manually written
* mistral-small generated
* qwen2.4:3b generated

mvn exec:java -Dquestion=questions -Dmin-score=0.6 -Dinvoke-llm

TOTAL NUM OF QUESTIONS       :11523
AVERAGE CONTEXT SIZE         :4
MAX CONTEXT SIZE             :145
MAX CONTEXT SIZE QUESTION    :Could you get the servlet-security deployment?
NUM TOP RANKED QUESTIONS     :9934 86.21019%
NUM NOT TOP RANKED QUESTIONS :1589 13.789812%
NUM NOT RANKED QUESTIONS     :0 0.0%
CONTEXT SIZE DISTRIBUTION  :
Context size 4 num: 11342 98.42923%
Context size 5 num: 56 0.48598456%
Context size 6 num: 34 0.29506204%
Context size 7 num: 17 0.14753102%
Context size 8 num: 7 0.06074807%
Context size 9 num: 4 0.034713183%
Context size 10 num: 9 0.07810466%
Context size 11 num: 14 0.12149614%
Context size 12 num: 5 0.043391477%
Context size 13 num: 8 0.069426365%
Context size 14 num: 3 0.026034886%
Context size 16 num: 4 0.034713183%
Context size 17 num: 1 0.008678296%
Context size 18 num: 2 0.017356591%
Context size 19 num: 1 0.008678296%
Context size 20 num: 1 0.008678296%
Context size 21 num: 1 0.008678296%
Context size 23 num: 4 0.034713183%
Context size 25 num: 1 0.008678296%
Context size 30 num: 1 0.008678296%
Context size 32 num: 1 0.008678296%
Context size 46 num: 1 0.008678296%
Context size 51 num: 1 0.008678296%
Context size 55 num: 1 0.008678296%
Context size 57 num: 1 0.008678296%
Context size 58 num: 2 0.017356591%
Context size 145 num: 1 0.008678296%
TOTAL NUM OF CONTEXTS        : 47339
SCORE DISTRIBUTION  :
0.90=17919 37.852512%
0.80=27760 58.640865%
0.70=1293 2.731363%
0.60=367 0.7752593%

## Analyze Mistral small replies to all questions

NUM QUESTIONS            :11523
NUM EXACT MATCH          :1689 14.0%
NUM NO ANSWER            :2175 18.0%
NUM REPLY PARSE OK       :7550 65.0%
NUM REPLY PARSE FAILURES :109 0.0%

## Analyze Mistral small replies to team questions

NUM QUESTIONS            :18
NUM EXACT MATCH          :3 16.0%
NUM NO ANSWER            :7 38.0%
NUM REPLY PARSE OK       :7 38.0%
NUM REPLY PARSE FAILURES :1 5.0%
