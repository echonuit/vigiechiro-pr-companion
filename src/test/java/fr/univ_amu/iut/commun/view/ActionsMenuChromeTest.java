package fr.univ_amu.iut.commun.view;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.persistence.BilanSauvegarde;
import fr.univ_amu.iut.commun.persistence.DeclarationPurgeOriginaux;
import fr.univ_amu.iut.commun.persistence.ServicePurgeOriginaux;
import fr.univ_amu.iut.commun.persistence.ServicePurgeOriginaux.ResultatPurge;
import fr.univ_amu.iut.commun.persistence.ServiceSauvegarde;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le **câblage des cinq entrées ☰** qui touchent au disque ou à la base (#1405).
///
/// Ce test couvre un trou que la conversion des dialogues avait laissé ouvert : chaque entrée
/// construisait son action **dans** `executer()`, avec ses vrais dialogues. Rien ne pouvait donc la
/// déclencher, et rien ne vérifiait qu'elle appelle **le bon geste**. Or les quatre entrées de
/// sauvegarde sont presque identiques - même service, même plomberie, même signature - et deux d'entre
/// elles **écrasent la base**. Intervertir `restaurer()` et `restaurerComplet()` d'un copier-coller
/// n'aurait rien cassé de visible, et personne ne l'aurait vu.
///
/// Chaque entrée détient maintenant son action pour de bon : on remplace ses trois dialogues, on
/// déclenche l'entrée, et on vérifie **quel** geste est parti - et, tout aussi important, **lequel ne
/// l'est pas**.
///
/// Ce test dit **ce que fait** chaque entrée. Ce qu'elles forment **ensemble** - les six contribuées au
/// menu ☰, dans l'ordre - est l'objet d'[ActionMenuWiringTest], sur le vrai injecteur.
class ActionsMenuChromeTest {

    private static final Path DOSSIER = Path.of("/tmp/sauvegardes");
    private static final Path FICHIER = Path.of("/tmp/sauvegardes/vigiechiro.db");

    private final ServiceSauvegarde sauvegarde = mock(ServiceSauvegarde.class);
    private final ServicePurgeOriginaux purge = mock(ServicePurgeOriginaux.class);
    private final Navigateur navigateur = new Navigateur(new NavigationViewModel());
    private final OccupationChrome occupation =
            new OccupationChrome(new ExecuteurTacheSynchrone(), new NavigationViewModel());

    /// Les trois dialogues d'une action de sauvegarde, remplacés d'un coup : le sélecteur désigne
    /// `choix`, l'utilisateur accepte, le compte rendu est avalé. Sans cela, `executer()` ouvrirait un
    /// sélecteur natif et figerait le test.
    private void neutraliserDialogues(ActionsSauvegarde actions, Path choix) {
        when(sauvegarde.dossierParDefaut()).thenReturn(DOSSIER);
        actions.selecteur().definir(new SelecteurFichier() {
            @Override
            public Optional<Path> choisirDossier(String titre, Optional<Path> dossierInitial) {
                return Optional.of(choix);
            }

            @Override
            public Optional<Path> choisirFichier(String titre, Optional<Path> dossierInitial, FiltreFichier filtre) {
                return Optional.of(choix);
            }
        });
        actions.confirmateur().definir(message -> true);
        actions.notificateur().definir((niveau, entete, message) -> {});
    }

    @Test
    @DisplayName("#1405 : « Sauvegarder la base » sauvegarde - et ne restaure rien")
    void entree_sauvegarder() {
        ActionSauvegarder entree = new ActionSauvegarder(sauvegarde, navigateur, occupation);
        neutraliserDialogues(entree.porteur().actions(), DOSSIER);
        when(sauvegarde.sauvegarder(DOSSIER)).thenReturn(FICHIER);

        entree.executer(null);

        verify(sauvegarde).sauvegarder(DOSSIER);
        verify(sauvegarde, never()).restaurer(any());
        verify(sauvegarde, never()).sauvegarderComplet(any());
    }

    @Test
    @DisplayName("#1405 : « Sauvegarde complète » emporte l'audio - ce n'est pas la sauvegarde simple")
    void entree_sauvegarder_complet() {
        ActionSauvegarderComplet entree = new ActionSauvegarderComplet(sauvegarde, navigateur, occupation);
        neutraliserDialogues(entree.porteur().actions(), DOSSIER);
        when(sauvegarde.sauvegarderComplet(DOSSIER)).thenReturn(new BilanSauvegarde(DOSSIER, 2, List.of()));

        entree.executer(null);

        // C'est la SEULE sauvegarde qui protège l'audio (#1346) : la confondre avec la simple, c'est
        // croire son audio à l'abri alors qu'il ne l'est pas.
        verify(sauvegarde).sauvegarderComplet(DOSSIER);
        verify(sauvegarde, never()).sauvegarder(any());
    }

    @Test
    @DisplayName("#1405 : « Restaurer une sauvegarde » restaure la base seule - et n'écrase pas l'audio")
    void entree_restaurer() {
        ActionRestaurer entree = new ActionRestaurer(sauvegarde, navigateur, occupation);
        neutraliserDialogues(entree.porteur().actions(), FICHIER);

        entree.executer(null);

        verify(sauvegarde).restaurer(FICHIER);
        verify(sauvegarde, never()).restaurerComplet(any());
        verify(sauvegarde, never()).sauvegarder(any());
    }

    @Test
    @DisplayName("#1405 : « Restauration complète » remplace base ET dossiers de session")
    void entree_restaurer_complet() {
        ActionRestaurerComplet entree = new ActionRestaurerComplet(sauvegarde, navigateur, occupation);
        neutraliserDialogues(entree.porteur().actions(), DOSSIER);

        entree.executer(null);

        verify(sauvegarde).restaurerComplet(DOSSIER);
        verify(sauvegarde, never()).restaurer(any());
    }

    @Test
    @DisplayName("#1405 : « Purger les originaux » purge, déclare le geste, et ne touche pas à la sauvegarde")
    void entree_purger() {
        DeclarationPurgeOriginaux declaration = mock(DeclarationPurgeOriginaux.class);
        ActionPurger entree = new ActionPurger(purge, navigateur, occupation, Optional.of(declaration));
        entree.actions().confirmateur().definir(message -> true);
        entree.actions().notificateur().definir((niveau, entete, message) -> {});
        when(purge.volumeRecuperable()).thenReturn(4_294_967_296L);
        when(purge.purgerTout()).thenReturn(new ResultatPurge(3, 4_294_967_296L));

        entree.executer(null);

        verify(purge).purgerTout();
        // Sans cette déclaration (#1303), l'audit prendrait les bruts purgés pour une corruption.
        verify(declaration).declarerPurgeGlobale();
    }
}
