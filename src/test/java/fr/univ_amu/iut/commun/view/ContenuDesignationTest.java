package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.view.ContenuDesignation.Entree;
import fr.univ_amu.iut.commun.view.ContenuDesignation.Mode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;

/// Ce que ces cas éprouvent, et pourquoi aucune fenêtre ne s'ouvre.
///
/// [ContenuDesignation] est séparé de sa fenêtre précisément pour cela : la fenêtre appellera
/// `showAndWait()`, qui fige un test headless, et le contenu se monte sans elle. Ces cas construisent
/// donc le contenu sur un `@TempDir` et lui parlent directement.
///
/// **Un vrai dossier temporaire, et non un double du système de fichiers.** Le contenu lit le disque,
/// et abstraire cette lecture pour la tester ferait éprouver l'abstraction plutôt que le geste. Un
/// `@TempDir` coûte moins et dit plus.
@ExtendWith(ApplicationExtension.class)
class ContenuDesignationTest {

    @TempDir
    Path bac;

    private final List<Path> valides = new ArrayList<>();
    private final AtomicReference<Boolean> annule = new AtomicReference<>(false);

    @BeforeEach
    void semer() throws IOException {
        Files.createDirectories(bac.resolve("Nuits 2026"));
        Files.createDirectories(bac.resolve("Archives"));
        Files.writeString(bac.resolve("releve.csv"), "a,b\n");
        Files.writeString(bac.resolve("notes.txt"), "rien\n");
        Files.writeString(bac.resolve(".cache"), "invisible\n");
    }

    private ContenuDesignation contenu(Mode mode, String nomPropose, FiltreFichier filtre) {
        return new ContenuDesignation(mode, bac, nomPropose, filtre, valides::add, () -> annule.set(true));
    }

    @Test
    @DisplayName("la liste montre les dossiers d'abord, et jamais les fichiers cachés")
    void la_liste_ordonne_et_masque_les_caches() {
        ContenuDesignation vue = contenu(Mode.FICHIER, null, null);

        List<String> noms = vue.entrees().stream().map(Entree::nom).toList();

        // Les deux dossiers avant les deux fichiers, chaque groupe par ordre alphabétique.
        assertThat(noms).containsExactly("Archives", "Nuits 2026", "notes.txt", "releve.csv");
        // `.cache` n'y est pas : le dialogue du système les propose, celui-ci non, et c'est plus
        // facile à ajouter qu'à retirer. Le design du changement laissait la question ouverte.
        assertThat(noms).doesNotContain(".cache");
    }

    @Test
    @DisplayName("en mode dossier, aucun fichier n'est proposé")
    void le_mode_dossier_ne_propose_que_des_dossiers() {
        ContenuDesignation vue = contenu(Mode.DOSSIER, null, null);

        assertThat(vue.entrees()).allMatch(Entree::dossier);
        assertThat(vue.entrees()).hasSize(2);
    }

    @Test
    @DisplayName("le filtre de type retient ce qu'il annonce")
    void le_filtre_retient_ce_qu_il_annonce() {
        ContenuDesignation vue = contenu(Mode.FICHIER, null, FiltreFichier.csv());

        List<String> noms = vue.entrees().stream().map(Entree::nom).toList();

        assertThat(noms).contains("releve.csv").doesNotContain("notes.txt");
        // Les dossiers restent, sans quoi on ne pourrait plus naviguer pour aller chercher le fichier.
        assertThat(noms).contains("Archives", "Nuits 2026");
    }

    @Test
    @DisplayName("en mode enregistrement, le nom proposé est là et compose le chemin")
    void l_enregistrement_propose_un_nom() {
        ContenuDesignation vue = contenu(Mode.ENREGISTREMENT, "sauvegarde-2026-09-06.vcz", FiltreFichier.csv());

        assertThat(vue.designer()).contains(bac.resolve("sauvegarde-2026-09-06.vcz"));
    }

    @Test
    @DisplayName("un chemin SAISI qui n'existe pas est refusé AVEC son message, et rien n'est rendu")
    void un_chemin_absent_est_refuse_avec_son_message() {
        // C'est l'exigence qui distingue ce dialogue du natif sous bac à sable : sans elle, un chemin
        // hors d'atteinte donnerait une liste vide, que l'utilisateur lit « ce dossier est vide »
        // alors qu'elle veut dire « je ne peux pas y aller » (ADR 2748).
        ContenuDesignation vue = contenu(Mode.DOSSIER, null, null);
        Path avant = vue.dossierCourant();

        vue.allerVers(bac.resolve("nulle-part").toString());

        assertThat(vue.message()).contains("n'existe pas");
        assertThat(vue.dossierCourant()).isEqualTo(avant);
        assertThat(valides).isEmpty();
    }

    @Test
    @DisplayName("un chemin SAISI lisible mène là où il pointe")
    void un_chemin_lisible_est_suivi() {
        ContenuDesignation vue = contenu(Mode.DOSSIER, null, null);

        vue.allerVers(bac.resolve("Archives").toString());

        assertThat(vue.dossierCourant()).isEqualTo(bac.resolve("Archives"));
        assertThat(vue.message()).isEmpty();
    }

    @Test
    @DisplayName("créer un dossier le crée et y entre")
    void creer_un_dossier_y_entre() {
        ContenuDesignation vue = contenu(Mode.ENREGISTREMENT, "Nuits 2027", null);

        vue.creerUnDossier();

        assertThat(bac.resolve("Nuits 2027")).exists();
        assertThat(vue.dossierCourant()).isEqualTo(bac.resolve("Nuits 2027"));
    }

    @Test
    @DisplayName("créer un dossier sans nom le dit, au lieu de créer un dossier sans nom")
    void creer_un_dossier_sans_nom_est_refuse() {
        ContenuDesignation vue = contenu(Mode.ENREGISTREMENT, "", null);

        vue.creerUnDossier();

        assertThat(vue.message()).contains("nom");
    }

    @Test
    @DisplayName("en mode enregistrement, un nom vide ne désigne RIEN")
    void un_nom_vide_ne_designe_rien() {
        // Rendre le dossier seul écrirait à un endroit que l'utilisateur n'a pas nommé.
        ContenuDesignation vue = contenu(Mode.ENREGISTREMENT, "   ", null);

        assertThat(vue.designer()).isEmpty();
    }
}
