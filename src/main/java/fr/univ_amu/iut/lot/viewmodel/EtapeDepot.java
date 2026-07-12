package fr.univ_amu.iut.lot.viewmodel;

import fr.univ_amu.iut.commun.viewmodel.EtatEtape;

/// Une étape du stepper de dépôt de M-Lot : un libellé court et son état d'avancement ([EtatEtape])
/// relatif à l'étape courante. Le flux ordonné est : Préparer → Générer les archives → Téléverser →
/// Marquer déposé.
///
/// @param libelle le libellé court affiché dans la puce du stepper
/// @param etat son état (franchie / courante / à venir)
public record EtapeDepot(String libelle, EtatEtape etat) {}
