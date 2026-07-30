package fr.univ_amu.iut.commun.persistence;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/// Ce qu'une **sauvegarde complète** a réellement emporté (#1346).
///
/// La copie saute les racines de session **inaccessibles** : typiquement une carte SD non montée, ou un
/// disque externe débranché. Elle le faisait jusqu'ici **en silence** : l'utilisateur repartait avec une
/// sauvegarde amputée, et la certitude de l'avoir complète. C'est précisément ce qu'on ne peut pas se
/// permettre avant un reset (#1151), dont la sauvegarde est le prérequis déclaré.
///
/// @param dossier le dossier de sauvegarde créé
/// @param sessionsCopiees nombre de dossiers de session effectivement copiés
/// @param racinesInaccessibles racines de session **sautées** (non montées, introuvables), en clair
public record BilanSauvegarde(Path dossier, int sessionsCopiees, List<String> racinesInaccessibles) {

    public BilanSauvegarde {
        Objects.requireNonNull(dossier, "dossier");
        racinesInaccessibles = List.copyOf(Objects.requireNonNull(racinesInaccessibles, "racinesInaccessibles"));
    }

    /// `true` si au moins une racine de session n'a pas pu être copiée : la sauvegarde existe, mais elle
    /// est **incomplète**, et le dire est tout l'objet de ce bilan.
    public boolean incomplete() {
        return !racinesInaccessibles.isEmpty();
    }

    /// Résumé prêt à afficher (IHM comme CLI) : ce qui a été copié, et ce qui manque.
    public String enClair() {
        String resume = sessionsCopiees + " dossier(s) de session copié(s)";
        if (!incomplete()) {
            return resume + ".";
        }
        return resume + ", " + racinesInaccessibles.size()
                + " inaccessible(s) (carte SD non montée, disque débranché ?) :\n"
                + String.join(
                        "\n",
                        racinesInaccessibles.stream()
                                .map(racine -> "  - " + racine)
                                .toList());
    }
}
