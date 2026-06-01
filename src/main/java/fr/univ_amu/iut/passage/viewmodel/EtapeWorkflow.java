package fr.univ_amu.iut.passage.viewmodel;

import fr.univ_amu.iut.commun.model.StatutWorkflow;

/// Une étape du stepper de statut de M-Passage : un statut workflow et son état d'avancement
/// ([EtatEtape]) relatif au statut courant du passage. Le libellé d'affichage est
/// [StatutWorkflow#libelle()].
///
/// @param statut le statut workflow de l'étape
/// @param etat son état (franchie / courante / à venir)
public record EtapeWorkflow(StatutWorkflow statut, EtatEtape etat) {}
