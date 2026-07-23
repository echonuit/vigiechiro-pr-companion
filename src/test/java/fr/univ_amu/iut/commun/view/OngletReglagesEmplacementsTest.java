package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.ServiceEmplacements;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.util.WaitForAsyncUtils;

/// Rendu et comportement de l'onglet « Emplacements » (#1038). Les trois dialogues sont remplacés par
/// des doubles : le sélecteur natif figerait TestFX, le compte rendu aussi, et `Platform.exit()` tuerait
/// le runtime. La sonde, elle, est **réelle** (elle écrit dans des dossiers jetables).
///
/// `vigiechiro.config` est posé sur un dossier jetable et `vigiechiro.workspace` effacé, pour que la
/// configuration écrite pilote `Workspace.resolu()` - la couture de production.
@ExtendWith(ApplicationExtension.class)
class OngletReglagesEmplacementsTest {

    private static final String PROP_CONFIG = "vigiechiro.config";
    private static final String PROP_WORKSPACE = "vigiechiro.workspace";

    @TempDir
    Path config;

    @TempDir
    Path choix;

    private String configAvant;
    private String workspaceAvant;

    @BeforeEach
    void poserProprietes() {
        configAvant = System.getProperty(PROP_CONFIG);
        workspaceAvant = System.getProperty(PROP_WORKSPACE);
        System.setProperty(PROP_CONFIG, config.toString());
        System.clearProperty(PROP_WORKSPACE);
    }

    @AfterEach
    void restaurerProprietes() {
        restaurer(PROP_CONFIG, configAvant);
        restaurer(PROP_WORKSPACE, workspaceAvant);
    }

    @Test
    @DisplayName("Choisir deux dossiers puis Appliquer écrit la configuration et montre l'avis de redémarrage")
    void choisir_puis_appliquer() {
        Path travail = choix.resolve("nuits");
        Path coffre = choix.resolve("coffre");
        OngletReglagesEmplacements onglet =
                onglet(selecteurQuiRepond(travail, coffre), notificateurQuiCapture(), () -> {});

        Region racine = construire(onglet);
        surFx(() -> boutons(racine, "emplacements-choisir").forEach(Button::fire));
        surFx(() -> bouton(racine, "emplacements-appliquer").fire());

        ServiceEmplacements.Emplacements ecrit = new ServiceEmplacements().emplacementsCourants();
        assertThat(ecrit.personnalise()).isTrue();
        assertThat(ecrit.espaceDeTravail()).isEqualTo(travail.toAbsolutePath());
        assertThat(ecrit.base())
                .as("la base est nommée dans le dossier choisi")
                .isEqualTo(coffre.resolve("vigiechiro.db").toAbsolutePath());
        assertThat(surFx(() -> avis(racine).isVisible() && avis(racine).isManaged()))
                .as("l'avis de redémarrage apparaît (visible ET pris en compte dans la mise en page)")
                .isTrue();
        assertThat(surFx(() -> textes(racine, "emplacements-chemin")))
                .as("le chemin affiché suit le dossier choisi")
                .contains(travail.toString(), coffre.toString());
    }

    @Test
    @DisplayName("Réinitialiser efface le choix, rétablit les défauts, désactive son bouton et montre l'avis")
    void reinitialiser_efface_le_choix() throws Exception {
        // On part d'une configuration personnalisée : le service l'écrit d'abord.
        Path travail = choix.resolve("nuits");
        new ServiceEmplacements().enregistrer(travail, choix.resolve("coffre"));
        OngletReglagesEmplacements onglet =
                onglet(selecteurQuiRepond(travail, choix.resolve("coffre")), notificateurQuiCapture(), () -> {});

        Region racine = construire(onglet);
        Button reinitialiser = bouton(racine, "emplacements-reinitialiser");
        assertThat(surFx(reinitialiser::isDisabled))
                .as("le bouton est actif quand une configuration existe")
                .isFalse();

        surFx(reinitialiser::fire);

        assertThat(new ServiceEmplacements().emplacementsCourants().personnalise())
                .as("la configuration a été effacée")
                .isFalse();
        assertThat(surFx(reinitialiser::isDisabled))
                .as("le bouton se désactive une fois les défauts rétablis")
                .isTrue();
        assertThat(surFx(() -> avis(racine).isVisible()))
                .as("réinitialiser prend effet au prochain démarrage, l'avis le dit")
                .isTrue();
        assertThat(surFx(() -> textes(racine, "emplacements-chemin")))
                .as("les DEUX chemins affichés repassent au défaut, aucun ne reste figé sur l'ancien choix")
                .contains(new ServiceEmplacements()
                        .emplacementsCourants()
                        .espaceDeTravail()
                        .toString())
                .doesNotContain(travail.toString(), choix.resolve("coffre").toString());
    }

