package fr.univ_amu.iut.audit.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.audit.model.BilanRecuperabilite;
import fr.univ_amu.iut.audit.model.RapportAudit;
import fr.univ_amu.iut.audit.model.RecuperabiliteNuit;
import fr.univ_amu.iut.audit.model.ResultatReset;
import fr.univ_amu.iut.audit.model.ServiceRecuperabilite;
import fr.univ_amu.iut.audit.model.ServiceReset;
import fr.univ_amu.iut.audit.model.SourceAudio;
import fr.univ_amu.iut.commun.persistence.BilanSauvegarde;
import fr.univ_amu.iut.commun.persistence.ServiceSauvegarde;
import fr.univ_amu.iut.commun.view.Confirmateur;
import fr.univ_amu.iut.commun.view.ExecuteurTacheSynchrone;
import fr.univ_amu.iut.commun.view.NiveauNotification;
import fr.univ_amu.iut.commun.view.Notificateur;
import fr.univ_amu.iut.commun.view.OccupationChrome;
import fr.univ_amu.iut.commun.view.SelecteurFichier;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le **geste de reset** vu de l'IHM ([GesteReset], #1419), joué pour de vrai — jusqu'à la fermeture de
/// l'application (#1405 : les dialogues sont des doubles, la fermeture aussi).
///
/// L'enjeu de ces tests n'est pas que le reset marche : c'est qu'il ne parte **jamais** sans que
/// l'utilisateur ait lu ce qu'il allait perdre. Un « êtes-vous sûr ? » générique ne serait pas un
/// consentement — d'où l'insistance sur le **contenu** du message de confirmation.
///
/// Exécution synchrone de bout en bout ([ExecuteurTacheSynchrone]) ; le voile du chrome n'est pas
/// installé (contexte partiel), ce qui n'ôte aucune garantie.
class GesteResetTest {

    private static final Path SAUVEGARDES = Path.of("/tmp/sauvegardes");

    private final ServiceRecuperabilite recuperabilite = mock(ServiceRecuperabilite.class);
    private final ServiceReset reset = mock(ServiceReset.class);
    private final ServiceSauvegarde sauvegarde = mock(ServiceSauvegarde.class);
    private final OccupationChrome occupation =
            new OccupationChrome(new ExecuteurTacheSynchrone(), new NavigationViewModel());

    /// Ce que le confirmateur a **demandé** : c'est le texte que l'utilisateur lit avant de dire oui.
    private final List<String> confirmations = new ArrayList<>();

    private final List<String> annonces = new ArrayList<>();
    private final List<NiveauNotification> niveaux = new ArrayList<>();

    /// Nombre de fois où la fermeture de l'application a été demandée.
    private int fermetures;

    private Optional<Path> choix = Optional.of(SAUVEGARDES);
    private boolean confirme = true;

    private GesteReset geste;

    @BeforeEach
    void preparer() {
        when(sauvegarde.dossierParDefaut()).thenReturn(SAUVEGARDES);
        geste = new GesteReset(recuperabilite, reset, sauvegarde, occupation, () -> null, () -> fermetures++);
        geste.selecteur().definir(new SelecteurFichier() {
            @Override
            public Optional<Path> choisirDossier(String titre, Optional<Path> dossierInitial) {
                return choix;
            }

            @Override
            public Optional<Path> choisirFichier(
                    String titre, Optional<Path> dossierInitial, fr.univ_amu.iut.commun.view.FiltreFichier filtre) {
                return choix;
            }
        });
        geste.confirmateur().definir(nouveauConfirmateur());
        geste.notificateur().definir(nouveauNotificateur());
    }

