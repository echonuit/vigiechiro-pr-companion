package fr.univ_amu.iut.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.validation.model.MessageObservation;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.PublicationMessage;
import fr.univ_amu.iut.validation.model.dao.MessageObservationDao;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// **Poster un message au validateur** ([PublicationMessage], #1418) : la seule écriture **définitive** de
/// ce chantier : le serveur ajoute par `$push` et n'offre aucune route de suppression.
///
/// Tous ces tests tournent sur une **API bouchonnée**. Aucune écriture réelle n'est tirée : une sonde
/// live sur cette route serait irréversible, et reste soumise à un accord explicite.
///
/// Ce qu'ils protègent : que **rien ne parte** quand ça ne doit pas partir, et que **rien ne soit écrit en
/// base** quand le serveur a refusé. Un message que l'observateur croirait envoyé et que le validateur ne
/// verrait jamais serait la perte silencieuse de l'EPIC #1154, prise à l'envers.
class PublicationMessageTest {

    private static final String DONNEE = "d-1";

    @TempDir
    Path dossier;

    private ClientVigieChiro client;
    private ObservationDao observations;
    private MessageObservationDao messages;
    private PublicationMessage publication;

    /// Séquence d'écoute à laquelle rattacher les observations (`observation.sequence_id` est NOT NULL).
    private Long idSequence;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        client = mock(ClientVigieChiro.class);
        observations = new ObservationDao(source);
        messages = new MessageObservationDao(source);
        publication = new PublicationMessage(client, observations, messages, new UniteDeTravail(source));
        idSequence = semerUneSequence(source);
    }

    /// Le minimum pour qu'une observation existe : un site, un point, une nuit, une séquence.
    private static Long semerUneSequence(SourceDeDonnees source) {
        // La chaîne entière est celle que la fixture pose par défaut, au nom de séquence près.
        return JeuDeDonneesPassage.dans(source)
                .utilisateur("u-1")
                .carre("130711")
                .nomSite("Test")
                .point("Z41")
                .enregistreur("1925492")
                .nuit(1, 2026, "2026-07-03")
                .statut(StatutWorkflow.IMPORTE)
                .semer()
                .ajouterSequence();
    }

    @Test
    @DisplayName("#1418 : le serveur accepte → le message part, PUIS il est écrit en base, en queue du fil")
    void message_accepte() {
        Long idObservation = observationAncree();
        when(client.posterMessage(anyString(), anyInt(), anyString())).thenReturn(ReponseApi.succes("ok"));

        ReponseApi<String> reponse = publication.poster(idObservation, "  Je doute de ce Pipkuh.  ");

        assertThat(reponse).isInstanceOf(ReponseApi.Succes.class);
        verify(client)
                .posterMessage(DONNEE, 3, "Je doute de ce Pipkuh."); // ancrage positionnel (#1139) + texte détouré
        assertThat(messages.filDeLObservation(idObservation))
                .as("le fil local montre immédiatement ce qui vient d'être dit, sans attendre le prochain import")
                .extracting(MessageObservation::texte)
                .containsExactly("Je doute de ce Pipkuh.");
        assertThat(messages.filDeLObservation(idObservation).getFirst().date())
                .as("ni auteur ni date ne sont inventés : le serveur les posera, et le prochain import"
                        + " réécrira le fil avec sa version, qui fait foi")
                .isNull();
    }

    @Test
    @DisplayName("#1418 : le serveur REFUSE → RIEN n'est écrit en base. Un message que l'observateur"
            + " croirait envoyé et que le validateur ne verrait jamais serait le pire des cas")
    void message_refuse_rien_en_base() {
        Long idObservation = observationAncree();
        when(client.posterMessage(anyString(), anyInt(), anyString()))
                .thenReturn(ReponseApi.refuse(404, "donnée introuvable (ancrage périmé)"));

        ReponseApi<String> reponse = publication.poster(idObservation, "Perdu d'avance.");

        assertThat(reponse.echec()).isPresent();
        assertThat(messages.filDeLObservation(idObservation))
                .as("le serveur d'abord, la base ensuite : sur un refus, la base ne bouge pas")
                .isEmpty();
    }

    @Test
    @DisplayName("#1418 : détection sans ancrage Vigie-Chiro (import CSV, saisie manuelle) → refus SANS"
            + " réseau : il n'y a personne à qui parler")
    void sans_ancrage_aucun_appel() {
        Long idObservation = observationSansAncrage();

        ReponseApi<String> reponse = publication.poster(idObservation, "Bonjour ?");

        assertThat(reponse.echec()).isPresent();
        verify(client, never()).posterMessage(anyString(), anyInt(), anyString());
        assertThat(publication.pourquoiImpossible(idObservation))
                .as("et l'IHM sait POURQUOI : elle désactive la saisie en le disant (affordance #789)")
                .isPresent()
                .get(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("n'existe pas sur Vigie-Chiro");
    }

    @Test
    @DisplayName("#1418 : message vide → refus sans réseau. On n'envoie pas du vide sur une route qui ne"
            + " sait pas revenir en arrière")
    void message_vide_aucun_appel() {
        Long idObservation = observationAncree();

        assertThat(publication.poster(idObservation, "   ").echec()).isPresent();
        verify(client, never()).posterMessage(anyString(), anyInt(), anyString());
    }

    @Test
    @DisplayName(
            "#1418 : une observation ancrée peut recevoir un message, rien à expliquer, la saisie" + " est ouverte")
    void observation_ancree_est_ecrivable() {
        assertThat(publication.pourquoiImpossible(observationAncree())).isEmpty();
    }

    // --- Fixtures ----------------------------------------------------------------------------------

    /// Une observation **ancrée** sur la plateforme (`_id` de la donnée + indice brut, V21 / #1139) : la
    /// seule qui puisse porter un message.
    private Long observationAncree() {
        return observations.insert(observation(DONNEE, 3)).id();
    }

    /// Une observation issue d'un CSV Tadarida ou d'une saisie manuelle : **aucun ancrage**, donc aucune
    /// existence côté serveur.
    private Long observationSansAncrage() {
        return observations.insert(observation(null, null)).id();
    }

    private Observation observation(String idDonnee, Integer indice) {
        return new Observation(
                null,
                idSequence,
                0.1,
                0.4,
                45,
                "Pipkuh",
                0.9,
                null,
                null,
                null,
                null,
                false,
                ModeValidation.NON_VALIDE,
                null,
                false,
                idDonnee,
                indice,
                null,
                null,
                null);
    }
}
