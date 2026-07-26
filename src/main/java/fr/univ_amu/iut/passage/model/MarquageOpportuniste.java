package fr.univ_amu.iut.passage.model;

/// Port de **marquage opportuniste** (#2525) : une feature qui crée des passages sans être `passage`
/// — au premier chef `importation` — déclare la nature opportuniste d'une nuit importée **sans
/// dépendre de tout [ServicePassage]**. Interface étroite, à l'image des autres ponts consommés par
/// `importation` (InventaireBrutsSource, RegenerationSequences).
///
/// Implémenté par [ServicePassage#marquerOpportuniste] (le concept vit avec les règles R3/R4 qu'il
/// neutralise) et fourni par `PassageModule`.
@FunctionalInterface
public interface MarquageOpportuniste {

    /// (Dé)marque le passage `idPassage` comme participation opportuniste.
    void definir(long idPassage, boolean opportuniste);
}
