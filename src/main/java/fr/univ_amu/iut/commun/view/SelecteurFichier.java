package fr.univ_amu.iut.commun.view;

import java.nio.file.Path;
import java.util.Optional;

/// Stratégie de **désignation d'un fichier ou d'un dossier** par l'utilisateur : « où enregistrer la
/// sauvegarde ? », « quelle sauvegarde restaurer ? ». Troisième contrat du socle avec [Confirmateur]
/// (le oui/non) et [Notificateur] (le compte rendu), et il existe pour la **même raison** qu'eux.
///
/// Cette raison est mécanique, et elle s'était vue à moitié jusqu'ici : un `showAndWait()` **fige** un
/// test TestFX headless. C'est vrai de l'`Alert` du compte rendu, et c'est tout aussi vrai du
/// `DirectoryChooser` / `FileChooser` **natif** qui ouvre l'action. Rendre le compte rendu injectable ne
/// suffit donc pas à rendre une action testable quand elle **commence** par un sélecteur : le test
/// s'arrête à la première ligne. C'est ce qui laissait la sauvegarde et la **restauration** (qui écrase
/// la base) sans aucun test de leur geste.
///
/// L'application branche [SelecteurFichierJavaFx] (vrais sélecteurs natifs) ; les tests un double qui
/// **répond** le chemin voulu, ou rien du tout (l'utilisateur a annulé) - ce dernier cas étant
/// justement celui qu'on n'avait jamais pu vérifier sur une action destructive. Voir
/// [SelecteurFichierModifiable] pour le porteur injectable.
public interface SelecteurFichier {

    /// Demande un **dossier**.
    ///
    /// @param titre titre du sélecteur (« Dossier où enregistrer la sauvegarde »)
    /// @param dossierInitial dossier proposé à l'ouverture, s'il existe
    /// @return le dossier choisi, ou vide si l'utilisateur a **annulé**
    Optional<Path> choisirDossier(String titre, Optional<Path> dossierInitial);

    /// Demande un **fichier existant**.
    ///
    /// @param titre titre du sélecteur (« Choisir une sauvegarde à restaurer »)
    /// @param dossierInitial dossier proposé à l'ouverture, s'il existe
    /// @param filtre types de fichiers proposés
    /// @return le fichier choisi, ou vide si l'utilisateur a **annulé**
    Optional<Path> choisirFichier(String titre, Optional<Path> dossierInitial, FiltreFichier filtre);
}
