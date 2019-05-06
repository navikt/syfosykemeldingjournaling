package no.nav.syfo.model

import kotlinx.serialization.Serializable
import no.nav.syfo.sm.Diagnosekoder

@Serializable
data class PdfPayload(
    val pasient: Pasient,
    val hovedDiagnose: EnumRepresentation?,
    val biDiagnoser: List<EnumRepresentation>,
    val annenFraversArsakGrunn: List<EnumRepresentation>,
    val sykmelding: Sykmelding
)

data class Pasient(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val personnummer: String
)

data class EnumRepresentation(
    val code: String,
    val text: String,
    val oid: String
)

fun AnnenFraverGrunn.toPDFFormat() = EnumRepresentation(codeValue, text, oid)

fun Diagnose.toPDFFormat() = when (system) {
    Diagnosekoder.ICPC2_CODE -> Diagnosekoder.icpc2[kode]
    Diagnosekoder.ICD10_CODE -> Diagnosekoder.icd10[kode]
    else -> throw RuntimeException("Invalid oid for diagnosis $system")
}?.toPDFFormat() ?: throw RuntimeException("Invalid code $kode for oid $system")

fun Diagnosekoder.DiagnosekodeType.toPDFFormat() = EnumRepresentation(code, text, oid)
