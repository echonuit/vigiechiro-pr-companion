package fr.univ_amu.iut.commun.view;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.text.Font;

/// La police du produit, **embarquée** plutôt qu'empruntée à la machine (#3361).
///
/// ## Pourquoi elle est dans le jar
///
/// `base.css` demandait `"Segoe UI", "Roboto", "Helvetica Neue", sans-serif`. **Aucune des trois n'est
/// installée** sur un poste Linux courant ni sur `ubuntu-latest` : les deux retombaient donc sur
/// `sans-serif`, un **alias** que chaque système résout à sa façon - Noto Sans ici, une police plus
/// large sur le runner. Les trois noms cités ne servaient jamais, chez personne.
///
/// Deux conséquences, dont la seconde est la vraie :
///
/// - le garde de troncature des captures échouait en CI sur des libellés qui tenaient en local, à 13 ou
///   28 px près. Trois allers-retours sur la seule clôture du chantier #3151 ;
/// - **deux utilisateurs sur deux systèmes voyaient des rendus différents.** Un libellé qui tient chez
///   l'un peut tronquer chez l'autre, et aucun garde ne le voit : celui des captures ne tourne qu'en CI,
///   sur une seule machine.
///
/// ## Ce que l'installation garantit, et ce qu'elle ne garantit pas
///
/// Elle rend le rendu **reproductible**, pas parfait : un libellé peut toujours être trop long pour son
/// champ. Le garde de troncature reste donc utile - c'est son **verdict** qui devient fiable, puisqu'il
/// ne dépend plus de la machine qui l'a rendu.
///
/// ## Deux points d'entrée, et pas un
///
/// L'application charge `base.css` par `MainView.fxml`, mais les **41 outils de capture** montent leurs
/// scènes sans passer par le chrome. L'installation doit donc être appelée des deux côtés, et elle est
/// **idempotente** pour que l'ordre d'appel n'ait pas d'importance.
public final class Typographie {

    /// Le nom de famille tel que JavaFX l'enregistre, à citer en tête de `base.css`.
    public static final String FAMILLE = "Noto Sans";

    /// La **monospace**, pour les chemins de fichiers et les valeurs brutes (#3412).
    ///
    /// `monospace` est un **alias générique**, exactement comme `sans-serif` : chaque système le résout
    /// à sa façon, et deux utilisateurs ne voient pas la même chose. Le défaut de l'ADR 3361 se rejouait
    /// donc à l'identique dans les deux feuilles qui le demandaient - `lot.css` pour le chemin du
    /// dossier de dépôt, `importation.css` pour les valeurs d'aperçu.
    public static final String FAMILLE_MONO = "Noto Sans Mono";

    /// Chargées à la taille par défaut de JavaFX : la taille réelle vient du CSS, pas d'ici.
    private static final List<String> FICHIERS = List.of(
            "/fonts/NotoSans-Regular.ttf",
            "/fonts/NotoSans-Bold.ttf",
            "/fonts/NotoSansMono-Regular.ttf",
            "/fonts/NotoSansMono-Bold.ttf");

    private static final Logger LOG = Logger.getLogger(Typographie.class.getName());

    /// La suite du message d'alerte, identique dans les trois cas : ce qu'on perd, et ce qui continue.
    private static final String REPLI =
            ". Le rendu retombe sur la police du système : il redevient dépendant de la machine, et un "
                    + "libellé peut se tronquer ici sans se tronquer ailleurs (ADR 3361).";

    private static boolean installee;

    private Typographie() {}

    /// Enregistre la police auprès de JavaFX, une seule fois par JVM.
    ///
    /// **Best-effort par contrat** : une police introuvable ne fait pas échouer le démarrage. Le produit
    /// retomberait alors sur la police du système, c'est-à-dire sur le comportement d'avant - dégradé,
    /// mais jamais bloquant. Un écran qui ne s'ouvre pas serait un remède pire que le mal.
    ///
    /// ⚠️ Best-effort ne veut pas dire **muet** (ADR 0008) :
    /// les trois façons d'échouer - ressource absente, police illisible, lecture impossible - sont
    /// **journalisées**. Sans cela, le produit reviendrait en silence au défaut que cette classe existe
    /// pour supprimer, et personne ne saurait pourquoi un libellé tronque sur une machine et pas sur une
    /// autre. Le cliquet du dépôt a d'ailleurs refusé la première version, qui avalait l'exception.
    public static synchronized void installer() {
        if (installee) {
            return;
        }
        installee = true;
        for (String chemin : FICHIERS) {
            try (InputStream flux = Typographie.class.getResourceAsStream(chemin)) {
                if (flux == null) {
                    LOG.warning(() -> "Police absente du jar : " + chemin + REPLI);
                    continue;
                }
                if (Font.loadFont(flux, -1) == null) {
                    // `loadFont` rend null sans lever quand le fichier n'est pas une police lisible.
                    LOG.warning(() -> "Police illisible : " + chemin + REPLI);
                }
            } catch (IOException echec) {
                LOG.log(Level.WARNING, echec, () -> "Police non chargée : " + chemin + REPLI);
            }
        }
    }
}
