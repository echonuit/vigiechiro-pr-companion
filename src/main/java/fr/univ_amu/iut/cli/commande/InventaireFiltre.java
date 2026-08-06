package fr.univ_amu.iut.cli.commande;

import fr.univ_amu.iut.analyse.model.FiltresActivite;
import fr.univ_amu.iut.analyse.model.FiltresAnalyse;
import fr.univ_amu.iut.validation.model.EspecesPrioritaires;
import fr.univ_amu.iut.validation.model.FiltresLieu;
import fr.univ_amu.iut.validation.model.ObservationAnalyse;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import picocli.CommandLine.Option;

/// Les **cinq critères** de l'écran « Espèces & observations », partagés par `lister-especes` et
/// `lister-carres` (#3269).
///
/// Un mixin picocli plutôt qu'une classe mère : les deux commandes n'ont en commun que leurs filtres, ni
/// leurs colonnes ni leur agrégation - c'est précisément pourquoi ce sont **deux** commandes et non une
/// seule à `--regrouper`. Les colonnes d'une commande restent ainsi stables, ce qu'un script attend.
public final class InventaireFiltre {

    @Option(
            names = "--statut",
            paramLabel = "<statut>",
            description = "Ne garde que cet état de revue : ${COMPLETION-CANDIDATES}.")
    private StatutObservation statut;

    @Option(
            names = "--taxon-parent",
            paramLabel = "<taxon>",
            description = "Restreint à une catégorie taxonomique (Chiroptères, Oiseaux…). Correspondance "
                    + "partielle, insensible à la casse et aux accents.")
    private String taxonParent;

    @Option(
            names = "--lieu",
            paramLabel = "<lieu>",
            description = "Restreint à une commune, un carré ou un point (répétable). Correspondance "
                    + "partielle, insensible à la casse et aux accents. Un code de point seul (« A1 ») "
                    + "retient les A1 de tous les carrés : la sortie porte le carré, elle les distingue.")
    private List<String> lieux = List.of();

    @Option(
            names = "--nature",
            paramLabel = "<protocole|opportuniste>",
            description = "Restreint aux nuits du protocole, ou à celles réalisées sur le carré d'un tiers.")
    private String nature;

    @Option(
            names = "--a-enjeu",
            description = "Ne garde que les espèces prioritaires au Plan National d'Actions Chiroptères.")
    private boolean aEnjeu;

    /// Applique les cinq critères, du plus large au plus étroit.
    ///
    /// L'ordre n'est pas indifférent pour les **messages** : chaque refus nomme ce qui est présent **dans
    /// ce qu'il a reçu**, donc après les filtres précédents. « Taxons parents présents » posé après un
    /// `--lieu` annonce ceux du lieu retenu, et non ceux de toute la saison - ce qui serait trompeur.
    /// C'est l'ordre qu'`exporter-activite` suit déjà pour les mêmes critères.
    ///
    /// @param avertir reçoit ce qui doit être dit sans faire échouer la commande
    public List<ObservationAnalyse> appliquer(
            List<ObservationAnalyse> observations,
            Set<Long> nuitsOpportunistes,
            EspecesPrioritaires especesPrioritaires,
            Consumer<String> avertir) {
        if (observations.isEmpty()) {
            // Constater la base vide AVANT de filtrer. `--lieu` et `--taxon-parent` DÉSIGNENT, donc
            // refusent une valeur absente des lignes (ADR 3082) : sur une base sans aucune observation ils
            // refuseraient TOUT lieu et TOUT taxon, en disant « cela n'existe pas » là où la vérité est
            // qu'il n'y a rien du tout. Piège déjà payé sur `lister-passages`, et de même forme ici.
            return observations;
        }
        List<ObservationAnalyse> retenues = FiltresLieu.parLieu(
                observations, lieux, FiltresAnalyse::dimensionsLieu, FiltresAnalyse::dimensionsNommees);
        retenues = FiltresAnalyse.parTaxonParent(retenues, taxonParent);
        retenues = FiltresAnalyse.parStatut(retenues, statut);
        retenues = FiltresAnalyse.parNature(retenues, nature, nuitsOpportunistes);
        if (!aEnjeu) {
            return retenues;
        }
        // Le seul filtre dont un résultat vide a deux causes OPPOSÉES : aucune espèce prioritaire ici, ou
        // aucun référentiel du tout (ADR 3048). Même référentiel et même piège qu'`exporter-activite` :
        // l'avertissement est donc le sien, pas une seconde formulation du même fait.
        Set<String> prioritaires = especesPrioritaires.codes();
        FiltresActivite.avertissementReferentielVide(prioritaires).ifPresent(avertir);
        return FiltresAnalyse.aEnjeu(retenues, observation -> prioritaires.contains(observation.taxonRetenu()));
    }
}
