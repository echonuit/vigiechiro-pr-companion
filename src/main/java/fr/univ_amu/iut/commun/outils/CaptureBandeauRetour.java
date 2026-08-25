package fr.univ_amu.iut.commun.outils;

import fr.univ_amu.iut.commun.view.BandeauRetour;
import fr.univ_amu.iut.commun.viewmodel.ResteDeRestauration;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/// Aperçus du **bandeau de retour** portant un message *venu d'ailleurs* (#2852).
///
/// La borne du message (#2076, #2841) existe pour un **effet de mise en page**. Le libellé porte `wrapText`,
/// donc rien n'est tronqué : un long message **enroule**, le bandeau grandit, et il pousse le contenu
/// vers le bas. Un test peut affirmer que la chaîne fait 240 caractères ; il ne dit pas à quoi
/// ressemble un bandeau de 240 caractères au-dessus d'une liste.
///
/// ## Ce que chaque aperçu montre
///
/// - `apercu-bandeau-retour-refus.png` : un refus **que nous avons écrit**, enrichi du geste attendu.
///   Une ligne. C'est le cas courant, celui qui doit rester discret.
/// - `apercu-bandeau-retour-externe.png` : un échec réseau **tel que la plateforme le rapporte**, passé
///   par la porte qui l'enrichit. Deux lignes : le bandeau grandit déjà.
/// - `apercu-bandeau-retour-borne.png` : un message de pilote SQLite, **coupé à la borne**, avec son
///   « … (détail dans le journal) ». Sans la borne, ce bandeau ferait trois fois cette hauteur.
/// - `apercu-bandeau-vue-amputee.png` : une vue mémorisée rejouée sans **une** valeur devenue
///   introuvable (#3056). Le cas courant après le renommage de #2995 : une ligne, discret.
/// - `apercu-bandeau-vue-amputee-longue.png` : la **même chose avec cinq valeurs**. C'est le cas qui
///   se produit quand une vue traverse un renommage de plein fouet, et le seul moyen de savoir si
///   l'énumération reste lisible ou s'il faudrait la résumer.
///
/// Le contenu sous le bandeau n'est pas décoratif : c'est lui qui rend visible ce que la borne protège.
///
/// [MesureBandeauRetour] construit le bandeau **à la main** et répond à « de combien ça grandit ». Ces
/// aperçus passent par le vrai [BandeauRetour], donc par sa sévérité, son icône et sa liaison au
/// [RetourOperation], et répondent à « est-ce que ça reste lisible ».
///
/// Lancement : `./mvnw exec:exec` avec cette classe en `mainClass` (headless, cf. capture-screenshots.sh).
public final class CaptureBandeauRetour {

    /// Largeur d'un écran de l'application, celle à laquelle la borne a été mesurée (#2076).
    private static final int LARGEUR = 1000;

