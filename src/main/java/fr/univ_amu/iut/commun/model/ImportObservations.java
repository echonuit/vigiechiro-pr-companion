package fr.univ_amu.iut.commun.model;

import fr.univ_amu.iut.commun.api.DonneeVigieChiro;
import java.util.List;

/// **Port** : importer les observations d'une nuit depuis Vigie-Chiro, depuis n'importe quel écran (#1264).
///
/// L'import lui-même vit dans la feature `validation` (il écrit les observations). Mais c'est depuis
/// **M-Passage** qu'on veut souvent le déclencher — juste après avoir constaté que l'analyse est terminée.
/// Or `passage` ne peut pas dépendre de `validation` : le graphe des features doit rester acyclique
/// (ArchUnit y veille). D'où ce contrat dans `commun`, que `validation` implémente et que `passage`
/// consomme — le même patron que [ReferentielPoint] et [CoordonneesPoint].
///
/// Le compte rendu est rendu sous forme de **texte prêt à afficher** : le bilan détaillé
/// (`BilanImport`) appartient à `validation`, et n'a pas à traverser le socle pour être relu par une
/// modale qui ne fait que l'annoncer.
public interface ImportObservations {

    /// La nuit est-elle rattachée à une participation Vigie-Chiro ? Sinon, il n'y a rien à importer (et le
    /// rattachement se fait depuis « Sons & validation »).
    boolean estRattache(Long idPassage);

    /// Importe les observations de la nuit. **Bloquant** (réseau) : à appeler hors du fil JavaFX.
    ///
    /// Lève une [RegleMetierException] quand il n'y a rien à importer — avec la **raison** (analyse jamais
    /// lancée, en cours, en échec…) plutôt qu'un refus muet (#1264).
    ///
    /// @param remplacer remplace le jeu existant en préservant les validations de l'observateur
    /// @return un compte rendu prêt à afficher
    String importer(Long idPassage, boolean remplacer);

    /// Variante **sans re-téléchargement** : importe des `donnees` **déjà rapatriées** par l'appelant, pour
    /// éviter de re-parcourir toutes les pages du réseau quand elles sont déjà en main (reconstruction d'un
    /// passage, #1522 : la nuit vient d'être téléchargée pour recréer ses séquences - la re-télécharger
    /// doublait le temps). Même écriture en base que [#importer(Long, boolean)], sans l'appel réseau.
    ///
    /// @param donnees les résultats déjà rapatriés (non vides : l'appelant l'a vérifié)
    /// @return un compte rendu prêt à afficher
    String importer(Long idPassage, List<DonneeVigieChiro> donnees, boolean remplacer);

    /// **Reconstruction instantanée par CSV** (#1565) : noms de séquences (fichiers) **distincts** présents
    /// dans un CSV Tadarida brut, dans leur ordre d'apparition. Le passage recrée ses lignes de séquences à
    /// partir d'eux **avant** l'import (l'import ignore les lignes sans séquence de même nom). Le CSV est
    /// pris tel quel (`String`) et non un `LigneObservation` : le format Tadarida est un détail de
    /// `validation`, il n'a pas à traverser le socle. Extraction seule, aucune écriture.
    ///
    /// @param contenuCsv le contenu du `participation-<id>-observations.csv` (téléchargé par le client)
    /// @return les noms de fichiers distincts, dans l'ordre ; liste vide si le CSV est vide ou illisible
    List<String> nomsSequencesCsv(String contenuCsv);

    /// Importe les observations d'un **CSV Tadarida brut** (#1565) pour un passage dont les séquences ont
    /// déjà été recréées (à partir de [#nomsSequencesCsv]). Les observations sont créées **sans ancrage
    /// plateforme** (`idDonneeVigieChiro = null`) : le CSV ne le porte pas ; l'ancrage est acquis plus tard,
    /// à la réactivation (#1571). Un seul téléchargement remplace ainsi les dizaines de pages de `donnees`.
    ///
    /// @param remplacer remplace le jeu existant en préservant les validations de l'observateur
    /// @return un compte rendu prêt à afficher
    String importerCsv(Long idPassage, String contenuCsv, boolean remplacer);

    /// Le passage a-t-il des observations **sans ancrage plateforme** (`idDonneeVigieChiro == null`) ?
    /// C'est le cas d'un passage reconstruit par CSV (#1565), dont l'ancrage — requis pour **publier des
    /// corrections** — n'est acquis qu'à la **réactivation** (#1571), quand l'audio (re)devient disponible.
    /// `false` si le passage n'a pas d'observations, ou si toutes portent déjà leur ancrage.
    boolean ancrageManquant(Long idPassage);
}
