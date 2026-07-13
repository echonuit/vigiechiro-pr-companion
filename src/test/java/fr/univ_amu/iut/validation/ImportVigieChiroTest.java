package fr.univ_amu.iut.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.DonneeVigieChiro;
import fr.univ_amu.iut.commun.api.EtatTraitement;
import fr.univ_amu.iut.commun.api.ObservationVigieChiro;
import fr.univ_amu.iut.commun.api.ParticipationVigieChiro;
import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.api.TraitementVigieChiro;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.validation.model.BilanImport;
import fr.univ_amu.iut.validation.model.ImportVigieChiro;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Orchestration de l'**import VigieChiro** ([ImportVigieChiro], axe 4.2) : résolution de la participation
/// rattachée au passage → récupération des `donnees` → import, sur client + DAO + service mockés.
@ExtendWith(MockitoExtension.class)
class ImportVigieChiroTest {

    private static final long ID_PASSAGE = 42L;
    private static final String PARTICIPATION = "6a4961f587bc8dba39481180";

    @Mock
    ClientVigieChiro client;

    @Mock
    LienVigieChiroDao liens;

    @Mock
    ServiceValidation service;

    private ImportVigieChiro importateur;

    @Mock
    private TraitementVigieChiro traitement;

    @BeforeEach
    void preparer() {
        importateur = new ImportVigieChiro(client, traitement, liens, service);
    }

    @Test
    @DisplayName("importer : résout la participation rattachée, récupère les donnees et importe")
    void importer_de_bout_en_bout() {
        List<DonneeVigieChiro> donnees = List.of(new DonneeVigieChiro("d1", "Car-Z41_000", List.of(observation())));
        BilanImport attendu = new BilanImport(null, 1, 0, 0);
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.of(PARTICIPATION));
        when(client.donnees(PARTICIPATION)).thenReturn(donnees);
        when(service.importerDepuisVigieChiro(ID_PASSAGE, donnees, false)).thenReturn(attendu);

        assertThat(importateur.importer(ID_PASSAGE, false)).isSameAs(attendu);
    }

    @Test
    @DisplayName("estRattache : reflète la présence d'un lien participation pour le passage")
    void est_rattache() {
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42"))
                .thenReturn(Optional.of(PARTICIPATION), Optional.empty());

        assertThat(importateur.estRattache(ID_PASSAGE)).isTrue();
        assertThat(importateur.estRattache(ID_PASSAGE)).isFalse();
    }

    @Test
    @DisplayName("passage non rattaché → refus dur, aucun appel réseau")
    void non_rattache_leve() {
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> importateur.importer(ID_PASSAGE, false))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("rattaché");
        verify(client, never()).donnees(eq(PARTICIPATION));
    }

    @Test
    @DisplayName("#1264 : analyse EN COURS → on le dit, et on ne parle plus d'erreur (il n'y a qu'à attendre)")
    void rien_a_importer_car_analyse_en_cours() {
        // Le serveur répond « 200, liste vide » tant que le calcul tourne : ce n'est pas une panne, c'est un
        // état. Le message d'avant confondait tout (« pas encore terminée, OU connexion indisponible »).
        armerParticipationSansDonnees();
        when(traitement.etat(PARTICIPATION)).thenReturn(etat(EtatTraitement.EN_COURS));

        assertThatThrownBy(() -> importateur.importer(ID_PASSAGE, false))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("en cours")
                .hasMessageContaining("dizaines de minutes");
        aucunImport();
    }

    @Test
    @DisplayName("#1264 : analyse JAMAIS LANCÉE → on renvoie vers l'étape qui la lance")
    void rien_a_importer_car_jamais_lancee() {
        armerParticipationSansDonnees();
        when(traitement.etat(PARTICIPATION)).thenReturn(Traitement.absent());

        assertThatThrownBy(() -> importateur.importer(ID_PASSAGE, false))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("jamais été lancée")
                .hasMessageContaining("Préparer le dépôt");
        aucunImport();
    }

    @Test
    @DisplayName("#1264 : analyse EN ÉCHEC → le motif du serveur est restitué (première ligne)")
    void rien_a_importer_car_analyse_en_echec() {
        armerParticipationSansDonnees();
        when(traitement.etat(PARTICIPATION))
                .thenReturn(new Traitement(
                        EtatTraitement.ERREUR, null, null, null, "RuntimeError: boum\n  at ligne 12", 1));

        assertThatThrownBy(() -> importateur.importer(ID_PASSAGE, false))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("a échoué")
                .hasMessageContaining("RuntimeError: boum")
                .hasMessageNotContaining("at ligne 12");
        aucunImport();
    }

    @Test
    @DisplayName("#1264 : analyse TERMINÉE mais aucune observation → c'est le dépôt qu'il faut vérifier")
    void rien_a_importer_alors_que_l_analyse_est_finie() {
        // Cas anormal : le calcul est fini et pourtant le serveur ne renvoie rien. Ce n'est plus une question
        // de patience — quelque chose s'est perdu en route.
        armerParticipationSansDonnees();
        when(traitement.etat(PARTICIPATION)).thenReturn(etat(EtatTraitement.FINI));

        assertThatThrownBy(() -> importateur.importer(ID_PASSAGE, false))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("aucune observation")
                .hasMessageContaining("Vérifiez le dépôt");
        aucunImport();
    }

    private void armerParticipationSansDonnees() {
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.of(PARTICIPATION));
        when(client.donnees(PARTICIPATION)).thenReturn(List.of());
    }

    private void aucunImport() {
        verify(service, never())
                .importerDepuisVigieChiro(eq(ID_PASSAGE), org.mockito.ArgumentMatchers.any(), eq(false));
    }

    private static Traitement etat(EtatTraitement etat) {
        return new Traitement(etat, null, "2026-07-13T09:00:00+00:00", null, null, null);
    }

    @Test
    @DisplayName("participationsDisponibles délègue au client ; rattacher stocke le lien participation")
    void participations_et_rattachement() {
        List<ParticipationVigieChiro> parts = List.of(new ParticipationVigieChiro("6a49", "Z41", "2026-07-03", "Site"));
        when(client.mesParticipations()).thenReturn(parts);

        assertThat(importateur.participationsDisponibles()).isSameAs(parts);

        importateur.rattacher(ID_PASSAGE, PARTICIPATION);
        verify(liens).upsert(new LienVigieChiro(LienVigieChiro.ENTITE_PASSAGE, "42", PARTICIPATION));
    }

    private static ObservationVigieChiro observation() {
        return new ObservationVigieChiro(0, "Pipkuh", 0.99, 44.0, 0.8, 4.7, null, null, null);
    }
}
