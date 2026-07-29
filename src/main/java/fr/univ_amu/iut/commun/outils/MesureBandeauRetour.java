package fr.univ_amu.iut.commun.outils;

import fr.univ_amu.iut.commun.view.ConfirmationNavigation;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/// Outil de **mesure**, utilisable tel quel (#2076).
///
/// Le bandeau de retour affiche parfois un texte que **nous n'avons pas écrit** : un message de pilote
/// SQLite, une réponse HTTP, une trace réseau. L'issue #2076 partait d'une analyse du code, sans qu'aucun
/// débordement n'ait été **observé**, et supposait que « la troncature de JavaFX suffit peut-être ».
///
/// Cet outil répond à la question par la mesure, avant toute décision : il rend le bandeau avec des
/// messages de longueurs réelles et **imprime la hauteur** qu'il prend, à la largeur d'un écran.
///
/// Lancement : `./mvnw exec:exec` avec cette classe en `mainClass` (headless, cf. capture-screenshots.sh).
public final class MesureBandeauRetour {

    /// Largeur d'un écran de l'application, celle des captures.
    private static final int LARGEUR = 1000;

    private MesureBandeauRetour() {}

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch fini = new CountDownLatch(1);
        Platform.startup(() -> {
            try {
                mesurer();
            } finally {
                fini.countDown();
            }
        });
        fini.await();
        Platform.exit();
        System.exit(0);
    }

    private static void mesurer() {
        System.out.println("Hauteur du bandeau de retour selon la longueur du message (largeur " + LARGEUR + ") :");
        mesurerUn("refus métier écrit par nous", "Aucun fichier à importer : la carte ne contient aucun WAV.");
        mesurerUn(
                "échec HTTP typique",
                "L'opération Vigie-Chiro a échoué : HTTP 500 : {\"_status\": \"ERR\", \"_error\":"
                        + " {\"code\": 500, \"message\": \"Internal Server Error\"}}");
        mesurerUn("message de pilote SQLite", messageSqlite());
        mesurerUn("collage arbitraire dans un champ de saisie", "« " + "A".repeat(600) + " » n'est pas un numéro.");
    }

    /// Un message de pilote réaliste : le driver SQLite préfixe sa cause et rappelle la requête entière.
    private static String messageSqlite() {
        return "[SQLITE_CONSTRAINT_FOREIGNKEY] A foreign key constraint failed (FOREIGN KEY constraint"
                + " failed) lors de : INSERT INTO observation (id_sequence, taxon_tadarida, taxon_observateur,"
                + " certitude, temps_debut, temps_fin, frequence_mediane, probabilite, id_resultats,"
                + " id_donnee_vigiechiro, indice_observation, commentaire, est_reference, est_douteuse)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    }

    private static void mesurerUn(String cas, String message) {
        Label libelle = new Label(message);
        libelle.setWrapText(true);
        libelle.setMaxWidth(Double.MAX_VALUE);
        HBox bandeau = new HBox(10, libelle);
        bandeau.getStyleClass().addAll("bandeau-retour", "retour-erreur");
        HBox.setHgrow(libelle, javafx.scene.layout.Priority.ALWAYS);
        VBox racine = new VBox(bandeau);
        Scene scene = new Scene(racine, LARGEUR, 600);
        scene.getStylesheets().addAll(styles());
        // La largeur doit être IMPOSÉE avant la mise en page, sinon le libellé calcule sa taille sur une
        // seule ligne et toutes les longueurs rendent la même hauteur - une mesure qui ne mesure rien.
        racine.resize(LARGEUR, 600);
        racine.applyCss();
        racine.layout();
        System.out.printf(
                "  %-45s %4d car. -> libellé %4.0f x %3.0f px, bandeau %3.0f px%n",
                cas, message.length(), libelle.getWidth(), libelle.getHeight(), bandeau.getHeight());
    }

    private static List<String> styles() {
        List<String> feuilles = new ArrayList<>();
        for (String nom : List.of("palette.css", "base.css", "design.css")) {
            var url = ConfirmationNavigation.class.getResource(nom);
            if (url != null) {
                feuilles.add(url.toExternalForm());
            }
        }
        return feuilles;
    }
}
