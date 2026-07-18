package fr.univ_amu.iut.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.DonneeVigieChiro;
import fr.univ_amu.iut.commun.api.EtatTraitement;
import fr.univ_amu.iut.commun.api.ObservationVigieChiro;
import fr.univ_amu.iut.commun.api.ParticipationVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
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
        when(client.donnees(eq(PARTICIPATION), any())).thenReturn(ReponseApi.succes(donnees));
        when(service.importerDepuisVigieChiro(ID_PASSAGE, donnees, false)).thenReturn(attendu);

        assertThat(importateur.importer(ID_PASSAGE, false)).isSameAs(attendu);
    }

    @Test
    @DisplayName("importerRapide : le CSV exploitable est pris d'un coup, sans toucher aux donnees (#1838)")
    void importer_rapide_prend_le_csv() {
        BilanImport attendu = new BilanImport(null, 3, 0, 0);
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.of(PARTICIPATION));
        when(client.csvObservations(PARTICIPATION)).thenReturn(ReponseApi.succes(Optional.of("CSV")));
        when(service.nomsSequencesCsv("CSV")).thenReturn(List.of("Car-Z41_000.wav"));
        when(service.importerContenuCsv(ID_PASSAGE, "CSV", false)).thenReturn(attendu);

        assertThat(importateur.importerRapide(ID_PASSAGE, false, (page, total) -> {}))
                .isSameAs(attendu);
        // Le gain est là : pas une seule page de donnees. C'est ce que #1838 vient chercher.
        verify(client, never()).donnees(any(), any());
    }

    @Test
    @DisplayName("importerRapide : un RÉimport reste sur les donnees, il ne doit pas effacer l'avis validateur")
    void importer_rapide_reimport_reste_complet() {
        List<DonneeVigieChiro> donnees = List.of(new DonneeVigieChiro("d1", "Car-Z41_000", List.of(observation())));
        BilanImport attendu = new BilanImport(null, 1, 0, 0);
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.of(PARTICIPATION));
        when(client.donnees(eq(PARTICIPATION), any())).thenReturn(ReponseApi.succes(donnees));
        when(service.importerDepuisVigieChiro(ID_PASSAGE, donnees, true)).thenReturn(attendu);

        assertThat(importateur.importerRapide(ID_PASSAGE, true, (page, total) -> {}))
                .isSameAs(attendu);
        // Le CSV n'est même pas demandé : il ne porte ni l'avis du validateur (#1417) — que le remplacement
        // écraserait par du vide — ni les fils, que la suppression des observations emporte en cascade.
        // « Réimporter » veut dire « va chercher ce qui a changé côté serveur ».
        verify(client, never()).csvObservations(any());
    }

    @Test
    @DisplayName("importerRapide : CSV inexploitable → repli sur les donnees, l'import aboutit quand même")
    void importer_rapide_repli_sur_donnees() {
        List<DonneeVigieChiro> donnees = List.of(new DonneeVigieChiro("d1", "Car-Z41_000", List.of(observation())));
        BilanImport attendu = new BilanImport(null, 1, 0, 0);
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.of(PARTICIPATION));
        // Contenu présent mais sans aucune séquence exploitable : l'optimisation ne doit pas coûter
        // le résultat, on repasse par la voie complète.
        when(client.csvObservations(PARTICIPATION)).thenReturn(ReponseApi.succes(Optional.of("vide")));
        when(service.nomsSequencesCsv("vide")).thenReturn(List.of());
        when(client.donnees(eq(PARTICIPATION), any())).thenReturn(ReponseApi.succes(donnees));
        when(service.importerDepuisVigieChiro(ID_PASSAGE, donnees, false)).thenReturn(attendu);

        assertThat(importateur.importerRapide(ID_PASSAGE, false, (page, total) -> {}))
                .isSameAs(attendu);
    }

    @Test
    @DisplayName("importerRapide : route CSV absente (refus) → repli silencieux sur les donnees")
    void importer_rapide_repli_si_csv_refuse() {
        List<DonneeVigieChiro> donnees = List.of(new DonneeVigieChiro("d1", "Car-Z41_000", List.of(observation())));
        BilanImport attendu = new BilanImport(null, 1, 0, 0);
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.of(PARTICIPATION));
        when(client.csvObservations(PARTICIPATION)).thenReturn(ReponseApi.refuse(404, "not found"));
        when(client.donnees(eq(PARTICIPATION), any())).thenReturn(ReponseApi.succes(donnees));
        when(service.importerDepuisVigieChiro(ID_PASSAGE, donnees, false)).thenReturn(attendu);

        assertThat(importateur.importerRapide(ID_PASSAGE, false, (page, total) -> {}))
                .isSameAs(attendu);
    }

    @Test
    @DisplayName("importerRapide : passage non rattaché → refus explicite, aucun appel réseau")
    void importer_rapide_non_rattache() {
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> importateur.importerRapide(ID_PASSAGE, false, (page, total) -> {}))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("rattaché à aucune participation");
        verify(client, never()).csvObservations(any());
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
        verify(client, never()).donnees(eq(PARTICIPATION), any());
    }

    @Test
    @DisplayName("#1277 rejoué : un refus serveur (422) devient un message avec statut et corps, pas une liste vide")
    void refus_serveur_expose_statut_et_corps() {
        // La panne d'origine : max_results=1000 → 422, que le transport taisait. L'import affichait
        // « Aucun résultat Tadarida disponible » avec 4806 observations sur le serveur.
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.of(PARTICIPATION));
        when(client.donnees(eq(PARTICIPATION), any())).thenReturn(ReponseApi.refuse(422, "max_results depasse"));

        assertThatThrownBy(() -> importateur.importer(ID_PASSAGE, false))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("refusé")
                .hasMessageContaining("422")
                .hasMessageContaining("max_results depasse");
        aucunImport();
    }

    @Test
    @DisplayName("#1284 : plateforme injoignable (délai) → message actionnable, pas « aucun résultat »")
    void injoignable_dit_injoignable() {
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.of(PARTICIPATION));
        when(client.donnees(eq(PARTICIPATION), any())).thenReturn(ReponseApi.injoignable("délai d'attente dépassé"));

        assertThatThrownBy(() -> importateur.importer(ID_PASSAGE, false))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("injoignable")
                .hasMessageContaining("délai d'attente dépassé");
        aucunImport();
    }

    @Test
    @DisplayName("#1284 : non connecté → on demande le jeton, sans parler de panne")
    void non_connecte_demande_le_jeton() {
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.of(PARTICIPATION));
        when(client.donnees(eq(PARTICIPATION), any())).thenReturn(ReponseApi.nonConnecte());

        assertThatThrownBy(() -> importateur.importer(ID_PASSAGE, false))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("jeton");
        aucunImport();
    }

    @Test
    @DisplayName("#1264 : analyse EN COURS → on le dit, et on ne parle plus d'erreur (il n'y a qu'à attendre)")
    void rien_a_importer_car_analyse_en_cours() {
        // Le serveur répond « 200, liste vide » tant que le calcul tourne : ce n'est pas une panne, c'est un
        // état. Le message d'avant confondait tout (« pas encore terminée, OU connexion indisponible »).
        armerParticipationSansDonnees();
        when(traitement.etat(PARTICIPATION)).thenReturn(ReponseApi.succes(etat(EtatTraitement.EN_COURS)));

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
        when(traitement.etat(PARTICIPATION)).thenReturn(ReponseApi.succes(Traitement.absent()));

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
                .thenReturn(ReponseApi.succes(new Traitement(
                        EtatTraitement.ERREUR, null, null, null, "RuntimeError: boum\n  at ligne 12", 1)));

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
        when(traitement.etat(PARTICIPATION)).thenReturn(ReponseApi.succes(etat(EtatTraitement.FINI)));

        assertThatThrownBy(() -> importateur.importer(ID_PASSAGE, false))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("aucune observation")
                .hasMessageContaining("Vérifiez le dépôt");
        aucunImport();
    }

    private void armerParticipationSansDonnees() {
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.of(PARTICIPATION));
        when(client.donnees(eq(PARTICIPATION), any())).thenReturn(ReponseApi.succes(List.of()));
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
        when(client.mesParticipations()).thenReturn(ReponseApi.succes(parts));

        assertThat(importateur.participationsDisponibles()).isEqualTo(ReponseApi.succes(parts));

        importateur.rattacher(ID_PASSAGE, PARTICIPATION);
        verify(liens).upsert(new LienVigieChiro(LienVigieChiro.ENTITE_PASSAGE, "42", PARTICIPATION));
    }

    private static ObservationVigieChiro observation() {
        return new ObservationVigieChiro(0, "Pipkuh", 0.99, 44.0, 0.8, 4.7, null, null, null, null, null, List.of());
    }
}