    @Test
    @DisplayName("Une réinitialisation impossible est signalée, pas avalée")
    void reinitialisation_impossible_est_signalee() throws Exception {
        // Bouton actif : une configuration existe d'abord.
        Path travail = choix.resolve("nuits");
        new ServiceEmplacements().enregistrer(travail, choix.resolve("coffre"));
        List<String> alertes = new ArrayList<>();
        OngletReglagesEmplacements onglet = onglet(
                selecteurQuiRepond(travail, choix.resolve("coffre")),
                (niveau, entete, message) -> alertes.add(entete),
                () -> {});
        Region racine = construire(onglet);

        // On rend l'écriture impossible en pointant la config SOUS un fichier (dossier non créable),
        // le déclencheur fiable de `sonde_non_creable`. `reinitialiser` doit alors échouer à écrire.
        Path obstacle = java.nio.file.Files.createFile(choix.resolve("obstacle"));
        System.setProperty(PROP_CONFIG, obstacle.resolve("sous").toString());

        surFx(() -> bouton(racine, "emplacements-reinitialiser").fire());

        assertThat(alertes)
                .as("l'échec de réinitialisation est dit, jamais silencieux")
                .containsExactly("Réinitialisation impossible");
    }

    @Test
    @DisplayName("Le formulaire est construit une seule fois : le même nœud est rendu à chaque appel")
    void formulaire_mis_en_cache() {
        OngletReglagesEmplacements onglet =
                onglet(selecteurQuiRepond(choix.resolve("a"), choix.resolve("b")), notificateurQuiCapture(), () -> {});

        Node premier = surFx(onglet::formulairePersonnalise);
        Node second = surFx(onglet::formulairePersonnalise);

        assertThat(second)
                .as("rendu deux fois, l'onglet ne reconstruit pas son nœud (état du choix préservé)")
                .isSameAs(premier);
    }

    @Test
    @DisplayName("Sans configuration, le bouton « Rétablir les défauts » est désactivé")
    void bouton_reinitialiser_desactive_sans_configuration() {
        OngletReglagesEmplacements onglet =
                onglet(selecteurQuiRepond(choix.resolve("a"), choix.resolve("b")), notificateurQuiCapture(), () -> {});

        Region racine = construire(onglet);

        assertThat(surFx(() -> bouton(racine, "emplacements-reinitialiser").isDisabled()))
                .as("rien à rétablir tant qu'aucun emplacement n'est personnalisé")
                .isTrue();
        assertThat(surFx(() -> avis(racine).isVisible() || avis(racine).isManaged()))
                .as("à l'ouverture, aucun avis de redémarrage : rien n'a encore été appliqué")
                .isFalse();
    }

    @Test
    @DisplayName("Choisir un nouveau dossier après avoir appliqué masque l'avis de redémarrage")
    void choisir_apres_appliquer_masque_l_avis() {
        Path travail = choix.resolve("nuits");
        OngletReglagesEmplacements onglet =
                onglet(selecteurQuiRepond(travail, choix.resolve("coffre")), notificateurQuiCapture(), () -> {});

        Region racine = construire(onglet);
        surFx(() -> bouton(racine, "emplacements-appliquer").fire());
        assertThat(surFx(() -> avis(racine).isVisible())).isTrue();

        // Un nouveau choix invalide l'avis : ce qui est affiché ne correspond plus à ce qui est écrit.
        surFx(() -> bouton(racine, "emplacements-choisir").fire());

        assertThat(surFx(() -> avis(racine).isVisible() || avis(racine).isManaged()))
                .as("l'avis disparaît dès qu'un nouveau dossier est choisi")
                .isFalse();
    }

    @Test
    @DisplayName("Un enregistrement impossible est signalé, pas avalé")
    void enregistrement_impossible_est_signale() throws Exception {
        // On pointe la configuration vers un FICHIER : `enregistrerDans` échoue à créer le dossier.
        Path fichierConfig = java.nio.file.Files.createFile(choix.resolve("config-est-un-fichier"));
        System.setProperty(PROP_CONFIG, fichierConfig.toString());
        List<String> alertes = new ArrayList<>();
        OngletReglagesEmplacements onglet = onglet(
                selecteurQuiRepond(choix.resolve("a"), choix.resolve("b")),
                (niveau, entete, message) -> alertes.add(entete),
                () -> {});

        Region racine = construire(onglet);
        surFx(() -> bouton(racine, "emplacements-appliquer").fire());

        assertThat(alertes)
                .as("l'échec d'écriture est dit à l'utilisateur, jamais silencieux")
                .containsExactly("Enregistrement impossible");
        assertThat(surFx(() -> avis(racine).isVisible()))
                .as("pas d'avis de redémarrage quand rien n'a été écrit")
                .isFalse();
    }

    @Test
    @DisplayName("L'onglet se déclare : identité, titre, rang, icône")
    void metadonnees_de_l_onglet() {
        OngletReglagesEmplacements onglet =
                onglet(selecteurQuiRepond(choix.resolve("a"), choix.resolve("b")), notificateurQuiCapture(), () -> {});

        assertThat(onglet.idFeature()).isEqualTo("emplacements");
        assertThat(onglet.titre()).isEqualTo("Emplacements");
        assertThat(onglet.ordre()).isEqualTo(15);
        assertThat(onglet.iconeLiteral()).isEqualTo("fas-folder-open");
    }