    private CaptureBandeauRetour() {}

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch fini = new CountDownLatch(1);
        AtomicReference<Throwable> erreur = new AtomicReference<>();
        Platform.startup(() -> {
            try {
                capturer();
            } catch (RuntimeException probleme) {
                erreur.set(probleme);
            } finally {
                fini.countDown();
            }
        });
        fini.await();
        Platform.exit();
        if (erreur.get() != null) {
            erreur.get().printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    private static void capturer() {
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));
        rendre(RetourOperation.erreur(refusEcritParNous()), sortie.resolve("apercu-bandeau-retour-refus.png"));
        rendre(
                RetourOperation.erreur(new IOException(echecReseau())),
                sortie.resolve("apercu-bandeau-retour-externe.png"));
        rendre(
                RetourOperation.erreur(new IllegalStateException(messageSqlite())),
                sortie.resolve("apercu-bandeau-retour-borne.png"));
        rendre(
                RetourOperation.vueAmputee("Z1 du carre 640380", new ResteDeRestauration(List.of("Z1"), List.of())),
                sortie.resolve("apercu-bandeau-vue-amputee.png"));
        rendre(
                RetourOperation.vueAmputee("Ma saison 2026", new ResteDeRestauration(valeursDisparues(), List.of())),
                sortie.resolve("apercu-bandeau-vue-amputee-longue.png"));
        rendre(
                RetourOperation.filtresNonRepris(new ResteDeRestauration(List.of(), criteresNonOfferts())),
                sortie.resolve("apercu-bandeau-filtres-non-repris.png"));
        rendre(
                RetourOperation.vueAmputee(
                        "Rhinolophes au-dessus de 90 %",
                        new ResteDeRestauration(List.of("640380 · Z1"), List.of("proba", "heure"))),
                sortie.resolve("apercu-bandeau-vue-amputee-deux-causes.png"));
    }

    /// Le cas du **transport** (#476, #3093) : Sons & validation offre dix critères, l'analyse cinq. Passer
    /// à la carte élargit donc forcément le résultat, et c'est l'énumération la plus longue des trois
    /// messages. C'est celle qu'il faut regarder avant de la livrer.
    private static List<String> criteresNonOfferts() {
        return List.of("taxon", "references", "douteux", "non_identifie", "proba", "heure");
    }

    /// Le cas qui inquiète : une vue qui traverse un renommage y perd **toutes** ses valeurs d'un coup,
    /// pas une. Cinq points qualifiés font une énumération longue, et c'est celle-là qu'il faut regarder
    /// avant de la livrer (#3056).
    private static List<String> valeursDisparues() {
        return List.of("Z1", "Z2", "Z3", "Barbastelle d'Europe", "Aix-en-Provence");
    }

    /// Un refus métier que nous avons rédigé, déjà porteur de son geste attendu (ADR 2635).
    private static String refusEcritParNous() {
        return "La connexion à Vigie-Chiro est requise. Connectez-vous depuis le menu principal > Se connecter,"
                + " puis recommencez.";
    }

    /// Ce que rapporte la plateforme quand elle refuse une écriture : notre vocabulaire nulle part.
    private static String echecReseau() {
        return "HTTP 502 Bad Gateway sur PUT /api/v1/donnees/6871a2c4f1e2b30012d4a9c7/observations :"
                + " upstream connect error or disconnect/reset before headers (reset reason: connection"
                + " termination)";
    }

    /// Le cas qui a motivé la borne : un pilote qui rappelle la requête entière, 379 caractères.
    private static String messageSqlite() {
        return "[SQLITE_CONSTRAINT_FOREIGNKEY] A foreign key constraint failed (FOREIGN KEY constraint"
                + " failed) - while executing INSERT INTO observation (id_enregistrement, code_taxon,"
                + " temps_debut, temps_fin, frequence_mediane, indice_confiance, id_donnee_vigiechiro,"
                + " indice_observation, commentaire, est_reference, est_douteuse) VALUES"
                + " (?,?,?,?,?,?,?,?,?,?,?)";
    }

    private static void rendre(RetourOperation retour, Path fichier) {
        Label texte = new Label();
        texte.setWrapText(true);
        texte.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(texte, Priority.ALWAYS);

        Button fermer = new Button("✕");
        fermer.getStyleClass().add("bandeau-retour-fermer");

        HBox bandeau = new HBox(10, texte, fermer);
        bandeau.getStyleClass().add("bandeau-retour");
        BandeauRetour.installer(bandeau, texte, fermer, new SimpleObjectProperty<>(retour), () -> {});

        // Le contenu SOUS le bandeau est ce qui rend la borne lisible : sans lui, un bandeau de trois
        // lignes ressemble à un choix de mise en page plutôt qu'à un écran dont la liste a reculé.
        VBox cadre = new VBox(12, bandeau, contenuFactice());
        cadre.setStyle("-fx-padding: 16; -fx-background-color: #f5f6f8;");

        Scene scene = new Scene(cadre, LARGEUR, -1);
        // Les feuilles du socle arrivent par `Habillage`, que `ApercuFx` appelle (#3992). Les poser
        // ici serait une copie de plus du même geste - l'ADR 3374 en a déjà retiré trois.
        ApercuFx.enregistrerPng(scene, fichier);
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    /// Deux lignes de liste, pour situer le bandeau dans un écran plutôt que dans le vide.
    private static VBox contenuFactice() {
        VBox liste = new VBox(6);
        for (String ligne : List.of(
                "Car640380-2026-Pass2-Z1 · nuit du 22/06/2026 · 612 séquences",
                "Car640380-2026-Pass2-Z2 · nuit du 23/06/2026 · 438 séquences")) {
            Label item = new Label(ligne);
            item.getStyleClass().add("texte-secondaire");
            liste.getChildren().add(item);
        }
        return liste;
    }
}
