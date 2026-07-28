package fr.univ_amu.iut.audio.viewmodel;

import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Action;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Avertissement;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Segment;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Teinte;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Ventilation;
import fr.univ_amu.iut.validation.model.BilanImport;
import java.util.ArrayList;
import java.util.List;

/// Traduit un **import de résultats depuis Vigie-Chiro** en compte rendu chiffré (#2358, #2651), celui
/// que rend [fr.univ_amu.iut.commun.view.PanneauCompteRendu].
///
/// `BilanImport` porte **sept** nombres ; l'écran n'en disait **qu'un**, dans une phrase (« Résultats
/// importés depuis Vigie-Chiro : 128 observation(s). »). Les six autres étaient jetés, dont les
/// **validations perdues** - le travail de revue de l'observateur qui disparaît, et que l'application
/// savait sans le dire.
///
/// Ce n'était d'ailleurs pas un manque de donnée mais un **écart CLI ↔ IHM** : `importer-vigie-chiro`
/// en imprime cinq depuis toujours.
///
/// Purement dérivé du [BilanImport] : aucune donnée n'est recalculée ici.
public final class CompteRenduChiffreImportVigieChiro {

    private CompteRenduChiffreImportVigieChiro() {}

    /// Le compte rendu chiffré d'un import terminé.
    ///
    /// @param bilan ce que l'import a inséré, écarté et préservé
    /// @param actions ce que l'écran propose ensuite ; c'est lui qui sait où mènent ses boutons
    public static CompteRenduChiffre de(BilanImport bilan, List<Action> actions) {
        return new CompteRenduChiffre(
                "Résultats importés depuis Vigie-Chiro",
                resultat(bilan),
                severite(bilan),
                List.of(),
                ventilation(bilan),
                List.of(),
                avertissements(bilan),
                actions);
    }

    /// « 128 importées », ou « 128 / 140 importées » quand des lignes ont été écartées. Pas d'écart
    /// affiché quand il n'y en a pas : un « 128 / 128 » fait chercher la différence.
    private static String resultat(BilanImport bilan) {
        int total = total(bilan);
        return bilan.importees() == total ? total + " importées" : bilan.importees() + " / " + total + " importées";
    }

    /// Tout ce que le CSV portait. `importees + ignorees` **est** le total des lignes par construction
    /// (`NoyauImportObservations` : `ignorees = lignes.size() - retenues.size()`), donc la ventilation est
    /// exhaustive avec ces deux parts, et il n'y en a pas de troisième à trouver.
    private static int total(BilanImport bilan) {
        return bilan.importees() + bilan.ignorees();
    }

    /// Perdre des validations est le seul fait qui coûte à l'observateur **du travail déjà accompli** :
    /// c'est lui qui décide de la sévérité, avant même les lignes écartées.
    private static Severite severite(BilanImport bilan) {
        return bilan.validationsPerdues() > 0 || bilan.ignorees() > 0 ? Severite.AVERTISSEMENT : Severite.SUCCES;
    }

    private static Ventilation ventilation(BilanImport bilan) {
        int total = total(bilan);
        if (total == 0) {
            return Ventilation.aucune();
        }
        List<Segment> segments = new ArrayList<>();
        ajouterSiPresent(segments, "Importées", bilan.importees(), Teinte.RETENU);
        ajouterSiPresent(segments, "Ignorées", bilan.ignorees(), Teinte.ECARTE);
        // « lignes » et non « observations » : les ignorées ne sont jamais DEVENUES des observations
        // (séquence absente, ou pas de taxon). Annoncer « le devenir de 140 observations » quand 12 n'en
        // ont jamais été promettrait un objet qui n'existe pas.
        //
        // ⚠️ Ce libellé n'est aujourd'hui **affiché nulle part** : `PanneauCompteRendu` ne lit pas
        // `Ventilation.libelle`, et les quatre traductions le composent pour rien (#2694). On l'écrit
        // juste quand même - le jour où la bande le rendra, ou le portera en texte accessible, il ne
        // faudra pas relire quatre traductions pour découvrir qu'elles se sont contredites.
        return new Ventilation("Devenir des " + total + " lignes reçues", total, segments);
    }

    private static void ajouterSiPresent(List<Segment> segments, String libelle, int quantite, Teinte teinte) {
        if (quantite > 0) {
            segments.add(new Segment(libelle, quantite, String.valueOf(quantite), teinte));
        }
    }

    /// Ce que la ventilation ne porte pas.
    ///
    /// Les **taxons hors référentiel** n'y ont pas leur place et ce n'est pas un arbitrage de goût : ils
    /// comptent des **codes de taxons distincts** (`taxonsAutoCrees.size()`, un `Set<String>`), là où les
    /// deux parts comptent des **observations**. Les mêler ferait une barre dont les segments ne mesurent
    /// pas la même chose, avec l'autorité du visuel.
    ///
    /// Les **validations** ne paraissent que si elles existent, jamais à zéro : elles valent 0 hors
    /// réimport, et « 0 validation perdue » annoncerait une absence là où il n'y avait rien à préserver.
    private static List<Avertissement> avertissements(BilanImport bilan) {
        List<Avertissement> avertissements = new ArrayList<>();
        if (bilan.validationsPerdues() > 0) {
            // Le fait le plus coûteux du bilan, et le seul que rien n'annonçait (#2651).
            avertissements.add(Avertissement.de(bilan.validationsPerdues()
                    + " validation(s) perdue(s) : elles n'ont pas retrouvé d'observation correspondante dans le"
                    + " nouveau jeu, et ce travail de revue est définitivement perdu."));
        }
        if (bilan.validationsPreservees() > 0 && bilan.validationsPerdues() == 0) {
            avertissements.add(Avertissement.succes(bilan.validationsPreservees()
                    + " validation(s) préservée(s) : votre travail de revue est" + " intact."));
        } else if (bilan.validationsPreservees() > 0) {
            avertissements.add(Avertissement.info(
                    bilan.validationsPreservees() + " validation(s) préservée(s) et réattachée(s)."));
        }
        if (bilan.ignorees() > 0) {
            avertissements.add(Avertissement.de(bilan.ignorees()
                    + " ligne(s) ignorée(s) : séquence audio absente, ou ligne sans taxon. Importez d'abord la"
                    + " nuit de ce passage si ses séquences manquent."));
        }
        if (bilan.taxonsHorsReferentiel() > 0) {
            // Cas normal, pas un incident : l'auto-enregistrement en souche a déjà eu lieu, rien à faire.
            avertissements.add(Avertissement.info(bilan.taxonsHorsReferentiel()
                    + " taxon(s) hors référentiel, auto-enregistré(s) en souches : leurs observations sont bien"
                    + " importées."));
        }
        if (bilan.observationsAvecEchange() > 0) {
            // #1867 : sans cette mention, l'observateur découvre un message du validateur par hasard, en
            // ouvrant la bonne observation.
            avertissements.add(Avertissement.info(bilan.observationsAvecEchange()
                    + " observation(s) portent un échange avec le validateur du MNHN."));
        }
        return avertissements;
    }
}
