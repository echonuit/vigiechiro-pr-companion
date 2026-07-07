package fr.univ_amu.iut.importation.model;

import java.nio.file.Path;

/// Un enregistrement original **à transformer**, découplant le **chemin physiquement lu** de son
/// **nom logique R6**.
///
/// Ce découplage permet à l'import de fonctionner dans deux modes :
///
/// - **conservation** (par défaut) : l'original est copié dans `bruts/` puis renommé R6 ; ici
///   `chemin` = le fichier de `bruts/` et `nomR6` = son propre nom (déjà préfixé).
/// - **sans copie** (économie d'espace) : l'original est lu **directement depuis la carte SD** (R9,
///   lecture seule), sans être copié ni renommé sur disque ; `chemin` = le fichier source et
///   `nomR6` = le nom R6 **calculé** ([Renommeur#nomApresRenommage(String, fr.univ_amu.iut.commun.model.Prefixe)]).
///
/// Le nommage des séquences (clé de jointure Tadarida) et le `nomFichier` persisté dérivent du
/// `nomR6`, jamais du nom de fichier de `chemin` : les deux modes produisent donc des séquences
/// **identiques**. Le `chemin` alimente le `cheminFichier` (`file_path`) de l'original persisté.
///
/// @param chemin chemin du WAV réellement lu (dans `bruts/` en conservation, sur la SD sans copie)
/// @param nomR6 nom logique R6 de l'original (base du nommage R8 des séquences et du `nomFichier`)
record SourceOriginal(Path chemin, String nomR6) {}
