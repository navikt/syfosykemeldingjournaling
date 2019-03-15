package no.nav.syfo.model

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.ZonedDateTime

@JsonInclude(JsonInclude.Include.NON_NULL)
data class MottaInngaandeForsendelseResultat(
    val journalpostId: String,
    val journalTilstand: String,
    val dokumentIdList: List<String>
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class MottaInngaaendeForsendelse(
    val forsokEndeligJF: Boolean,
    val forsendelseInformasjon: ForsendelseInformasjon,
    val tilleggsopplysninger: List<Tilleggsopplysning>,
    val dokumentInfoHoveddokument: DokumentInfo,
    val dokumentInfoVedlegg: List<DokumentInfo>
)

data class ForsendelseInformasjon(
    val bruker: AktoerWrapper,
    val avsender: AktoerWrapper,
    val tema: String,
    val kanalReferanseId: String,
    val forsendelseMottatt: ZonedDateTime,
    val forsendelseInnsendt: ZonedDateTime,
    val mottaksKanal: String,
    val tittel: String?,
    val arkivSak: ArkivSak?
)

data class AktoerWrapper(
    val aktoer: Aktoer
)

data class Aktoer(
    val organisasjon: Organisasjon? = null,
    val person: Person? = null
)

data class Person(
    val ident: String? = null,
    val fnr: String? = null
)

data class Organisasjon(
    val orgnr: String,
    val navn: String? = null
)

data class ArkivSak(
    val arkivSakSystem: String,
    val arkivSakId: String
)

data class Tilleggsopplysning(
    val nokkel: String,
    val verdi: String
)

data class DokumentInfo(
    val dokumentTypeId: String? = null,
    val tittel: String?,
    val dokumentkategori: String?,
    val brevkode: String? = null,
    val dokumentVariant: List<DokumentVariant>
)

data class DokumentVariant(
    val arkivFilType: String,
    val variantFormat: String,
    val dokument: ByteArray
)
