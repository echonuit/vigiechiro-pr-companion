package fr.univ_amu.iut.passage.model;

import java.util.List;

/// Rapport d'une reconstruction (#1305) : ce que la plateforme a permis de recréer, et — tout aussi
/// important — **ce qui manque**. Un passage reconstruit depuis le serveur est **moins riche** qu'un
/// passage archivé par purge : le serveur rend les observations, pas le journal du capteur, pas le relevé
/// climatique, pas les séquences dépourvues d'observation. On le **montre**, plutôt que de laisser croire
/// à une équivalence.
///
/// @param idPassage identifiant du passage créé localement
/// @param sequencesRecreees lignes de séquences recréées (sans fichier : le passage naît **archivé**)
/// @param observationsImportees observations rapatriées depuis la plateforme
/// @param lacunes ce qu'un passage reconstruit n'a pas, en clair, pour l'afficher tel quel
public record RapportReconstruction(
        Long idPassage, int sequencesRecreees, int observationsImportees, List<String> lacunes) {

    public RapportReconstruction {
        lacunes = List.copyOf(lacunes);
    }

    /// Les lacunes **structurelles** d'un passage reconstruit depuis la plateforme : elles ne dépendent
    /// pas de la nuit, mais de ce que l'API sait rendre.
    public static List<String> lacunesConnues() {
        return List.of(
                "Aucun fichier audio : le passage naît archivé (l'écoute suppose de réimporter les fichiers d'origine).",
                "Aucun journal du capteur ni relevé climatique : la plateforme ne les restitue pas.",
                "Seules les séquences porteuses d'observations sont connues : celles que Tadarida n'a pas"
                        + " identifiées n'existent pas côté serveur.",
                "Aucune empreinte de fichier : une réactivation s'appuiera sur les noms et sur les cris"
                        + " eux-mêmes (vérification acoustique), pas sur une empreinte.");
    }
}
