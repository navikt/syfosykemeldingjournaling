package no.nav.syfo.model

import kotlinx.serialization.Serializable
import no.nav.syfo.sm.Diagnosekoder

@Serializable
data class PdfPayload(
    val pasient: Pasient,
    val hovedDiagnose: EnumRepresentation?,
    val biDiagnoser: List<EnumRepresentation>,
    val annenFraversArsakGrunn: List<EnumRepresentation>,
    val sykmelding: Sykmelding,
    val validationResult: ValidationResult
)

data class Pasient(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val personnummer: String,
    val tlfNummer: String?
)

data class EnumRepresentation(
    val kode: String,
    val tekst: String,
    val oid: String
)

fun AnnenFraverGrunn.toPDFFormat() = EnumRepresentation(codeValue, text, oid)

fun Diagnose.toPDFFormat() = when (system) {
    Diagnosekoder.ICPC2_CODE -> Diagnosekoder.icpc2[kode]
    Diagnosekoder.ICD10_CODE -> Diagnosekoder.icd10[kode]
    else -> throw RuntimeException("Invalid oid for diagnosis $system")
}?.toPDFFormat() ?: EnumRepresentation(kode, "Ukjent diagnosekode $kode for ${diagnoseTypeTekst(this)}", system)

fun diagnoseTypeTekst(diagnose: Diagnose) = when (diagnose.system) {
    Diagnosekoder.ICPC2_CODE -> "ICPC-2"
    Diagnosekoder.ICD10_CODE -> "ICD-10"
    else -> throw RuntimeException("Invalid oid for diagnosis ${diagnose.system}")
}

fun Diagnosekoder.DiagnosekodeType.toPDFFormat() = EnumRepresentation(code, text, oid)
