package fr.univ_amu.iut.importation.model;

/// Levée pour interrompre **proprement** une opération longue (décompression d'un `.zip` #139 ou import
/// #146) à la demande de l'utilisateur. Distincte d'une [fr.univ_amu.iut.commun.model.RegleMetierException] :
/// la couche IHM la traite comme une **annulation** (état neutre + nettoyage des fichiers partiels), pas
/// comme un échec. Émise par [JetonAnnulation#leverSiAnnule()] entre deux unités de travail (fichiers).
public final class AnnulationImportException extends RuntimeException {

    public AnnulationImportException() {
        super("Opération annulée par l'utilisateur");
    }
}