    @Test
    @DisplayName("Un dossier refusé par la sonde n'est pas retenu, et le refus est signalé")
    void dossier_refuse_par_la_sonde() throws Exception {
        // La cible « base » est un FICHIER : la sonde répond PAS_UN_DOSSIER, le choix ne doit pas bouger.
        Path travail = choix.resolve("nuits");
        Path fichier = java.nio.file.Files.createFile(choix.resolve("un-fichier"));
        List<String> refus = new ArrayList<>();
        OngletReglagesEmplacements onglet =
                onglet(selecteurQuiRepond(travail, fichier), (niveau, entete, message) -> refus.add(entete), () -> {});

        Region racine = construire(onglet);
        surFx(() -> boutons(racine, "emplacements-choisir").forEach(Button::fire));

        assertThat(refus)
                .as("le refus de la sonde est signalé à l'utilisateur")
                .containsExactly("Dossier inutilisable");
        // La base n'a pas été retenue : Appliquer écrit donc le dossier de base par DÉFAUT, pas le fichier.
        surFx(() -> bouton(racine, "emplacements-appliquer").fire());
        assertThat(new ServiceEmplacements().emplacementsCourants().base())
                .as("un chemin refusé ne se retrouve pas dans la configuration écrite")
                .isNotEqualTo(fichier.resolve("vigiechiro.db").toAbsolutePath());
    }

    @Test
    @DisplayName("Le bouton « Quitter » déclenche la sortie de l'application")
    void quitter_declenche_la_sortie() {
        boolean[] quitte = {false};
        OngletReglagesEmplacements onglet = onglet(
                selecteurQuiRepond(choix.resolve("a"), choix.resolve("b")),
                notificateurQuiCapture(),
                () -> quitte[0] = true);

        Region racine = construire(onglet);
        surFx(() -> bouton(racine, "emplacements-appliquer").fire()); // fait apparaître l'avis + le bouton Quitter
        surFx(() -> bouton(racine, "emplacements-quitter").fire());

        assertThat(quitte[0])
                .as("« Quitter » passe par le double de sortie, jamais par Platform.exit()")
                .isTrue();
    }

    private OngletReglagesEmplacements onglet(SelecteurFichier selecteur, Notificateur notificateur, Runnable sortie) {
        OngletReglagesEmplacements onglet = new OngletReglagesEmplacements(new ServiceEmplacements());
        onglet.definirSelecteur(selecteur);
        onglet.definirNotificateur(notificateur);
        onglet.definirSortie(sortie);
        return onglet;
    }

    private static Region construire(OngletReglagesEmplacements onglet) {
        return surFx(() -> {
            onglet.formulairePersonnalise();
            return onglet.racineTest();
        });
    }

    private static SelecteurFichier selecteurQuiRepond(Path travail, Path base) {
        return new SelecteurFichier() {
            @Override
            public Optional<Path> choisirDossier(String titre, Optional<Path> dossierInitial) {
                if (titre.contains("Dossier de travail")) {
                    return Optional.of(travail);
                }
                if (titre.contains("Base de données")) {
                    return Optional.of(base);
                }
                return Optional.empty();
            }

            @Override
            public Optional<Path> choisirFichier(String titre, Optional<Path> dossierInitial, FiltreFichier filtre) {
                return Optional.empty();
            }

            @Override
            public Optional<Path> enregistrerFichier(String titre, String nomPropose, FiltreFichier filtre) {
                return Optional.empty();
            }
        };
    }

    private static Notificateur notificateurQuiCapture() {
        return (niveau, entete, message) -> {};
    }

    private static List<Button> boutons(Region racine, String classe) {
        return racine.lookupAll("." + classe).stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .toList();
    }

    private static Button bouton(Region racine, String classe) {
        return (Button) racine.lookup("." + classe);
    }

    private static List<String> textes(Region racine, String classe) {
        return racine.lookupAll("." + classe).stream()
                .filter(javafx.scene.control.Label.class::isInstance)
                .map(javafx.scene.control.Label.class::cast)
                .map(javafx.scene.control.Label::getText)
                .toList();
    }

    private static Node avis(Region racine) {
        return racine.lookup(".emplacements-avis");
    }

    private static void surFx(Runnable action) {
        WaitForAsyncUtils.waitForAsyncFx(5_000, action);
    }

    private static <T> T surFx(java.util.concurrent.Callable<T> action) {
        return WaitForAsyncUtils.waitForAsyncFx(5_000, action);
    }

    private static void restaurer(String cle, String valeur) {
        if (valeur == null) {
            System.clearProperty(cle);
        } else {
            System.setProperty(cle, valeur);
        }
    }
}
