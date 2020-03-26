package no.nav.syfo.pdl.client.model

data class GetPersonResponse(
    val data: ResponseData
)

data class ResponseData(
    val hentIdenter: Identliste?,
    val hentPerson: HentPerson?
)

data class HentPerson(
    val navn: List<Navn>?,
    val adressebeskyttelse: List<Adressebeskyttelse>?
)

data class Adressebeskyttelse(
    val gradering: String
)

data class Navn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String
)

data class Identliste(
    val identer: List<IdentInformasjon>
)

data class IdentInformasjon(
    val ident: String
)
