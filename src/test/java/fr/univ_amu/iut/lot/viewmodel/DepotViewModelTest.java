package fr.univ_amu.iut.lot.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.EtatTraitement;
import fr.univ_amu.iut.commun.api.IssueLancement;
import fr.univ_amu.iut.commun.api.ResultatLancement;
import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.lot.model.BilanDepot;
import fr.univ_amu.iut.lot.model.DepotUnite;
import fr.univ_amu.iut.lot.model.DepotVigieChiro;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.lot.model.StatutDepotUnite;
import fr.univ_amu.iut.lot.model.TypeDepotUnite;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Tests unitaires de [DepotViewModel] (#142) : coordination du téléversement d'une nuit — résolution des
/// séquences via [ServiceLot], dépôt via [DepotVigieChiro], avec dépôt **optionnel** (indisponible hors
/// application connectée). DAO + client mockés, aucun réseau.
@ExtendWith(MockitoExtension.class)
class DepotViewModelTest {

    private static final long ID_PASSAGE = 42L;

    @Mock
    private ServiceLot service;

    @Mock
    private DepotVigieChiro depot;

    @Test
    @DisplayName("disponible() reflète la présence du dépôt (Optional)")
    void disponible_selon_presence() {
        assertThat(new DepotViewModel(service, Optional.of(depot)).disponible()).isTrue();
        assertThat(new DepotViewModel(service, Optional.empty()).disponible()).isFalse();
    }

    @Test
    @DisplayName("televerser dépose les fichiers résolus par ServiceLot (ZIP par défaut) et renvoie le bilan")
    void televerser_depose_les_fichiers_par_defaut() {
        List<Path> archives =
                List.of(Path.of("/ws/session-42/depot/Car-1.zip"), Path.of("/ws/session-42/depot/Car-2.zip"));
        when(service.fichiersDepotParDefaut(ID_PASSAGE)).thenReturn(archives);
        when(depot.deposer(eq(ID_PASSAGE), any(), any(), any())).thenReturn(new BilanDepot("part-1", 2, List.of()));

        BilanDepot bilan = new DepotViewModel(service, Optional.of(depot)).televerser(ID_PASSAGE);

        assertThat(bilan.participationId()).isEqualTo("part-1");
        assertThat(bilan.deposees()).isEqualTo(2);
        verify(depot).deposer(eq(ID_PASSAGE), eq(archives), any(), any());
    }

    @Test
    @DisplayName("#1044 : demanderAnnulation() bascule le drapeau lu par le moteur ; marquerEnCours() le réarme")
    void annulation_cooperative_du_vm() {
        when(service.fichiersDepotParDefaut(ID_PASSAGE)).thenReturn(List.of(Path.of("/ws/depot/Car-1.zip")));
        DepotViewModel vm = new DepotViewModel(service, Optional.of(depot));
        // Le moteur (mocké) lit le drapeau comme le vrai : avant demande → false, après → true.
        when(depot.deposer(eq(ID_PASSAGE), any(), any(), any())).thenAnswer(invocation -> {
            java.util.function.BooleanSupplier annule = invocation.getArgument(2);
            assertThat(annule.getAsBoolean()).as("pas encore demandé").isFalse();
            vm.demanderAnnulation();
            assertThat(annule.getAsBoolean()).as("demande visible du moteur").isTrue();
            return new BilanDepot("part-1", 0, List.of());
        });

        vm.televerser(ID_PASSAGE);
        assertThat(vm.annulationDemandeeProperty().get()).isTrue();

        // Nouveau lancement : le drapeau est réarmé.
        vm.marquerEnCours();
        assertThat(vm.annulationDemandeeProperty().get()).isFalse();
    }

    @Test
    @DisplayName("#1044 : après annulation, le bilan restitue « interrompu » avec les compteurs de la table")
    void bilan_apres_annulation_est_distinct() {
        DepotViewModel vm = new DepotViewModel(service, Optional.empty());
        vm.suiviLignes()
                .planifier(List.of(
                        new DepotUnite(
                                1L,
                                ID_PASSAGE,
                                "a.wav",
                                TypeDepotUnite.WAV,
                                StatutDepotUnite.DEPOSE,
                                "obj-1",
                                null,
                                "2026-07-12T09:00:00"),
                        new DepotUnite(
                                2L,
                                ID_PASSAGE,
                                "b.wav",
                                TypeDepotUnite.WAV,
                                StatutDepotUnite.A_DEPOSER,
                                null,
                                null,
                                "2026-07-12T09:00:00")));
        vm.demanderAnnulation();

        // Bilan « sans échec » de la tentative : sans le drapeau, il serait pris pour un dépôt complet.
        vm.appliquerBilan(new BilanDepot("part-1", 1, List.of()));

        assertThat(vm.messageProperty().get())
                .contains("interrompu")
                .contains("1/2")
                .contains("Reprendre le dépôt");
        assertThat(vm.enCoursProperty().get()).isFalse();
    }

    @Test
    @DisplayName("#983 : rehydrater() reflète l'état persisté des unités dans la table de dépôt")
    void rehydrater_refile_l_etat_persiste() {
        when(service.unitesDepot(ID_PASSAGE))
                .thenReturn(List.of(
                        new DepotUnite(
                                1L,
                                ID_PASSAGE,
                                "Car-1.zip",
                                TypeDepotUnite.ZIP,
                                StatutDepotUnite.DEPOSE,
                                "obj-1",
                                null,
                                "2026-07-11T14:00:00"),
                        new DepotUnite(
                                2L,
                                ID_PASSAGE,
                                "Car-2.zip",
                                TypeDepotUnite.ZIP,
                                StatutDepotUnite.ECHEC,
                                null,
                                "HTTP 503",
                                "2026-07-11T14:00:00")));
        DepotViewModel vm = new DepotViewModel(service, Optional.empty());

        vm.rehydrater(ID_PASSAGE);

        assertThat(vm.suiviLignes().lignes()).hasSize(2);
        assertThat(vm.suiviLignes().resteAReprendreProperty().get())
                .as("un échec persiste : l'action devient « Retenter les échecs »")
                .isTrue();
    }

    @Test
    @DisplayName("refus du service (rien à déposer / archives à générer) propagé, aucun appel réseau")
    void televerser_propage_le_refus_du_service() {
        when(service.fichiersDepotParDefaut(ID_PASSAGE))
                .thenThrow(new RegleMetierException("Aucune séquence transformée à déposer pour ce passage."));

        DepotViewModel vm = new DepotViewModel(service, Optional.of(depot));

        assertThatThrownBy(() -> vm.televerser(ID_PASSAGE))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Aucune séquence");
        verify(depot, never()).deposer(any(), any(), any(), any());
    }

    @Test
    @DisplayName("dépôt indisponible (Optional vide, contexte de capture) → refus dur")
    void televerser_indisponible_leve() {
        DepotViewModel vm = new DepotViewModel(service, Optional.empty());

        assertThatThrownBy(() -> vm.televerser(ID_PASSAGE))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("indisponible");
    }

    @Test
    @DisplayName("#984 : lancerTraitement délègue au moteur ; indisponible → refus")
    void lancer_traitement_delegue_ou_refuse() {
        when(depot.lancerTraitement(ID_PASSAGE)).thenReturn(ResultatLancement.accepte());
        assertThat(new DepotViewModel(service, Optional.of(depot))
                        .lancerTraitement(ID_PASSAGE)
                        .issue())
                .isEqualTo(IssueLancement.ACCEPTE);

        assertThatThrownBy(() -> new DepotViewModel(service, Optional.empty()).lancerTraitement(ID_PASSAGE))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("indisponible");
    }

    @Test
    @DisplayName("#984 : participationLiee (propriété) : fausse au départ, vraie après réhydratation liée ou dépôt")
    void participation_liee_propriete() {
        when(service.unitesDepot(ID_PASSAGE)).thenReturn(List.of());
        when(depot.participationLiee(ID_PASSAGE)).thenReturn(true);

        DepotViewModel vm = new DepotViewModel(service, Optional.of(depot));
        assertThat(vm.participationLieeProperty().get()).isFalse();

        vm.rehydrater(ID_PASSAGE);
        assertThat(vm.participationLieeProperty().get()).isTrue();

        DepotViewModel apresDepot = new DepotViewModel(service, Optional.of(depot));
        apresDepot.appliquerBilan(new BilanDepot("p", 1, List.of()));
        assertThat(apresDepot.participationLieeProperty().get()).isTrue();
    }

    @Test
    @DisplayName("#1261 : restituerLancement dit ce qui s'est VRAIMENT passé, une issue à la fois")
    void restituer_lancement_message() {
        DepotViewModel vm = new DepotViewModel(service, Optional.of(depot));

        vm.restituerLancement(ResultatLancement.accepte());
        assertThat(vm.messageProperty().get()).contains("Traitement lancé");

        // « Déjà en cours » n'est PAS un échec : le serveur travaille, il n'y a qu'à attendre. Avant
        // #1261, ce cas s'affichait comme un échec, avec un point d'interrogation en prime.
        vm.restituerLancement(ResultatLancement.dejaLance(traitement(EtatTraitement.EN_COURS)));
        assertThat(vm.messageProperty().get()).contains("déjà en cours").doesNotContain("Échec");

        vm.restituerLancement(ResultatLancement.relanceBloquee(traitement(EtatTraitement.FINI)));
        assertThat(vm.messageProperty().get()).contains("déjà été analysée", "effacerait");

        vm.restituerLancement(ResultatLancement.refuse(403, "interdit"));
        assertThat(vm.messageProperty().get()).contains("refusé");

        vm.restituerLancement(ResultatLancement.injoignable());
        assertThat(vm.messageProperty().get()).contains("injoignable");
    }

    @Test
    @DisplayName("#1543 : marquerLancementEnCours annonce « en cours » (bouton grisé), restituerLancement le lève")
    void lancement_en_cours_visible_puis_leve() {
        DepotViewModel vm = new DepotViewModel(service, Optional.of(depot));

        vm.marquerLancementEnCours();
        assertThat(vm.enCoursProperty().get())
                .as("le POST de lancement ne part plus sans retour visible")
                .isTrue();
        assertThat(vm.messageProperty().get()).contains("Lancement");

        vm.restituerLancement(ResultatLancement.accepte());
        assertThat(vm.enCoursProperty().get())
                .as("le lancement abouti relâche le bouton")
                .isFalse();
    }

    /// Traitement serveur dans l'état voulu (les dates n'entrent pas en jeu dans les messages).
    private static Traitement traitement(EtatTraitement etat) {
        return new Traitement(etat, null, null, null, null, null);
    }

    @Test
    @DisplayName("#984 : reinitialiser délègue à ServiceLot, vide la table et informe")
    void reinitialiser_efface_et_informe() {
        when(service.unitesDepot(ID_PASSAGE)).thenReturn(List.of()); // après reset : plan vidé
        DepotViewModel vm = new DepotViewModel(service, Optional.of(depot));

        vm.reinitialiser(ID_PASSAGE);

        verify(service).reinitialiserDepot(ID_PASSAGE);
        assertThat(vm.suiviLignes().lignes()).isEmpty();
        assertThat(vm.messageProperty().get()).contains("réinitialisé");
    }

    @Test
    @DisplayName("cycle d'état IHM : en cours → bilan complet / partiel / échec")
    void cycle_etat_ihm() {
        DepotViewModel vm = new DepotViewModel(service, Optional.of(depot));
        assertThat(vm.enCoursProperty().get()).isFalse();

        vm.marquerEnCours();
        assertThat(vm.enCoursProperty().get()).isTrue();
        assertThat(vm.messageProperty().get()).contains("en cours");

        vm.appliquerBilan(new BilanDepot("p", 5, List.of()));
        assertThat(vm.enCoursProperty().get()).isFalse();
        assertThat(vm.messageProperty().get()).contains("5 fichier");

        vm.appliquerBilan(new BilanDepot("p", 3, List.of("x.wav")));
        assertThat(vm.messageProperty().get()).contains("échec");

        vm.echec("Token expiré");
        assertThat(vm.enCoursProperty().get()).isFalse();
        assertThat(vm.messageProperty().get()).isEqualTo("Token expiré");
    }
}
