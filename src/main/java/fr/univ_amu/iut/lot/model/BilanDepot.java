package fr.univ_amu.iut.lot.model;

import java.util.List;

/// Compte rendu d'un **dépôt de nuit** sur VigieChiro ([DepotVigieChiro], #142) : l'identifiant de la
/// participation créée et le détail des fichiers téléversés / en échec. Un dépôt partiel (participation
/// créée mais des fichiers en échec) reste exploitable : les fichiers manquants peuvent être relancés.
///
/// @param participationId `_id` de la participation créée côté VigieChiro
/// @param deposees nombre de fichiers téléversés et finalisés avec succès
/// @param echecs noms des fichiers dont le téléversement a échoué (vide si dépôt complet)
/// @param octetsDeposes volume **effectivement en ligne** (#2653), somme des fichiers dont l'envoi a
///     abouti. Aucune surface ne le disait, et rien ne le mesurait pour lui-même : le téléverseur
///     calculait déjà la taille de chaque archive pour choisir sa voie d'envoi, et la jetait.
public record BilanDepot(String participationId, int deposees, List<String> echecs, long octetsDeposes) {

    public BilanDepot {
        echecs = List.copyOf(echecs);
        if (octetsDeposes < 0) {
            throw new IllegalArgumentException("volume déposé négatif : " + octetsDeposes);
        }
    }

    /// Bilan sans volume mesuré, pour les appelants qui n'en produisent pas (tests de plan, reprise d'un
    /// bilan historique). Un volume à zéro se lit « rien en ligne », ce que la surface sait taire.
    public BilanDepot(String participationId, int deposees, List<String> echecs) {
        this(participationId, deposees, echecs, 0);
    }

    /// `true` si tous les fichiers ont été déposés (aucun échec).
    public boolean estComplet() {
        return echecs.isEmpty();
    }
}
