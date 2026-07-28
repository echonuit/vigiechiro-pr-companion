package fr.univ_amu.iut.cli.commande;

import fr.univ_amu.iut.commun.model.Certitude;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.validation.model.CriteresRevue;
import fr.univ_amu.iut.validation.model.SelectionObservations;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.List;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Option;

/// **Les cibles d'un geste de revue** (#1311), partagées par toutes les commandes qui en posent un.
///
/// Deux manières de désigner, exclusives l'une de l'autre, et **obligatoires** (on ne pose pas un geste
/// sans dire sur quoi) :
///
/// 1. `--observation 12,13,14` - le geste **chirurgical**, sur des lignes qu'on a **lues**
///    (`lister-observations` les donne). Un identifiant inconnu **arrête tout** : le lot est atomique, on
///    ne valide pas 2 lignes sur 3 en laissant deviner laquelle a échoué.
/// 2. `--passage 3 [--statut ..] [--taxon ..] …` - le geste **scripté**, reproductible, sur un
///    sous-ensemble **décrit**. Les filtres sont **exactement** ceux de `lister-observations` : ce qu'elle
///    montre est ce qu'on touche.
///
/// **Le garde-fou** : viser un passage **sans aucun filtre**, c'est viser **tout le passage** - des
/// centaines d'observations. Ce cas-là exige `--confirmer`. Ce n'est pas de la cérémonie : un filtre
/// oublié dans un script transforme « corrige ces trois lignes » en « corrige la nuit entière », et rien
/// dans la commande ne le distinguerait de l'intention.
///
/// Classe **partagée** plutôt que dupliquée dans chaque commande : sans elle, chaque geste réinventerait sa
/// façon de choisir, et ils divergeraient - c'est exactement ainsi qu'une CLI se met à ne plus faire ce que
/// son écran fait.
public final class CiblesRevue {

    @Option(
            names = "--observation",
            split = ",",
            paramLabel = "<ids>",
            description = "Identifiants des observations visées, séparés par des virgules (ex. 12,13,14). "
                    + "Ils s'obtiennent avec « lister-observations ».")
    private List<Long> ids;

    @ArgGroup(exclusive = false, heading = "%nOu bien, par filtres (les mêmes que lister-observations) :%n")
    private ParFiltres filtres;

    /// Désignation par **filtres** : le passage, et de quoi restreindre à l'intérieur.
    public static final class ParFiltres {

        @Option(
                names = "--passage",
                required = true,
                paramLabel = "<id>",
                description = "Identifiant du passage dont les observations sont visées.")
        private long passage;

        @Option(
                names = "--statut",
                paramLabel = "<statut>",
                description = "Ne vise que ce statut : ${COMPLETION-CANDIDATES}.")
        private StatutObservation statut;

        @Option(
                names = "--taxon-tadarida",
                paramLabel = "<code>",
                description = "Ne vise que les observations dont Tadarida propose ce taxon (ex. Pipkuh).")
        private String taxonTadarida;

        @Option(names = "--douteux", description = "Ne vise que les observations douteuses.")
        private boolean douteux;

        @Option(names = "--reference", description = "Ne vise que les observations de référence.")
        private boolean reference;

        @Option(
                names = "--certitude-posee",
                paramLabel = "<certitude>",
                description = "Ne vise que cette certitude observateur : ${COMPLETION-CANDIDATES}.")
        private Certitude certitude;

        @Option(
                names = "--a-enjeu",
                description = "Ne vise que les observations d'espèces prioritaires du Plan National d'Actions "
                        + "Chiroptères.")
        private boolean aEnjeu;

        @Option(names = "--confirmer", description = "Obligatoire pour viser un passage ENTIER (aucun filtre posé).")
        private boolean confirmer;

        /// Les drapeaux picocli sont binaires, le critère est **ternaire** : « absent » vaut `null` (« ne
        /// filtre pas là-dessus »), jamais `false` (« seulement les non-douteuses »).
        CriteresRevue criteres() {
            return new CriteresRevue(
                    statut,
                    taxonTadarida,
                    douteux ? Boolean.TRUE : null,
                    reference ? Boolean.TRUE : null,
                    certitude,
                    aEnjeu ? Boolean.TRUE : null);
        }
    }

    /// Les identifiants à traiter, quel que soit le chemin emprunté pour les désigner.
    ///
    /// @throws RegleMetierException si l'on vise un passage entier sans `--confirmer`, ou si les filtres ne
    ///     retiennent aucune observation
    public List<Long> resoudre(SelectionObservations selection) {
        if (ids != null && !ids.isEmpty()) {
            return List.copyOf(ids);
        }
        CriteresRevue criteres = filtres.criteres();
        if (criteres.vide() && !filtres.confirmer) {
            throw new RegleMetierException("Aucun filtre : ce geste viserait TOUTES les observations du passage "
                    + filtres.passage + ". Posez un filtre (--statut, --taxon-tadarida…), ou assumez le passage "
                    + "entier avec --confirmer.");
        }
        return selection.ids(filtres.passage, criteres);
    }

    /// De quoi annoncer, en clair, ce qui vient d'être touché.
    public String description(int nombre) {
        if (ids != null && !ids.isEmpty()) {
            return nombre + " observation(s) désignée(s) par identifiant";
        }
        return nombre + " observation(s) du passage " + filtres.passage
                + (filtres.criteres().vide() ? " (passage ENTIER)" : " (sélection filtrée)");
    }
}
