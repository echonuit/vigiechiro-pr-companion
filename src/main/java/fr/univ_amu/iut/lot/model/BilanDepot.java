package fr.univ_amu.iut.lot.model;

import java.util.List;

/// Compte rendu d'un **dépôt de nuit** sur VigieChiro ([DepotVigieChiro], #142) : l'identifiant de la
/// participation créée et le détail des fichiers téléversés / en échec. Un dépôt partiel (participation
/// créée mais des fichiers en échec) reste exploitable : les fichiers manquants peuvent être relancés.
///
/// @param participationId `_id` de la participation créée côté VigieChiro
/// @param deposees nombre de fichiers téléversés et finalisés avec succès
/// @param echecs noms des fichiers dont le téléversement a échoué (vide si dépôt complet)
public record BilanDepot(String participationId, int deposees, List<String> echecs) {

    public BilanDepot {
        echecs = List.copyOf(echecs);
    }

    /// `true` si tous les fichiers ont été déposés (aucun échec).
    public boolean estComplet() {
        return echecs.isEmpty();
    }
}
