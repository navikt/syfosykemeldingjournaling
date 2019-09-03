package no.nav.syfo

import no.nav.syfo.model.ReceivedSykmelding
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.LocalDateTime

object SortedPeriodeSpek : Spek({

    describe("Testing sorting the fom and tom of a periode") {

        it("Should choose the the fom and tom") {

            val sykmelding = generateSykmelding()

            val receivedSykmelding = ReceivedSykmelding(
                    sykmelding = sykmelding,
                    personNrPasient = "1231231",
                    tlfPasient = "1323423424",
                    personNrLege = "123134",
                    navLogId = "4d3fad98-6c40-47ec-99b6-6ca7c98aa5ad",
                    msgId = "06b2b55f-c2c5-4ee0-8e0a-6e252ec2a550",
                    legekontorOrgNr = "444333",
                    legekontorOrgName = "Helese sentar",
                    legekontorHerId = "33",
                    legekontorReshId = "1313",
                    mottattDato = LocalDateTime.now(),
                    rulesetVersion = "2",
                    fellesformat = "",
                    tssid = "13415"
            )

            receivedSykmelding.sykmelding.perioder.sortedSykmeldingPeriodeFOMDate().first().fom shouldEqual LocalDate.now()
            receivedSykmelding.sykmelding.perioder.sortedSykmeldingPeriodeTOMDate().first().tom shouldEqual LocalDate.now()

        }

    }

})