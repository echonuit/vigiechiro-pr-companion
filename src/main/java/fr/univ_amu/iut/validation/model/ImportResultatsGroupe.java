package fr.univ_amu.iut.validation.model;

import fr.univ_amu.iut.commun.model.ActionGroupee;
import fr.univ_amu.iut.commun.model.CiblePassage;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.util.Objects;
import java.util.Optional;

/// « Importer les résultats Tadarida » appliqué à plusieurs passages (#2357, lot 3, PR 4/5).
///
/// ## Jamais de remplacement en lot
///
/// [ImportVigieChiro#importerRapide] sait remplacer un jeu existant. Cette action ne le lui demande
/// **jamais** : un passage qui a déjà ses résultats est **écarté**.
///
/// Réimporter n'est pas anodin - c'est aller rechercher ce qui a changé côté serveur, au prix de la
/// pagination complète, et cela touche à ce que l'observateur a validé à la main. Ce genre de décision
/// se prend nuit par nuit, en connaissance de cause, depuis l'écran de validation. Un lot qui
/// remplacerait vingt jeux de résultats parce qu'on a coché vingt lignes serait un piège, pas un
/// service.
///
/// ## L'absence de résultats n'est pas une erreur
///
/// Le serveur répond « 200, liste vide » tant que l'analyse n'est pas terminée (#1264). Pour un lot,
/// c'est un cas **courant** : on coche six nuits déposées la veille, deux sont analysées. Les quatre
/// autres remontent en **échec avec le motif du service**, qui dit pourquoi - et le lot continue.
///
/// On aurait pu les écarter d'avance, mais il faudrait interroger la plateforme pour chacune avant de
/// commencer : l'éligibilité doit rester **locale et peu coûteuse**, elle est consultée sur toute la
/// sélection.
public class ImportResultatsGroupe implements ActionGroupee {

    private final Optional<ImportVigieChiro> import_;
    private final ResultatsIdentificationDao resultats;

    /// @param import_ l'import, **optionnel** : sa liaison n'existe qu'en connexion à Vigie-Chiro
    public ImportResultatsGroupe(Optional<ImportVigieChiro> import_, ResultatsIdentificationDao resultats) {
        this.import_ = Objects.requireNonNull(import_, "import");
        this.resultats = Objects.requireNonNull(resultats, "resultats");
    }

    @Override
    public String libelle() {
        return "Importer les résultats";
    }

    /// Trois écarts, tous **locaux** : hors connexion, pas de participation rattachée, résultats déjà là.
    @Override
    public Optional<String> motifNonEligible(CiblePassage cible) {
        if (import_.isEmpty()) {
            return Optional.of("hors connexion à Vigie-Chiro");
        }
        if (!import_.get().estRattache(cible.idPassage())) {
            return Optional.of("pas encore déposé sur Vigie-Chiro");
        }
        if (resultats.findByPassage(cible.idPassage()).isPresent()) {
            return Optional.of("résultats déjà importés");
        }
        return Optional.empty();
    }

    /// Le jeton est ignoré : un import de résultats est une écriture d'un bloc, sans état intermédiaire
    /// nommé où s'arrêter. Le moteur l'interrompt donc **entre** deux passages, ce qui suffit.
    @Override
    public void executer(CiblePassage cible, JetonAnnulation jeton) {
        import_.orElseThrow().importerRapide(cible.idPassage(), false, (page, totalPages) -> {});
    }
}
