package fr.univ_amu.iut.audit.model;

import java.nio.file.Path;
import java.util.List;

/// Ce qu'un retrait de dossiers orphelins a **réellement** fait (#3482).
///
/// Les trois champs répondent aux trois questions que se pose l'utilisateur après le ménage : qu'est-ce
/// qui est parti, qu'est-ce qui est resté, et combien de place ai-je regagnée.
///
/// `resistants` n'est pas un détail défensif : c'est la seule chose qui distingue « j'ai fait le ménage »
/// de « j'ai cru faire le ménage ». Un dossier verrouillé par l'explorateur Windows est le cas courant,
/// pas le cas tordu.
///
/// @param retires dossiers effectivement disparus du disque
/// @param resistants dossiers encore présents après la tentative, avec la raison
/// @param octetsLiberes taille cumulée des seuls dossiers `retires`
public record BilanNettoyage(List<Path> retires, List<DossierResistant> resistants, long octetsLiberes) {

    /// Un dossier qu'on n'a pas réussi à retirer, et pourquoi.
    public record DossierResistant(Path dossier, String raison) {}

    public BilanNettoyage {
        retires = List.copyOf(retires);
        resistants = List.copyOf(resistants);
    }

    /// Vrai si le disque n'a pas bougé : ni retrait, ni résistance.
    public boolean estVide() {
        return retires.isEmpty() && resistants.isEmpty();
    }

    /// Vrai si tout ce qui était demandé est parti.
    public boolean estComplet() {
        return resistants.isEmpty();
    }
}
