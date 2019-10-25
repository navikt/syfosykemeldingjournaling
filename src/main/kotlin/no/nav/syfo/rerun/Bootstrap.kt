package no.nav.syfo.rerun

import io.ktor.util.KtorExperimentalAPI
import java.util.Properties
import no.nav.syfo.Environment
import no.nav.syfo.VaultCredentials
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.rerun.pdf.service.RerunPdfGenerationService
import no.nav.syfo.rerun.service.FindNAVKontorService
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.service.JournalService
import no.nav.syfo.ws.createPort
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.binding.ArbeidsfordelingV1
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer

@UseExperimental(KtorExperimentalAPI::class)
fun setupRerunDependencies(journalService: JournalService, personV3: PersonV3, env: Environment, credentials: VaultCredentials, consumerConfig: Properties, applicationState: ApplicationState, producerConfig: Properties) {
    val arbeidsfordelingV1 = createPort<ArbeidsfordelingV1>(env.arbeidsfordelingV1EndpointURL) {
        port { withSTS(credentials.serviceuserUsername, credentials.serviceuserPassword, env.securityTokenServiceURL) }
    }
    val findNAVKontorService = FindNAVKontorService(personV3, arbeidsfordelingV1)

    val kafkaConsumer = KafkaConsumer<String, String>(consumerConfig)
    val kafkaProducer = KafkaProducer<String, ProduceTask>(producerConfig)

    val rerunPdfGenerationService = RerunPdfGenerationService(kafkaConsumer, journalService, applicationState, env.rerunTopicName, findNAVKontorService, kafkaProducer)

    rerunPdfGenerationService.start()
}
