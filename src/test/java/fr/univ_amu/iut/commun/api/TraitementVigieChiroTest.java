package fr.univ_amu.iut.commun.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Lancement du traitement serveur (#1261) : le cœur du sujet est **qualifier un refus**. Le serveur
/// renvoie `400` aussi bien quand il travaille déjà (bénin) que quand quelque chose ne va pas ; plutôt que
/// de lire son message, on **relit l'état** de la participation. Client mocké, aucun réseau.
class TraitementVigieChiroTest {

    private static final String PART = "part-1";
    private static final String CHEMIN = "/participations/part-1/compute";

    private final ClientVigieChiro client = mock(ClientVigieChiro.class);
    private final TraitementVigieChiro traitement = new TraitementVigieChiro(client);

    @Test
    @DisplayName("le serveur accepte (2xx) → ACCEPTE, sans relire l'état (rien à qualifier)")
    void lancement_accepte() {
        when(client.poster(eqChemin(), any())).thenReturn(ReponseApi.succes("{}"));

        assertThat(traitement.lancer(PART).issue()).isEqualTo(IssueLancement.ACCEPTE);
    }

    @Test
    @DisplayName("refus 400 + traitement EN COURS côté serveur → DEJA_LANCE (bénin : il n'y a qu'à attendre)")
    void refus_avec_traitement_en_cours_est_benin() {
        when(client.poster(eqChemin(), any())).thenReturn(ReponseApi.refuse(400, "{\"etat\": \"Already EN_COURS\"}"));
        when(client.participation(PART)).thenReturn(ReponseApi.succes(detail(EtatTraitement.EN_COURS)));

        ResultatLancement resultat = traitement.lancer(PART);

        assertThat(resultat.issue()).isEqualTo(IssueLancement.DEJA_LANCE);
        assertThat(resultat.traitement().etat()).isEqualTo(EtatTraitement.EN_COURS);
        assertThat(resultat.traitementEnRoute()).isTrue();
    }

    @Test
    @DisplayName("refus 400 mais AUCUN traitement en attente → REFUSE (là, c'est un vrai problème)")
    void refus_sans_traitement_en_attente_est_un_echec() {
        // Même code HTTP que le cas précédent : seule la relecture de l'état les départage. C'est
        // exactement ce que le booléen d'avant ne savait pas faire.
        when(client.poster(eqChemin(), any())).thenReturn(ReponseApi.refuse(400, "participation invalide"));
        when(client.participation(PART)).thenReturn(ReponseApi.succes(detail(null)));

        ResultatLancement resultat = traitement.lancer(PART);

        assertThat(resultat.issue()).isEqualTo(IssueLancement.REFUSE);
        assertThat(resultat.detail()).contains("400", "participation invalide");
    }

    @Test
    @DisplayName("le serveur ne répond pas (hors ligne ou non connecté) → INJOIGNABLE, pas « refusé »")
    void serveur_muet_est_injoignable() {
        when(client.poster(eqChemin(), any())).thenReturn(ReponseApi.injoignable("délai d'attente dépassé"));
        assertThat(traitement.lancer(PART).issue()).isEqualTo(IssueLancement.INJOIGNABLE);

        when(client.poster(eqChemin(), any())).thenReturn(ReponseApi.nonConnecte());
        assertThat(traitement.lancer(PART).issue()).isEqualTo(IssueLancement.INJOIGNABLE);
    }

    @Test
    @DisplayName("etat : le bloc traitement en Succes ; une lecture impossible garde son issue (#1284)")
    void etat_du_traitement() {
        when(client.participation(PART)).thenReturn(ReponseApi.succes(detail(EtatTraitement.FINI)));
        assertThat(traitement.etat(PART).enOptionnel().orElseThrow().resultatsDisponibles())
                .isTrue();

        // Avant #1284, tout ceci s'effondrait en Traitement.absent() : « jamais calculée » et
        // « plateforme injoignable » étaient indiscernables (l'aveu vivait dans SuiviTraitement).
        when(client.participation(PART)).thenReturn(ReponseApi.injoignable("délai d'attente dépassé"));
        assertThat(traitement.etat(PART)).isEqualTo(ReponseApi.injoignable("délai d'attente dépassé"));

        when(client.participation(PART)).thenReturn(ReponseApi.refuse(404, "inconnue"));
        assertThat(traitement.etat(PART)).isEqualTo(ReponseApi.refuse(404, "inconnue"));
    }

    @Test
    @DisplayName("refus 400 + état illisible au moment de la relecture → on s'en tient au refus initial")
    void refus_avec_etat_illisible_reste_un_refus() {
        when(client.poster(eqChemin(), any())).thenReturn(ReponseApi.refuse(400, "boom"));
        when(client.participation(PART)).thenReturn(ReponseApi.injoignable("délai d'attente dépassé"));

        ResultatLancement resultat = traitement.lancer(PART);

        assertThat(resultat.issue()).isEqualTo(IssueLancement.REFUSE);
        assertThat(resultat.detail()).contains("400", "boom");
    }

    private static String eqChemin() {
        return org.mockito.ArgumentMatchers.eq(CHEMIN);
    }

    private static String any() {
        return org.mockito.ArgumentMatchers.anyString();
    }

    /// Détail distant dont seul le bloc traitement compte ici.
    private static ParticipationDetail detail(EtatTraitement etat) {
        return new ParticipationDetail(
                PART, "e1", "Z41", null, null, null, Map.of(), new Traitement(etat, null, null, null, null, null));
    }
}
