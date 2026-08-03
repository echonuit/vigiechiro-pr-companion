package fr.univ_amu.iut.commun.view;

import java.util.Map;

/// Comment un critère se **nomme à l'écran**, à partir de sa clé.
///
/// ## Pourquoi cette classe existe
///
/// Trouvé en **ouvrant une capture** pendant la revue visuelle du chantier #3092, pas en lisant un
/// test. Le bandeau d'un écran qui n'a pas su reprendre des filtres affichait :
///
/// > sans 6 critères qu'il n'offre pas (taxon, **references**, douteux, **non_identifie**, proba,
/// > heure)
///
/// Les clés sortaient telles quelles au milieu d'une phrase française : tiret bas, accents manquants.
/// Ça se lit comme une faute de frappe ou une fuite technique. L'assertion qui existait,
/// `contains("proba")`, passait sans rien voir - « proba » est lisible **par hasard**.
///
/// ## Pourquoi ce n'est pas dans `ClesCriteres`
///
/// [ClesCriteres] est un **contrat** : les clés partagées entre écrans, et le concept de chacune. Une
/// clé propre à un seul écran n'y a pas sa place, son unicité ne posant aucune question.
///
/// Ici c'est une affaire de **présentation**, et elle doit couvrir **toutes** les clés, partagées ou
/// non : l'écran qui rend compte d'un critère qu'il n'offre pas nomme précisément celles qu'il ne
/// connaît pas. Les deux préoccupations sont distinctes, donc séparées.
///
/// Une clé sans libellé enregistré se rend **telle quelle** : c'est moins bon, mais lisible, et cela
/// vaut mieux qu'une exception dans un message d'avertissement.
public final class LibellesCriteres {

    /// Le libellé de chaque critère de l'application, tel que sa puce l'affiche.
    private static final Map<String, String> LIBELLES = Map.ofEntries(
            Map.entry(ClesCriteres.STATUT_OBSERVATION, "Statut"),
            Map.entry(ClesCriteres.STATUT_WORKFLOW, "Statut"),
            Map.entry(ClesCriteres.GROUPE, "Taxon parent"),
            Map.entry(ClesCriteres.LIEU, "Lieu"),
            Map.entry(ClesCriteres.TAXON, "Espèce"),
            Map.entry(ClesCriteres.A_ENJEU, "Espèces à enjeu"),
            Map.entry("references", "Références"),
            Map.entry("douteux", "Douteux"),
            Map.entry("non_identifie", "Non identifiés"),
            Map.entry("proba", "Proba"),
            Map.entry("heure", "Heure"),
            Map.entry("carre", "Carré"),
            Map.entry("campagne", "Campagne"),
            Map.entry("annee", "Année"),
            Map.entry("verdict", "Verdict"),
            Map.entry("analyse", "Analyse"),
            Map.entry("nuit", "Nuit"),
            Map.entry("natureNuit", "Nature de la nuit"));

    private LibellesCriteres() {}

    /// Le libellé de `cle`, ou la clé elle-même si elle n'est pas enregistrée.
    public static String de(String cle) {
        return LIBELLES.getOrDefault(cle, cle);
    }
}
