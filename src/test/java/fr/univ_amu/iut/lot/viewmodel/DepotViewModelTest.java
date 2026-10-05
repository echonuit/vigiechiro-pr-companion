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
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.lot.model.BilanDepot;
import fr.univ_amu.iut.lot.model.DepotUnite;
import fr.univ_amu.iut.lot.model.DepotVigieChiro;
import fr.univ_amu.iut.lot.model.EchecUnite;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.lot.model.SourceDepot;
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

/// Tests unitaires de [DepotViewModel] (#142) : coordination du téléversement d'une nuit, résolution des
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
    @DisplayName("televerser dépose les fichiers résolus par ServiceLot et renvoie le bilan")
    void televerser_depose_les_fichiers_par_defaut() {
        SourceDepot archives = SourceDepot.desFichiers(
                List.of(Path.of("/ws/session-42/depot/Car-1.zip"), Path.of("/ws/session-42/depot/Car-2.zip")));
        when(service.sourceDepotParDefaut(ID_PASSAGE)).thenReturn(archives);
        when(depot.deposer(eq(ID_PASSAGE), any(), any(), any())).thenReturn(new BilanDepot("part-1", 2, List.of()));

        BilanDepot bilan = new DepotViewModel(service, Optional.of(depot)).televerser(ID_PASSAGE);

        assertThat(bilan.participationId()).isEqualTo("part-1");
        assertThat(bilan.deposees()).isEqualTo(2);
        verify(depot).deposer(eq(ID_PASSAGE), eq(archives), any(), any());
    }

    @Test
    @DisplayName("#1044 : demanderAnnulation() bascule le drapeau lu par le moteur ; marquerEnCours() le réarme")
    void annulation_cooperative_du_vm() {
        when(service.sourceDepotParDefaut(ID_PASSAGE))
                .thenReturn(SourceDepot.desFichiers(List.of(Path.of("/ws/depot/Car-1.zip"))));
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

        // Depuis #2653 la fin de dépôt passe par la bande, pas par le bandeau d'une ligne. Ce qui est
        // vérifié ici reste le même fait : l'interruption est PORTÉE, sans quoi une tentative sans échec
        // se lirait comme un dépôt complet.
        DepotViewModel.FinDepot fin = vm.finDepotProperty().get();
        assertThat(fin).isNotNull();
        assertThat(fin.plan().interrompu()).isTrue();
        assertThat(fin.plan().enLigne()).isEqualTo(1);
        assertThat(fin.plan().unitesDuPlan()).isEqualTo(2);
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
        when(service.sourceDepotParDefaut(ID_PASSAGE))
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
        assertThat(vm.retourLancementProperty().get().texte()).contains("Analyse demandée à Vigie-Chiro");
        assertThat(vm.retourLancementProperty().get().severite()).isEqualTo(Severite.SUCCES);

        // « Déjà en cours » n'est PAS un échec : le serveur travaille, il n'y a qu'à attendre. Avant
        // #1261, ce cas s'affichait comme un échec, avec un point d'interrogation en prime.
        vm.restituerLancement(ResultatLancement.dejaLance(traitement(EtatTraitement.EN_COURS)));
        assertThat(vm.retourLancementProperty().get().texte())
                .contains("déjà demandée")
                .doesNotContain("Échec");
        assertThat(vm.retourLancementProperty().get().severite())
                .as("#1890 : un traitement déjà lancé n'est pas un échec, il n'y a rien à faire")
                .isEqualTo(Severite.INFO);

        vm.restituerLancement(ResultatLancement.relanceBloquee(traitement(EtatTraitement.FINI)));
        assertThat(vm.retourLancementProperty().get().texte()).contains("déjà été analysée", "effacerait");
        assertThat(vm.retourLancementProperty().get().severite())
                .as("#1890 : la relance bloquée protège les observations, elle ne rapporte pas une panne")
                .isEqualTo(Severite.INFO);

        vm.restituerLancement(ResultatLancement.refuse(403, "interdit"));
        assertThat(vm.retourLancementProperty().get().texte())
                .as("#5682 : le motif du refus se dit, comme en ligne de commande")
                .isEqualTo("Vigie-Chiro a refusé de lancer l'analyse : HTTP 403 interdit.");
        assertThat(vm.retourLancementProperty().get().severite()).isEqualTo(Severite.ERREUR);

        vm.restituerLancement(ResultatLancement.injoignable());
        assertThat(vm.retourLancementProperty().get().texte()).contains("injoignable");
    }

    /// Le résultat d'un lancement appartient au lancement et au passage qui l'ont produit (#5682) : un
    /// nouveau lancement l'efface pendant qu'il travaille, et un autre passage ouvert ne le reprend pas.
    @Test
    @DisplayName("#5682 : le résultat du lancement s'efface au lancement suivant et à l'ouverture d'un passage")
    void le_resultat_du_lancement_s_efface() {
        DepotViewModel vm = new DepotViewModel(service, Optional.of(depot));

        vm.restituerLancement(ResultatLancement.accepte());
        vm.marquerLancementEnCours();
        assertThat(vm.retourLancementProperty().get().texte())
                .as("un lancement en cours")
                .isEmpty();

        vm.restituerLancement(ResultatLancement.accepte());
        vm.rehydrater(43L);
        assertThat(vm.retourLancementProperty().get().texte())
                .as("un autre passage ouvert")
                .isEmpty();
    }

    @Test
    @DisplayName("#1543 : marquerLancementEnCours annonce « en cours » (bouton grisé), restituerLancement le lève")
    void lancement_en_cours_visible_puis_leve() {
        DepotViewModel vm = new DepotViewModel(service, Optional.of(depot));

        vm.marquerLancementEnCours();
        assertThat(vm.enCoursProperty().get())
                .as("le POST de lancement ne part plus sans retour visible")
                .isTrue();
        // #1886 : l'annonce du travail en cours a quitté le retour d'opération (fermable, et réservé aux
        // résultats) pour un état propre, que la barre de statut rend.
        assertThat(vm.lancementEnCoursProperty().get())
                .as("le lancement s'annonce par son état, distinct du téléversement")
                .isTrue();
        assertThat(vm.retourProperty().get().texte())
                .as("un travail en cours n'est pas un résultat : le retour reste vide")
                .isEmpty();

        vm.restituerLancement(ResultatLancement.accepte());
        assertThat(vm.enCoursProperty().get())
                .as("le lancement abouti relâche le bouton")
                .isFalse();
        assertThat(vm.lancementEnCoursProperty().get())
                .as("l'annonce s'éteint avec le lancement")
                .isFalse();
        assertThat(vm.retourLancementProperty().get().texte())
                .as("#5682 : le résultat va dans la zone de l'étape ④")
                .isNotEmpty();
        assertThat(vm.retourProperty().get().texte())
                .as("#5682 : et pas au bandeau du haut, hors de vue au moment du clic")
                .isEmpty();
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
        assertThat(vm.retourProperty().get().texte()).contains("réinitialisé");
    }

    @Test
    @DisplayName("#2653 : le compte rendu ne dit jamais MOINS que ce que la tentative vient de déposer")
    void le_plan_ne_sous_estime_pas_ce_qui_vient_de_partir() {
        DepotViewModel vm = new DepotViewModel(service, Optional.of(depot));
        // Table de suivi vide : c'est le cas d'un relais en retard sur le moteur. Sans plancher, la bande
        // annoncerait « 0 déposées » juste après avoir mis trois archives en ligne.
        vm.appliquerBilan(new BilanDepot("part-1", 3, List.of()));

        assertThat(vm.finDepotProperty().get().plan().enLigne()).isEqualTo(3);
        assertThat(vm.finDepotProperty().get().plan().unitesDuPlan())
                .as("le total suit le plancher, sinon la ventilation ne serait pas exhaustive")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("cycle d'état IHM : en cours → bilan complet / partiel / échec")
    void cycle_etat_ihm() {
        DepotViewModel vm = new DepotViewModel(service, Optional.of(depot));
        assertThat(vm.enCoursProperty().get()).isFalse();

        vm.marquerEnCours();
        assertThat(vm.enCoursProperty().get()).isTrue();
        // #1886 : le téléversement s'annonce par son état (la barre de statut en rend le décompte vivant),
        // plus par un message de retour - le bandeau est fermable et réservé aux résultats.
        assertThat(vm.retourProperty().get().texte())
                .as("un travail en cours n'est pas un résultat")
                .isEmpty();

        vm.appliquerBilan(new BilanDepot("p", 5, List.of()));
        assertThat(vm.enCoursProperty().get()).isFalse();
        // #2653 : le résultat n'est plus une phrase dans le bandeau, c'est le bilan entier que la vue
        // traduira. Le bandeau reste au service des ERREURS et des autres opérations de l'écran.
        assertThat(vm.finDepotProperty().get().bilan().deposees()).isEqualTo(5);
        assertThat(vm.retourProperty().get().texte())
                .as("un dépôt réussi ne laisse plus de bandeau : la bande le dit mieux")
                .isEmpty();

        vm.appliquerBilan(new BilanDepot("p", 3, List.of(EchecUnite.rejouable("x.wav", "HTTP 503"))));
        // #1890 conservé, déplacé : un dépôt partiel ne ment ni dans un sens ni dans l'autre. Ce n'est
        // plus la sévérité d'un bandeau qui le porte mais celle du compte rendu, décidée à la traduction.
        assertThat(vm.finDepotProperty().get().bilan().echecs())
                .extracting(EchecUnite::identifiantUnite)
                .containsExactly("x.wav");

        vm.echec("Token expiré");
        assertThat(vm.enCoursProperty().get()).isFalse();
        assertThat(vm.retourProperty().get().texte()).contains("Token expiré");
        assertThat(vm.retourProperty().get().severite()).isEqualTo(Severite.ERREUR);
    }

    /// Les séquences refusées sans recours (#5867) sont un état du dépôt : c'est ce modèle de vue qui les
    /// porte, relues dans le plan enregistré à l'ouverture d'une nuit et après chaque téléversement.
    @Test
    @DisplayName("#5867 : rehydrater() relit dans le plan les séquences refusées sans recours")
    void rehydrater_relit_les_sequences_refusees_sans_recours() {
        when(service.unitesDepot(ID_PASSAGE)).thenReturn(List.of());
        when(service.sequencesRefuseesSansRecours(ID_PASSAGE)).thenReturn(2);
        DepotViewModel vm = new DepotViewModel(service, Optional.of(depot));

        vm.rehydrater(ID_PASSAGE);

        assertThat(vm.sequencesRefuseesSansRecoursProperty().get()).isEqualTo(2);
    }

    @Test
    @DisplayName("#5867 : après un téléversement, le compte se relit sans rouvrir la nuit")
    void le_compte_se_relit_apres_un_televersement() {
        when(service.sequencesRefuseesSansRecours(ID_PASSAGE)).thenReturn(3);
        DepotViewModel vm = new DepotViewModel(service, Optional.of(depot));
        assertThat(vm.sequencesRefuseesSansRecoursProperty().get()).isZero();

        vm.relireLesRefus(ID_PASSAGE);

        assertThat(vm.sequencesRefuseesSansRecoursProperty().get()).isEqualTo(3);
    }

    @Test
    @DisplayName("#5867 : une autre nuit ouverte ne garde pas les refus de la précédente")
    void une_autre_nuit_ne_garde_pas_les_refus_de_la_precedente() {
        when(service.unitesDepot(ID_PASSAGE)).thenReturn(List.of());
        when(service.unitesDepot(43L)).thenReturn(List.of());
        when(service.sequencesRefuseesSansRecours(ID_PASSAGE)).thenReturn(2);
        when(service.sequencesRefuseesSansRecours(43L)).thenReturn(0);
        DepotViewModel vm = new DepotViewModel(service, Optional.of(depot));
        vm.rehydrater(ID_PASSAGE);

        vm.rehydrater(43L);

        assertThat(vm.sequencesRefuseesSansRecoursProperty().get()).isZero();
    }
}
