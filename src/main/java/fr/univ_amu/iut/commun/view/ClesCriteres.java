package fr.univ_amu.iut.commun.view;

/// Les **clés des critères de filtre** partagés entre écrans (#3096), et le concept que chacune
/// désigne.
///
/// ## Pourquoi un porteur unique
///
/// Une clé n'est pas un détail d'implémentation local. C'est le **contrat de sérialisation** des vues
/// mémorisées (`vue_sauvegardee.descripteur_json`) et la base du **transport** des filtres d'un écran à
/// l'autre (« Voir sur la carte », #476). Deux écrans qui filtrent le même concept doivent donc la
/// partager, et deux concepts différents ne doivent jamais la partager.
///
/// Avant cette classe, `lieu` était déclaré **quatre** fois, `groupe` trois, et l'écran audio écrivait
/// les siennes en littéraux. Rien n'empêchait un cinquième écran de nommer le même concept « lieux »,
/// ni deux écrans de nommer deux concepts « statut » : c'est précisément ce qui était arrivé.
///
/// ## Le cas qui a motivé le renommage
///
/// `statut` désignait le **statut d'observation** (à revoir / validée / corrigée) en audio et en
/// analyse, et le **statut de workflow** (importé / déposé / vérifié) en multisite. Les vues étant
/// persistées par écran, rien ne cassait ; mais le mécanisme de transport arme exactement ce piège, et
/// le jour où un écran enverrait ses filtres vers Carte & passages, un statut d'observation se
/// restaurerait en statut de workflow, ou serait jeté en silence.
///
/// D'où deux clés distinctes, dont les noms disent le concept. La rétrocompatibilité des vues déjà
/// enregistrées est assurée par [CritereFiltre#nomsHerites()], sans migration de base.
///
/// ## Ce que le contrat garantit
///
/// `ClesCriteresTest` interdit à tout catalogue de réécrire une de ces clés en littéral : elles se
/// déclarent ici, une fois. Une clé **propre à un seul écran** (la probabilité Tadarida, l'heure de
/// capture, l'année d'un passage) n'a pas sa place ici : elle reste dans son catalogue, où son unicité
/// ne pose aucune question.
public final class ClesCriteres {

    /// **Statut d'observation** : à revoir, validée, corrigée. Partagée par Sons & validation et
    /// Espèces & observations, qui filtrent bien la même chose.
    public static final String STATUT_OBSERVATION = "statut";

    /// **Statut de workflow** d'une nuit : importée, déposée, vérifiée, à réactiver. Propre à Carte &
    /// passages, et volontairement **distincte** de [#STATUT_OBSERVATION] : ce n'est pas le même
    /// concept, et les confondre sous une même clé rendait le transport faux.
    public static final String STATUT_WORKFLOW = "statut_workflow";

    /// **Taxon parent** (groupe taxonomique) : Chiroptères, Oiseaux, Orthoptères…
    public static final String GROUPE = "groupe";

    /// **Lieu**, toutes dimensions confondues : commune, carré, point, site. Le critère le plus
    /// partagé de l'application (quatre écrans).
    public static final String LIEU = "lieu";

    /// **Taxon retenu** (l'espèce), par son code.
    public static final String TAXON = "taxon";

    /// **Espèces à enjeu** : les espèces prioritaires du Plan National d'Actions Chiroptères.
    public static final String A_ENJEU = "a_enjeu";

    private ClesCriteres() {}
}
