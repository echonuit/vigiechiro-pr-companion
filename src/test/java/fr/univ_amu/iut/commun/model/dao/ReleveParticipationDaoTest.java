package fr.univ_amu.iut.commun.model.dao;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.api.MeteoDepot;
import fr.univ_amu.iut.commun.model.ReleveParticipation;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Le relevé de ce que la plateforme portait à la dernière lecture ([ReleveParticipationDao], #4706).
///
/// Il sert de **base** de comparaison, et il ne dit pas ce qui est vrai : il dit ce que nous avions
/// vu. Les cas ci-dessous portent sur ce qu'il retient et sur ce qu'il oublie.
class ReleveParticipationDaoTest {

    private static final String PARTICIPATION = "6a4961f587bc8dba39481180";

    @TempDir
    Path dossier;

    private ReleveParticipationDao dao;
    private Long idPassage;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier.resolve("ws")));
        new MigrationSchema(source).migrer();
        Long idPoint = JeuDeDonneesPassage.dans(source)
                .utilisateur("u-1")
                .carre("640380")
                .nomSite("Étang")
                .point("Z1")
                .position(43.5, 5.4)
                .enregistreur("1925492")
                .semerSiteEtPoint()
                .idPoint();
        idPassage = JeuDeDonneesPassage.dans(source)
                .utilisateur("u-1")
                .surLePoint(idPoint)
                .enregistreur("1925492")
                .nuit(1, 2026, "2026-04-22")
                .heures("20:25:00", "07:47:00")
                .statut(StatutWorkflow.DEPOSE)
                .semerPassage()
                .idPassage();
        dao = new ReleveParticipationDao(source);
    }

    @Test
    @DisplayName("aucun relevé au départ : sans base, la question du conflit reste sans réponse")
    void aucun_releve_au_depart() {
        assertThat(dao.pour(idPassage)).isEmpty();
    }

    @Test
    @DisplayName("enregistrer puis relire : les dates, la météo et le dictionnaire matériel distant")
    void enregistrer_et_relire() {
        dao.enregistrer(new ReleveParticipation(
                idPassage,
                PARTICIPATION,
                "Wed, 22 Apr 2026 18:25:00 GMT",
                "Thu, 23 Apr 2026 05:47:00 GMT",
                new MeteoDepot("FAIBLE", "0-25"),
                Map.of("micro0_type", "ICS", "micro1_type", "SMX"),
                "2026-08-29T09:30:00"));

        ReleveParticipation relu = dao.pour(idPassage).orElseThrow();

        assertThat(relu.participationId()).isEqualTo(PARTICIPATION);
        assertThat(relu.dateDebut()).isEqualTo("Wed, 22 Apr 2026 18:25:00 GMT");
        assertThat(relu.dateFin()).isEqualTo("Thu, 23 Apr 2026 05:47:00 GMT");
        assertThat(relu.meteo()).isEqualTo(new MeteoDepot("FAIBLE", "0-25"));
        // La cle micro1_type n'est pas des notres : le releve garde le dictionnaire du serveur entier,
        // sans quoi il ne pourrait pas dire qu'un champ hors de notre portee a bouge.
        assertThat(relu.configuration()).containsEntry("micro1_type", "SMX");
        assertThat(relu.releveLe()).isEqualTo("2026-08-29T09:30:00");
    }

    @Test
    @DisplayName("le relevé est ÉCRASÉ : on retient ce que la plateforme porte, pas ce qu'elle portait")
    void releve_ecrase() {
        dao.enregistrer(new ReleveParticipation(
                idPassage, PARTICIPATION, null, null, new MeteoDepot("NUL", "0-25"), Map.of(), "2026-08-29T09:30:00"));

        dao.enregistrer(new ReleveParticipation(
                idPassage,
                PARTICIPATION,
                null,
                null,
                new MeteoDepot("FORT", "75-100"),
                Map.of(),
                "2026-08-29T10:15:00"));

        ReleveParticipation relu = dao.pour(idPassage).orElseThrow();
        assertThat(relu.meteo()).isEqualTo(new MeteoDepot("FORT", "75-100"));
        assertThat(relu.releveLe()).isEqualTo("2026-08-29T10:15:00");
        assertThat(dao.compter()).as("un seul relevé par passage").isEqualTo(1);
    }

    @Test
    @DisplayName("#4768 : les températures survivent à l'aller-retour, comme le vent et la couverture")
    void temperatures_survivent() {
        dao.enregistrer(new ReleveParticipation(
                idPassage,
                PARTICIPATION,
                null,
                null,
                new MeteoDepot("FAIBLE", "0-25", 12, 5),
                Map.of(),
                "2026-08-29T09:30:00"));

        MeteoDepot relue = dao.pour(idPassage).orElseThrow().meteo();

        // Sans elles, la comparaison de #4707 les voit toujours divergentes et bloque tout envoi.
        assertThat(relue.temperatureDebut()).isEqualTo(12);
        assertThat(relue.temperatureFin()).isEqualTo(5);
    }

    @Test
    @DisplayName("une météo absente chez eux se relit absente, et non comme un bloc vide")
    void meteo_absente_reste_absente() {
        dao.enregistrer(
                new ReleveParticipation(idPassage, PARTICIPATION, null, null, null, Map.of(), "2026-08-29T09:30:00"));

        assertThat(dao.pour(idPassage).orElseThrow().meteo()).isNull();
    }
}
