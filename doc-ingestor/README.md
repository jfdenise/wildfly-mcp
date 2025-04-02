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

## Test semantic search and ask Mistral AI LLM to compute a reply for each question

This takes long time!!!!

mvn exec:java -Dquestion=questions -Dinvoke-llm=true

## Anayze Mistral AI LLM replies

mvn exec:java -Danalyze-llm-replies=true

