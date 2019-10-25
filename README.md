[![Build status](https://github.com/navikt/syfosmsak/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)](https://github.com/navikt/syfosmsak/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)

# SYFO sykemelding journaling
This is a simple application who takes the sykemelding2013 XML document, generates a PDF and sends it to Joark to
persist it

## Technologies used
* Kotlin
* Ktor
* Gradle
* Spek
* Kafka

## Getting started
### Running locally
Unless you change kafka_consumer.properties you will need to either connect to one of NAVs kafka clusters or use the
[docker compose](https://github.com/navikt/navkafka-docker-compose) environment to test it against. To run it the
environment variables `SRVSYFOSYKEMELDINGREGLER_USERNAME` and `SYFOSYKEMELDINGREGLER_PASSWORD` needs to be set to
a user that has access to the topic defined by the environment variable `KAFKA_SM2013_JOURNALING_TOPIC`


### Building the application
#### Compile and package application
To build locally and run the integration tests you can simply run `./gradlew shadowJar` or  on windows 
`gradlew.bat shadowJar`

#### Creating a docker image
Creating a docker image should be as simple as `docker build -t syfosmsak .`


## Contact us
### Code/project related questions can be sent to
* Joakim Kartveit, `joakim.kartveit@nav.no`
* Andreas Nilsen, `andreas.nilsen@nav.no`
* Sebastian Knudsen, `sebastian.knudsen@nav.no`
* Tia Firing, `tia.firing@nav.no`

### For NAV employees
We are available at the Slack channel #team-sykmelding
