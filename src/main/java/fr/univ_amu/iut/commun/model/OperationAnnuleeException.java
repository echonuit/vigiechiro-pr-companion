package fr.univ_amu.iut.commun.model;

/// Signale qu'une opération longue s'est arrêtée **proprement** parce que son annulation coopérative a
/// été demandée ([JetonAnnulation#leverSiAnnule()], #1252). Distincte d'une [RegleMetierException] : la
/// couche IHM la traite comme une **annulation** (retour à un état neutre, nettoyage éventuel), pas comme
/// un échec - le socle [fr.univ_amu.iut.commun.view.ExecuteurTache] la distingue des erreurs et conclut
/// par le callback `annule` (jamais par `echec`).
///
/// Non finale : une exception d'annulation propre à une feature (p. ex. celle de l'import) peut en
/// dériver pour rejoindre le traitement uniforme du socle sans casser ses appelants.
public class OperationAnnuleeException extends RuntimeException {

    public OperationAnnuleeException() {
        super("Opération annulée par l'utilisateur");
    }
}
