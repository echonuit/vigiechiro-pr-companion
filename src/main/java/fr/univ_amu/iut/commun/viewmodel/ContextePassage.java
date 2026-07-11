package fr.univ_amu.iut.commun.viewmodel;

import java.util.Objects;

/// Contexte de navigation d'un **passage** transmis aux écrans qui en dépendent (M-Passage et ses
/// écrans enfants : vérification, diagnostic, dépôt, validation).
///
/// Porte de quoi situer l'écran dans la hiérarchie de navigation (fil d'Ariane) **quelle que soit la
/// route d'arrivée** : l'identifiant du passage, son numéro (pour le libellé « Détails du passage
/// N° X ») et le [ContexteSite] de rattachement (carré/point), connu même lorsque le passage est
/// atteint depuis la vue multi-sites. Construit par M-Passage, qui possède ces trois informations, et
/// passé aux écrans enfants via les contrats socle `Ouvrir*`.
///
/// @param idPassage identifiant du passage
/// @param numeroPassage numéro du passage dans l'année (R3), pour le libellé du fil ; 0 si inconnu
/// @param site contexte du site de rattachement (carré, point, nom) ; jamais nul
public record ContextePassage(Long idPassage, int numeroPassage, ContexteSite site) {

    public ContextePassage {
        Objects.requireNonNull(idPassage, "idPassage");
        Objects.requireNonNull(site, "site");
    }

    /// Identité **compacte** du passage pour la **zone gauche** de la barre de statut (#1016) :
    /// « Carré 640380 · A1 · N° 3 ». Le segment « · N° X » est omis quand le numéro est inconnu (`0`).
    public String identiteStatut() {
        String base = "Carré " + site.numeroCarre() + " · " + site.codePoint();
        return numeroPassage > 0 ? base + " · N° " + numeroPassage : base;
    }
}
