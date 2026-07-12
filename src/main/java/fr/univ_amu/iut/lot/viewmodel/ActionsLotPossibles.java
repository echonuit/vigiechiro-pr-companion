package fr.univ_amu.iut.lot.viewmodel;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.lot.model.EtatLot;

/// Actions de dépôt possibles pour un état de lot, dérivées du **statut** ET de la **cohérence** (recalculée
/// à chaque chargement). Extrait du [LotViewModel] pour y concentrer la règle « un contrôle bloquant rend le
/// lot non conforme ».
///
/// Règle : un contrôle de cohérence en **échec** (ex. préfixe des séquences/originaux non réconcilié après un
/// changement de n° de passage) rend le lot **non conforme**. Dans ce cas, la vérification reste
/// **re-lançable** (« Vérifier et préparer » actionnable, hors passage déjà déposé) et la **suite est
/// neutralisée** (génération d'archives et dépôt indisponibles), même sous un statut persisté « Prêt à
/// déposer » qui a régressé depuis.
///
/// @param preparer « Vérifier et préparer » disponible (statut Vérifié, ou re-vérification d'un lot bloqué)
/// @param deposer dépôt possible (Prêt à déposer / Dépôt en cours, et cohérent)
/// @param genererArchives génération des archives possible (lot figé, et cohérent)
/// @param depose le passage est déjà déposé (statut terminal)
record ActionsLotPossibles(boolean preparer, boolean deposer, boolean genererArchives, boolean depose) {

    static ActionsLotPossibles depuis(EtatLot etat) {
        boolean coherent = !etat.aDesEchecs();
        StatutWorkflow statut = etat.statut();
        boolean depose = statut == StatutWorkflow.DEPOSE;
        boolean preparer = statut == StatutWorkflow.VERIFIE || (!coherent && !depose);
        boolean deposer =
                coherent && (statut == StatutWorkflow.PRET_A_DEPOSER || statut == StatutWorkflow.DEPOT_EN_COURS);
        boolean genererArchives = coherent
                && (statut == StatutWorkflow.PRET_A_DEPOSER
                        || statut == StatutWorkflow.DEPOT_EN_COURS
                        || statut == StatutWorkflow.DEPOSE);
        return new ActionsLotPossibles(preparer, deposer, genererArchives, depose);
    }
}
