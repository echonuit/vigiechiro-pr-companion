package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.Prefixe;
import java.nio.file.Path;
import java.util.Optional;

/// Session d'enregistrement : agrégat de données produit par un passage (C6, table
/// `recording_session`). Relation **1:1 stricte** avec le passage (`passage_id` unique) : la
/// session regroupe les enregistrements originaux, les séquences d'écoute, le journal du capteur
/// et l'éventuel relevé climatique d'une même nuit.
///
/// Les volumes ([#volumeOriginauxOctets], [#volumeSequencesOctets]) sont des champs **dérivés**
/// (calculés à partir des fichiers sur disque), non autoritaires : le DAO se contente de les
/// mapper, sans garantir leur recalcul. Ils sont donc nullables.
///
/// La session ne porte **aucun marqueur** de disponibilité, ni pour l'audio ni pour les bruts : la
/// présence des fichiers est un état **observé** sur le disque ([DisponibiliteAudio], #1298), pas un
/// fait déclaré (ADR 0048). L'utilisateur possède ses fichiers : l'application les regarde, elle ne
/// les gouverne pas.
///
/// @param id clé technique, `null` avant insertion
/// @param cheminRacine chemin du sous-dossier workspace de la session (R22)
/// @param volumeOriginauxOctets volume total des originaux en octets (dérivé, optionnel)
/// @param volumeSequencesOctets volume total des séquences en octets (dérivé, optionnel)
/// @param idPassage identifiant du passage producteur (FK → `passage.id`, unique)
public record SessionDEnregistrement(
        Long id, String cheminRacine, Long volumeOriginauxOctets, Long volumeSequencesOctets, Long idPassage) {

    /// Préfixe R6 de la nuit (`Car130711-2026-Pass1-Z41`), relu du **nom du dossier** de la session.
    ///
    /// C'est le seul endroit où `passage` peut le retrouver sans dépendre de `sites` (cycle) : le carré et
    /// le code du point vivent là-bas. Vide si le dossier a été renommé à la main, ce que les appelants
    /// traitent chacun à leur façon (la voie « transformés » de la réactivation n'en a pas besoin, la voie
    /// « bruts » s'y refuse).
    public Optional<Prefixe> prefixe() {
        Path nom = Path.of(cheminRacine).getFileName();
        return Prefixe.depuisNomDossier(nom == null ? null : nom.toString());
    }

    /// Le chemin réel d'un fichier de cette session : **absolu tel quel, relatif résolu contre la
    /// racine** (#4666). Vivait en deux exemplaires, ici parce qu'ici vit la racine.
    ///
    /// **Aucune branche `isAbsolute()`, et c'est voulu** : `Path.resolve` rend trivialement un argument
    /// absolu, c'est son contrat. Les deux exemplaires en portaient une, morte.
    ///
    /// Un chemin absent et une session introuvable restent les cas de l'appelant, pas la règle.
    /// Ce que l'écart a coûté est écrit en #1994 : un chemin non résolu faisait échouer le dépôt.
    ///
    /// @param cheminFichier le chemin stocké, absolu ou relatif
    /// @return le chemin résolu
    public Path resoudre(Path cheminFichier) {
        return Path.of(cheminRacine).resolve(cheminFichier);
    }
}
