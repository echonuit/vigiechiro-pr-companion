package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.persistence.BilanSauvegarde;
import fr.univ_amu.iut.commun.persistence.ServiceSauvegarde;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Les **gestes** de sauvegarde et de restauration, joués pour de vrai (#1405).
///
/// Aucun des quatre n'était couvert, et le port [Notificateur] seul n'y suffisait pas : ces actions
/// **commencent** par un `DirectoryChooser` / `FileChooser` **natif**, qui fige un test headless
/// exactement comme un `Alert.showAndWait()`. Le test se serait arrêté à la première ligne. Il fallait
/// donc le troisième porteur, [SelecteurFichier] : les trois dialogues d'une action (désigner, confirmer,
/// rendre compte) deviennent des doubles, et le geste se vérifie **jusqu'à son effet**.
///
/// L'enjeu est le même que pour la purge, mais dans l'autre sens : la restauration **écrase** la base
/// locale. « Annuler annule vraiment » n'avait jamais été vérifié - ni sur le sélecteur, ni sur la
/// confirmation.
///
/// Exécution synchrone de bout en bout ([ExecuteurTacheSynchrone]) ; le voile du chrome n'est pas
/// installé (contexte partiel), ce qui n'ôte aucune garantie ([OccupationChromeTest]).
class ActionsSauvegardeTest {

    private static final Path DOSSIER = Path.of("/tmp/sauvegardes");
    private static final Path FICHIER = Path.of("/tmp/sauvegardes/vigiechiro-2026-07-14.db");

    private final ServiceSauvegarde service = mock(ServiceSauvegarde.class);
    private final OccupationChrome occupation =
            new OccupationChrome(new ExecuteurTacheSynchrone(), new NavigationViewModel());

    /// Ce que le confirmateur a **demandé** (le message nomme ce qui va être écrasé).
    private final List<String> confirmations = new ArrayList<>();

    /// Ce que le notificateur a **dit**, au lieu de l'afficher.
    private final List<String> annonces = new ArrayList<>();

    private final List<NiveauNotification> niveaux = new ArrayList<>();

    /// Titres des sélecteurs réellement ouverts (vide = l'action n'a même pas demandé de fichier).
    private final List<String> selections = new ArrayList<>();

    /// Nombre de fois où la base a été relue après restauration.
    private int relectures;

    /// Ce que le double de sélection répondra : `Optional.empty()` = l'utilisateur a **annulé**.
    private Optional<Path> choix = Optional.empty();

    /// Ce que le double de confirmation répondra.
    private boolean confirme = true;

    private ActionsSauvegarde action;

    @BeforeEach
    void preparer() {
        when(service.dossierParDefaut()).thenReturn(DOSSIER);
        action = new ActionsSauvegarde(service, occupation, () -> null, () -> relectures++);
        action.selecteur().definir(new SelecteurFichier() {
            @Override
            public Optional<Path> choisirDossier(String titre, Optional<Path> dossierInitial) {
                selections.add(titre);
                return choix;
            }

            @Override
            public Optional<Path> choisirFichier(String titre, Optional<Path> dossierInitial, FiltreFichier filtre) {
                selections.add(titre);
                return choix;
            }
        });
        action.confirmateur().definir(message -> {
            confirmations.add(message);
            return confirme;
        });
        action.notificateur().definir((niveau, entete, message) -> {
            niveaux.add(niveau);
            annonces.add(entete + " | " + message);
        });
    }

    @Test
    @DisplayName("#1405 : sauvegarde de la base : le dossier choisi est celui qui reçoit la copie, et on dit où")
    void sauvegarde_ecrit_dans_le_dossier_choisi() {
        choix = Optional.of(DOSSIER);
        when(service.sauvegarder(DOSSIER)).thenReturn(FICHIER);

        action.sauvegarder();

        verify(service).sauvegarder(DOSSIER);
        assertThat(niveaux).containsExactly(NiveauNotification.INFORMATION);
        assertThat(annonces)
                .singleElement()
                .satisfies(annonce -> assertThat(annonce)
                        .contains("Sauvegarde créée")
                        .as("le chemin obtenu est la seule information utile : c'est là qu'il faudra la rechercher")
                        .contains(FICHIER.toString()));
    }

    @Test
    @DisplayName("#1405 : sélecteur annulé : aucune copie, aucun compte rendu")
    void selecteur_annule_ne_sauvegarde_rien() {
        choix = Optional.empty();

        action.sauvegarder();

        verify(service, never()).sauvegarder(any());
        assertThat(annonces).as("renoncer n'est pas un événement").isEmpty();
    }

    @Test
    @DisplayName("#1346 : sauvegarde complète amputée : c'est un AVERTISSEMENT, et il dit ce qui manque")
    void sauvegarde_complete_incomplete_avertit_et_nomme_ce_qui_manque() {
        choix = Optional.of(DOSSIER);
        when(service.sauvegarderComplet(DOSSIER))
                .thenReturn(new BilanSauvegarde(DOSSIER, 2, List.of("/media/carte-sd/Car640380")));

        action.sauvegarderComplet();

        verify(service).sauvegarderComplet(DOSSIER);
        // Une sauvegarde qu'on croit complète et qui ne l'est pas vaut moins que pas de sauvegarde du tout.
        assertThat(niveaux).containsExactly(NiveauNotification.AVERTISSEMENT);
        assertThat(annonces)
                .singleElement()
                .satisfies(annonce -> assertThat(annonce)
                        .contains("Sauvegarde incomplète")
                        .as("la racine non montée est nommée : sans cela, l'utilisateur ne peut pas la remonter")
                        .contains("/media/carte-sd/Car640380"));
    }

    @Test
    @DisplayName("#1346 : sauvegarde complète intégrale : information, pas avertissement")
    void sauvegarde_complete_integrale_informe() {
        choix = Optional.of(DOSSIER);
        when(service.sauvegarderComplet(DOSSIER)).thenReturn(new BilanSauvegarde(DOSSIER, 3, List.of()));

        action.sauvegarderComplet();

        assertThat(niveaux).containsExactly(NiveauNotification.INFORMATION);
        assertThat(annonces)
                .singleElement()
                .satisfies(annonce -> assertThat(annonce).contains("Sauvegarde complète créée"));
    }

    @Test
    @DisplayName("#1405 : sauvegarde complète refusée (elle peut peser des Go) : rien n'est copié")
    void sauvegarde_complete_refusee_ne_copie_rien() {
        choix = Optional.of(DOSSIER);
        confirme = false;

        action.sauvegarderComplet();

        verify(service, never()).sauvegarderComplet(any());
        assertThat(annonces).isEmpty();
    }

    @Test
    @DisplayName("#1405 : restauration confirmée : la base est remplacée, puis relue")
    void restauration_confirmee_remplace_la_base_et_la_relit() {
        choix = Optional.of(FICHIER);

        action.restaurer();

        // La confirmation nomme le fichier qui va écraser la base, et dit où part l'état courant.
        assertThat(confirmations)
                .singleElement()
                .satisfies(message -> assertThat(message)
                        .contains(FICHIER.getFileName().toString())
                        .contains("vigiechiro.db.avant-restauration"));
        verify(service).restaurer(FICHIER);
        assertThat(niveaux).containsExactly(NiveauNotification.INFORMATION);
        assertThat(annonces)
                .singleElement()
                .satisfies(annonce -> assertThat(annonce).contains("Base restaurée"));
        assertThat(relectures)
                .as("la base sous les yeux de l'utilisateur n'est plus la même : il faut la relire")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("#1405 : restauration refusée : la base locale est intacte")
    void restauration_refusee_laisse_la_base_intacte() {
        choix = Optional.of(FICHIER);
        confirme = false;

        action.restaurer();

        verify(service, never()).restaurer(any());
        assertThat(relectures).isZero();
        assertThat(annonces).isEmpty();
    }

    @Test
    @DisplayName("#1405 : sélecteur annulé : on ne demande même pas de confirmer un écrasement qui n'aura pas lieu")
    void selecteur_annule_ne_demande_pas_de_confirmer() {
        choix = Optional.empty();

        action.restaurer();

        assertThat(selections).containsExactly("Choisir une sauvegarde à restaurer");
        assertThat(confirmations)
                .as("aucun fichier désigné : il n'y a rien à confirmer")
                .isEmpty();
        verify(service, never()).restaurer(any());
    }

    @Test
    @DisplayName("#1346 : restauration complète confirmée : base et dossiers de session sont remplacés, puis relus")
    void restauration_complete_confirmee_remplace_tout() {
        choix = Optional.of(DOSSIER);

        action.restaurerComplet();

        // Ce que l'utilisateur doit savoir avant de dire oui : l'audio, lui, n'est pas mis de côté.
        assertThat(confirmations)
                .singleElement()
                .satisfies(message -> assertThat(message)
                        .contains(DOSSIER.getFileName().toString())
                        .contains("mais pas l'audio"));
        verify(service).restaurerComplet(DOSSIER);
        assertThat(niveaux).containsExactly(NiveauNotification.INFORMATION);
        assertThat(annonces)
                .singleElement()
                .satisfies(annonce -> assertThat(annonce).contains("Sauvegarde restaurée"));
        assertThat(relectures).isEqualTo(1);
    }

    @Test
    @DisplayName("#1405 : restauration complète refusée : rien n'est écrasé")
    void restauration_complete_refusee_n_ecrase_rien() {
        choix = Optional.of(DOSSIER);
        confirme = false;

        action.restaurerComplet();

        verify(service, never()).restaurerComplet(any());
        assertThat(relectures).isZero();
    }

    @Test
    @DisplayName("#1405 : restauration interrompue : l'utilisateur est averti, la base n'est pas relue")
    void restauration_interrompue_avertit() {
        choix = Optional.of(FICHIER);
        doThrow(new IllegalStateException("sauvegarde corrompue")).when(service).restaurer(FICHIER);

        action.restaurer();

        assertThat(niveaux).containsExactly(NiveauNotification.AVERTISSEMENT);
        assertThat(annonces)
                .singleElement()
                .satisfies(annonce ->
                        assertThat(annonce).contains("Restauration impossible").contains("sauvegarde corrompue"));
        assertThat(relectures)
                .as("la restauration a échoué : la base affichée est toujours la bonne")
                .isZero();
    }
}
