package fr.univ_amu.iut.bibliotheque.model;

/// Une entrée de la **bibliothèque de sons de référence** (parcours P10, COULD) : le
/// rapprochement d'une [fr.univ_amu.iut.validation.model.Observation] marquée « référence » et
/// de la [fr.univ_amu.iut.passage.model.SequenceDEcoute] dont elle est extraite.
///
/// **Objet de présentation** (pas une entité persistée) : il transporte uniquement ce qui est
/// nécessaire au récapitulatif exporté (CSV) et à la copie du fichier audio correspondant. Aucune
/// dépendance JavaFX (couche `model` pure).
///
/// @param taxon code du taxon retenu : taxon observateur s'il a été validé, sinon taxon Tadarida
///     (jamais `null`, `taxon_tadarida` étant obligatoire au schéma)
/// @param nomSequence nom de fichier de la séquence d'écoute source
///     (`listening_sequence.file_name`)
/// @param cheminFichier chemin sur disque du fichier de séquence à copier
///     (`listening_sequence.file_path`, sous-dossier `transformes/`)
/// @param frequenceKHz fréquence médiane en kHz de l'observation (`null` si absente)
/// @param commentaire commentaire libre de l'observateur (`null` si absent)
public record EntreeBiblio(
        String taxon, String nomSequence, String cheminFichier, Integer frequenceKHz, String commentaire) {}
