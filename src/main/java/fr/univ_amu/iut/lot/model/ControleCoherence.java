package fr.univ_amu.iut.lot.model;

/// Un contrôle de cohérence affiché à l'étape « Préparer le lot » (#254) : son **libellé** court, son
/// **statut** (✓ / ✗ / ⚠) et un **détail** explicatif. La liste de ces contrôles, produite par
/// [VerificationCoherence#controler], permet à l'IHM d'afficher une **checklist vivante** (visible même
/// quand tout est satisfait) plutôt que de ne montrer que les échecs.
///
/// @param libelle intitulé court du contrôle (ex. « Transformation des enregistrements »)
/// @param statut [StatutControle] : satisfait, en échec (bloquant) ou en avertissement (non bloquant)
/// @param detail explication (confirmation si satisfait, raison et correction à apporter sinon)
public record ControleCoherence(String libelle, StatutControle statut, String detail) {

    /// `true` si ce contrôle **bloque** la préparation (statut [StatutControle#ECHEC]).
    public boolean estBloquant() {
        return statut == StatutControle.ECHEC;
    }
}
