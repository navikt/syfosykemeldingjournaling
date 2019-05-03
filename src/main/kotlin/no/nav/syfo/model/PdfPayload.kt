package no.nav.syfo.model

import kotlinx.serialization.Serializable
import no.nav.syfo.sm.Diagnosekoder

@Serializable
data class PdfPayload(
    val pasient: Pasient,
    val sykmelding: Sykmelding,
    val hovedDiagnose: PdfDiagnosisCode?,
    val biDiagnoser: List<PdfDiagnosisCode>
)

data class Pasient(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val personnummer: String
)

data class PdfDiagnosisCode(
    val code: String,
    val text: String,
    val oid: String
)

fun Diagnose.toPDFFormat() = when(system) {
    Diagnosekoder.ICPC2_CODE -> Diagnosekoder.icpc2[kode]
    Diagnosekoder.ICD10_CODE -> Diagnosekoder.icd10[kode]
    else -> throw RuntimeException("Invalid oid for diagnosis $system")
}?.toPDFFormat() ?: throw RuntimeException("Invalid code $kode for oid $system")

fun Diagnosekoder.DiagnosekodeType.toPDFFormat() = PdfDiagnosisCode(code, text, oid)
