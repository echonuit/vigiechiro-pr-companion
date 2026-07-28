package fr.univ_amu.iut.audio.viewmodel;

import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Action;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Avertissement;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Motif;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Segment;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Teinte;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Ventilation;
import fr.univ_amu.iut.validation.model.BilanPublication;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Traduit une **publication de corrections** vers Vigie-Chiro (#723) en compte rendu chiffré (#2358),
/// celui que rend [fr.univ_amu.iut.commun.view.PanneauCompteRendu].
///
/// Troisième et dernier branchement du lot, après l'import et la réactivation. C'est celui où la
/// proportion manquait le plus : la restitution textuelle annonçait « 12 corrections envoyées » puis
/// trois lignes d'écarts et une de refus, sans que rien ne dise **quelle part de la revue** avait
/// effectivement atteint la plateforme. Or c'est la seule question de l'observateur à cet instant.
///
/// Purement dérivé du [BilanPublication] : aucune donnée n'est recalculée ici.
public final class CompteRenduChiffrePublication {

    private CompteRenduChiffrePublication() {}

    /// Séparateur que le moteur pose entre le sujet d'un refus et sa cause.
    private static final String SEPARATEUR_CAUSE = " : ";

    /// Le compte rendu chiffré d'une publication terminée.
    ///
    /// @param bilan ce que la publication a écrit, écarté et s'est vu refuser
    /// @param actions ce que l'écran propose ensuite ; c'est lui qui sait où mènent ses boutons
    public static CompteRenduChiffre de(BilanPublication bilan, List<Action> actions) {
        return new CompteRenduChiffre(
                "Corrections publiées vers Vigie-Chiro",
                resultat(bilan),
                severite(bilan),
                List.of(),
                ventilation(bilan),
                motifs(bilan),
                avertissements(bilan),
                actions);
    }

    /// « 12 / 20 publiées » : la part qui a atteint la plateforme, comparée à tout ce qui a été revu.
    private static String resultat(BilanPublication bilan) {
        int total = total(bilan);
        return bilan.poussees() == total ? total + " publiées" : bilan.poussees() + " / " + total + " publiées";
    }

    /// Tout ce que la revue a produit : ce qui est parti, ce qui a été écarté avant l'envoi, ce que la
    /// plateforme a refusé. Les trois s'excluent, donc la ventilation est exhaustive par construction.
    private static int total(BilanPublication bilan) {
        return bilan.poussees() + bilan.ecartees() + bilan.echecs().size();
    }

    /// Un **refus de la plateforme** est une erreur : la correction n'y est pas et l'observateur croyait
    /// l'avoir envoyée. Un écart avant envoi n'en est pas une - il attend une action de sa part, et le
    /// compte rendu dit laquelle.
    private static Severite severite(BilanPublication bilan) {
        if (!bilan.sansEchec()) {
            return Severite.ERREUR;
        }
        return bilan.ecartees() > 0 ? Severite.AVERTISSEMENT : Severite.SUCCES;
    }

    private static Ventilation ventilation(BilanPublication bilan) {
        int total = total(bilan);
        if (total == 0) {
            return Ventilation.aucune();
        }
        List<Segment> segments = new ArrayList<>();
        ajouterSiPresent(segments, "Publiées", bilan.poussees(), Teinte.RETENU);
        ajouterSiPresent(segments, "À compléter", bilan.sansCertitude(), Teinte.ECARTE);
        // PRINCIPALE et non SECONDAIRE : cette dernière porte le vert de RETENU, prévue pour la seconde
        // part d'un couple de MÊME nature (les volumes lus/écrits). Dans une ventilation où chaque part a
        // un sens distinct, elle faisait lire « sans ancrage » comme une réussite - vu à la capture.
        ajouterSiPresent(segments, "Sans ancrage", bilan.sansAncrage(), Teinte.PRINCIPALE);
        ajouterSiPresent(segments, "Hors référentiel", bilan.horsReferentiel(), Teinte.REFERENCE);
        ajouterSiPresent(segments, "Refusées", bilan.echecs().size(), Teinte.REFUSE);
        return new Ventilation("Devenir des " + total + " observations revues", total, segments);
    }

    private static void ajouterSiPresent(List<Segment> segments, String libelle, int quantite, Teinte teinte) {
        if (quantite > 0) {
            segments.add(new Segment(libelle, quantite, String.valueOf(quantite), teinte));
        }
    }

    /// Les refus, **groupés par cause**, chacun ouvrant la liste des observations concernées.
    ///
    /// Le moteur écrit « Observation 42 (donnée d1, indice 3) : HTTP 404 (ancrage périmé…) » : le sujet
    /// est devant, la cause derrière. On coupe au **premier** séparateur - miroir exact de l'import, où le
    /// nom du fichier était en suffixe. Sans cette coupe, chaque observation serait son propre motif, et
    /// vingt refus pour une même panne feraient vingt lignes disant la même chose.
    private static List<Motif> motifs(BilanPublication bilan) {
        Map<String, List<String>> parCause = new LinkedHashMap<>();
        for (String echec : bilan.echecs()) {
            int coupure = echec.indexOf(SEPARATEUR_CAUSE);
            String cause = coupure < 0 ? "cause non précisée" : echec.substring(coupure + SEPARATEUR_CAUSE.length());
            String sujet = coupure < 0 ? echec : echec.substring(0, coupure);
            parCause.computeIfAbsent(cause, ignore -> new ArrayList<>()).add(sujet);
        }
        return parCause.entrySet().stream()
                .map(motif -> new Motif("observation(s) : " + motif.getKey(), motif.getValue()))
                .toList();
    }

    /// Ce que la ventilation ne porte pas : **quoi faire** de ce qui a été écarté, et ce que la phase
    /// d'ancrage a rapatrié au passage.
    ///
    /// Les écarts sont dénombrés par la barre ; ces mentions disent le **remède**, qui diffère pour
    /// chacun. Sans elles, l'observateur verrait trois parts grises sans savoir laquelle dépend de lui.
    private static List<Avertissement> avertissements(BilanPublication bilan) {
        List<Avertissement> avertissements = new ArrayList<>();
        if (bilan.sansCertitude() > 0) {
            avertissements.add(Avertissement.de(bilan.sansCertitude()
                    + " observation(s) à compléter : la plateforme exige la certitude avec le taxon, et elle"
                    + " n'est jamais posée par défaut."));
        }
        if (bilan.sansAncrage() > 0) {
            // Depuis #1838 la publication ancre elle-même ce qui peut l'être : ce qui reste n'est pas un
            // oubli de réimport, c'est une nuit sans participation à quoi s'ancrer. Le remède a changé.
            avertissements.add(Avertissement.de(bilan.sansAncrage()
                    + " observation(s) sans ancrage plateforme : rattachez la nuit à sa participation"
                    + " Vigie-Chiro."));
        }
        if (bilan.horsReferentiel() > 0) {
            // Cas normal, pas un incident : un taxon hors référentiel Vigie-Chiro n'y a pas d'identifiant.
            avertissements.add(Avertissement.info(bilan.horsReferentiel()
                    + " observation(s) hors référentiel Vigie-Chiro : elles restent locales, c'est attendu."));
        }
        if (!bilan.rapatriement().estMuet()) {
            // Le rapatriement d'ancrage ramène aussi les échanges avec le validateur (#1867). Les taire
            // reviendrait à laisser l'observateur les découvrir en ouvrant la bonne observation, par
            // hasard. Le texte vient du port d'import, qui seul sait ce qu'il a écrit.
            avertissements.add(Avertissement.info(bilan.rapatriement().texte()));
        }
        if (bilan.sansEchec() && bilan.ecartees() == 0 && bilan.poussees() > 0) {
            avertissements.add(Avertissement.succes(
                    "Toutes les corrections revues sont sur Vigie-Chiro : la publication est idempotente,"
                            + " la relancer ne créera pas de doublon."));
        }
        return avertissements;
    }
}
