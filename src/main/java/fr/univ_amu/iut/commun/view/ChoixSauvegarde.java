package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.persistence.InventaireSauvegardes;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/// Désignation d'une **sauvegarde à restaurer**, parmi celles que l'application a écrites (#3197).
///
/// Quatrième contrat du socle avec [SelecteurFichier], [Confirmateur] et [Notificateur], et il existe
/// pour la même raison mécanique qu'eux : un `showAndWait()` **fige** un test TestFX headless.
///
/// ## Pourquoi il ne suffisait pas de garder le sélecteur de fichiers
///
/// La restauration commençait par un `FileChooser` **natif**, où l'utilisateur choisissait un `.db`
/// sans voir ni sa date, ni sa taille, ni ce que l'ensemble occupait - alors que le dossier peut
/// contenir des filets de migration accumulés depuis des mois. Il naviguait à l'aveugle au moment
/// précis où le geste écrase sa base.
///
/// La liste ne **remplace** pas la navigation libre : le choix « Parcourir… » reste, pour la
/// sauvegarde rangée ailleurs (disque externe, dossier personnel). Ce qui change, c'est qu'on ne
/// **commence** plus par là.
public interface ChoixSauvegarde {

    /// Demande laquelle restaurer.
    ///
    /// @param titre titre de la fenêtre (« Quelle sauvegarde restaurer ? »)
    /// @param dossier dossier inventorié, montré à l'utilisateur et point de départ de « Parcourir… »
    /// @param entrees ce que le dossier contient, **déjà filtré** par l'appelant selon ce qu'il sait
    ///     restaurer : une base ne se restaure pas depuis une sauvegarde complète, et réciproquement
    /// @param repli navigation libre, jouée si l'utilisateur clique « Parcourir… ». C'est l'appelant qui
    ///     la fournit : lui seul sait s'il cherche un **fichier** (base) ou un **dossier** (sauvegarde
    ///     complète), et la fenêtre n'a pas à le deviner de la nature des entrées
    /// @return le chemin choisi (dans la liste ou par navigation libre), ou vide si l'utilisateur a
    ///     **annulé**
    Optional<Path> choisir(
            String titre, Path dossier, List<InventaireSauvegardes.Entree> entrees, Supplier<Optional<Path>> repli);
}
