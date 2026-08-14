package fr.univ_amu.iut.audit.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.Provider;
import fr.univ_amu.iut.audit.model.BilanRecuperabilite;
import fr.univ_amu.iut.audit.model.RapportAudit;
import fr.univ_amu.iut.audit.model.RecuperabiliteNuit;
import fr.univ_amu.iut.audit.model.ResultatReset;
import fr.univ_amu.iut.audit.model.ServiceRecuperabilite;
import fr.univ_amu.iut.audit.model.ServiceReset;
import fr.univ_amu.iut.audit.model.SourceAudio;
import fr.univ_amu.iut.commun.persistence.BilanSauvegarde;
import fr.univ_amu.iut.commun.persistence.ServiceSauvegarde;
import fr.univ_amu.iut.commun.view.ExecuteurTacheSynchrone;
import fr.univ_amu.iut.commun.view.GroupeMenu;
import fr.univ_amu.iut.commun.view.OccupationChrome;
import fr.univ_amu.iut.commun.view.SelecteurFichier;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le **câblage de la sixième entrée ☰** (« Repartir d'une base neuve » (#1419)) au regard du trou que
/// #1436 a fermé sur les cinq autres.
///
/// Les cinq entrées qui touchent au disque construisaient leur action **dans** `executer()`, avec leurs
/// vrais dialogues : rien ne pouvait donc les déclencher, et rien ne vérifiait qu'elles appellent **le
/// bon geste**. Celle-ci était née après, et reproduisait le défaut : alors qu'elle est **la plus
/// destructrice du menu** : elle efface la base.
///
/// Elle détient maintenant son geste. On remplace ses dialogues (et sa fermeture d'application, sans quoi
/// le test tuerait sa propre JVM), on déclenche l'entrée, et on vérifie **quel** geste est parti : et,
/// tout aussi important, **lequel ne l'est pas**.
class ActionResetGuideTest {

    private static final Path SAUVEGARDES = Path.of("/tmp/sauvegardes");

    private final ServiceRecuperabilite recuperabilite = mock(ServiceRecuperabilite.class);
    private final ServiceReset reset = mock(ServiceReset.class);
    private final ServiceSauvegarde sauvegarde = mock(ServiceSauvegarde.class);
    private final OccupationChrome occupation =
            new OccupationChrome(new ExecuteurTacheSynchrone(), new NavigationViewModel());

    private int fermetures;

    @Test
    @DisplayName("#1419 : « Repartir d'une base neuve » lance le RESET, et surtout pas une sauvegarde")
    void l_entree_lance_le_reset() {
        when(recuperabilite.bilan()).thenReturn(bilanAvecPerte());
        when(reset.executer(any(), anyBoolean())).thenReturn(fait());

        ActionResetGuide entree = entreePrete();

        entree.executer(null);

        verify(reset)
                .executer(SAUVEGARDES, true); // la perte a été montrée puis acceptée : c'est ce qui arme le service
        // C'est là tout l'enjeu du garde-fou : sur six entrées qui se ressemblent, celle-ci EFFACE la base.
        // Un copier-coller qui l'aurait branchée sur `sauvegarderComplet` n'aurait rien cassé de visible.
        verify(sauvegarde, never()).sauvegarderComplet(any());
        verify(sauvegarde, never()).restaurer(any());
        assertThat(fermetures)
                .as("le reset a abouti : l'application se ferme, ses écrans tenant l'ancienne base")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("l'entrée s'annonce dans le menu ☰ avec son libellé, son rang et son icône")
    void annonce_sa_place_dans_le_menu() {
        ActionResetGuide entree =
                new ActionResetGuide(fournir(recuperabilite), fournir(reset), fournir(sauvegarde), occupation);

        assertThat(entree.groupe()).isEqualTo(GroupeMenu.BASE);
        assertThat(entree.ordre()).isEqualTo(35);
        assertThat(entree.libelle()).isEqualTo("Repartir d'une base neuve…");
        assertThat(entree.iconeLiteral()).isEqualTo("fas-recycle");
    }

    @Test
    @DisplayName("#1419 : refuser la confirmation → RIEN ne part, et l'application reste ouverte")
    void refuser_n_execute_rien() {
        when(recuperabilite.bilan()).thenReturn(bilanAvecPerte());

        ActionResetGuide entree = entreePrete();
        entree.geste().confirmateur().definir(message -> false);

        entree.executer(null);

        verify(reset, never()).executer(any(), anyBoolean());
        assertThat(fermetures).isZero();
    }

    /// L'entrée, avec ses dialogues et sa fermeture remplacés : le sélecteur désigne le dossier de
    /// sauvegarde, l'utilisateur accepte, le compte rendu est avalé, et la fermeture est comptée au lieu
    /// d'être jouée.
    private ActionResetGuide entreePrete() {
        when(sauvegarde.dossierParDefaut()).thenReturn(SAUVEGARDES);
        ActionResetGuide entree =
                new ActionResetGuide(fournir(recuperabilite), fournir(reset), fournir(sauvegarde), occupation);
        GesteReset geste = entree.geste();
        geste.selecteur().definir(new SelecteurFichier() {
            @Override
            public Optional<Path> choisirDossier(String titre, Optional<Path> dossierInitial) {
                return Optional.of(SAUVEGARDES);
            }

            @Override
            public Optional<Path> choisirFichier(
                    String titre, Optional<Path> dossierInitial, fr.univ_amu.iut.commun.view.FiltreFichier filtre) {
                return Optional.of(SAUVEGARDES);
            }

            /// Le reset ne demande jamais où **écrire** un fichier : il désigne un dossier de sauvegarde.
            @Override
            public Optional<Path> enregistrerFichier(
                    String titre, String nomPropose, fr.univ_amu.iut.commun.view.FiltreFichier filtre) {
                return Optional.empty();
            }
        });
        geste.confirmateur().definir(message -> true);
        geste.notificateur().definir((niveau, entete, message) -> {});
        geste.definirFermeture(() -> fermetures++);
        return entree;
    }

    private static <T> Provider<T> fournir(T instance) {
        return () -> instance;
    }

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
                List.of());
    }
}