    @Test
    @DisplayName("#1419 : la confirmation NOMME les nuits qui perdraient leur audio — on ne peut accepter"
            + " que ce qu'on a lu")
    void la_confirmation_nomme_ce_qui_serait_perdu() {
        when(recuperabilite.bilan()).thenReturn(bilanAvecPerte());
        when(reset.executer(any(), anyBoolean())).thenReturn(fait());

        geste.lancer();

        assertThat(confirmations)
                .singleElement()
                .as("le texte énumère la nuit perdue : c'est la différence entre une confirmation et un"
                        + " consentement")
                .asString()
                .contains("ne reviendra PAS")
                .contains("Car130711-2026-Pass1-Z41")
                .contains("passages archivés");
        verify(reset)
                .executer(
                        SAUVEGARDES,
                        true); // dire oui à CE texte-là, c'est accepter la perte : c'est ce qui arme le service
    }

    @Test
    @DisplayName("#1419 : annuler la confirmation n'exécute RIEN — et annuler le choix du dossier non plus")
    void annuler_annule_vraiment() {
        when(recuperabilite.bilan()).thenReturn(bilanAvecPerte());

        confirme = false;
        geste.lancer();

        choix = Optional.empty();
        confirme = true;
        geste.lancer();

        verify(reset, never()).executer(any(), anyBoolean());
        assertThat(fermetures).as("et l'application reste ouverte").isZero();
    }

    @Test
    @DisplayName("#1419 : reset réussi → l'application se ferme, parce que ses écrans tiennent encore"
            + " l'ancienne base en mémoire")
    void un_reset_reussi_ferme_l_application() {
        when(recuperabilite.bilan()).thenReturn(bilanAvecPerte());
        when(reset.executer(any(), anyBoolean())).thenReturn(fait());

        geste.lancer();

        assertThat(annonces)
                .singleElement()
                .asString()
                .contains("Base remise à neuf")
                .contains("relancez-la");
        assertThat(fermetures)
                .as("aucun écran ne doit pouvoir afficher un fantôme de l'ancienne base")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("#1419 : le service refuse (plateforme injoignable) → l'IHM le dit et NE ferme pas")
    void un_refus_est_affiche_et_ne_ferme_rien() {
        when(recuperabilite.bilan()).thenReturn(bilanAvecPerte());
        when(reset.executer(any(), anyBoolean()))
                .thenReturn(new ResultatReset.Refuse("VigieChiro ne répond pas.", bilanAvecPerte()));

        geste.lancer();

        assertThat(niveaux).containsExactly(NiveauNotification.AVERTISSEMENT);
        assertThat(annonces).singleElement().asString().contains("Rien n'a été modifié");
        assertThat(fermetures)
                .as("rien n'a été détruit : il n'y a aucune raison de fermer")
                .isZero();
    }

    @Test
    @DisplayName("#1419 : base vide → on le dit, sans rien proposer de détruire")
    void base_vide_rien_a_reinitialiser() {
        when(recuperabilite.bilan()).thenReturn(new BilanRecuperabilite(List.of()));

        geste.lancer();

        assertThat(annonces).singleElement().asString().contains("Rien à réinitialiser");
        assertThat(confirmations).isEmpty();
        verify(reset, never()).executer(any(), anyBoolean());
    }

    // --- Fixtures ----------------------------------------------------------------------------------

    private static BilanRecuperabilite bilanAvecPerte() {
        return new BilanRecuperabilite(List.of(new RecuperabiliteNuit(
                7L,
                "Car130711-2026-Pass1-Z41 (nuit du 2026-07-03)",
                SourceAudio.PERDU,
                0,
                12,
                "déposée en archive : le serveur n'a gardé aucun son")));
    }

    private static ResultatReset fait() {
        return new ResultatReset.Fait(
                new BilanSauvegarde(SAUVEGARDES, 1, List.of()),
                Path.of("/tmp/vigiechiro.db.avant-reset"),
                1,
                new RapportAudit(List.of()),
                List.of("Car130711-2026-Pass1-Z41 (nuit du 2026-07-03)"));
    }

    private Confirmateur nouveauConfirmateur() {
        return message -> {
            confirmations.add(message);
            return confirme;
        };
    }

    private Notificateur nouveauNotificateur() {
        return (niveau, entete, message) -> {
            niveaux.add(niveau);
            annonces.add(entete + " | " + message);
        };
    }
}
