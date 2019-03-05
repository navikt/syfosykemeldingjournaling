package no.nav.syfo.model

import java.time.ZonedDateTime

data class MottaInngaandeForsendelseResultat(
        val journalpostId: String,
        val journalTilstand: String,
        val dokumentIdList: List<String>
)

data class MottaInngaaendeForsendelse(
        val forsokEndeligJF: Boolean,
        val forsendelseInformasjon: ForsendelseInformasjon,
        val tilleggsopplysninger: List<Tilleggsopplysning>,
        val dokumentInfoHoveddokument: DokumentInfo,
        val dokumentInfoVedlegg: List<DokumentInfo>
)

data class ForsendelseInformasjon(
        val bruker: Aktoer,
        val avsender: Aktoer,
        val tema: String,
        val kanalReferanseId: String,
        val forsendelseMottatt: ZonedDateTime,
        val forsendelseInnsendt: ZonedDateTime,
        val mottaksKanal: String,
        val tittel: String?,
        val arkivSak: ArkivSak?
)

data class Aktoer(
        val orgnr: String? = null,
        val aktoerId: String? = null,
        val fnr: String? = null,
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
