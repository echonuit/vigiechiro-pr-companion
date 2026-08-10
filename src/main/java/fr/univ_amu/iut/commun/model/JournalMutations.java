package fr.univ_amu.iut.commun.model;

/// Port par lequel un service annonce qu'il vient de **valider une mutation structurelle** des
/// données (#3541) : ce qui permet à un écran de suivre la **base** au lieu de suivre la navigation.
///
/// ## Pourquoi un port, et pas la propriété observable elle-même
///
/// Les services qui écrivent vivent dans `model`, et `model` ne dépend pas de JavaFX (règle ArchUnit
/// `model_sans_javafx`). Le contrat se déclare donc ici, sans rien d'observable ; l'implémentation
/// observable vit dans `commun.viewmodel`. Même inversion que [Horloge], [EspaceDisque] ou
/// [CommunePoint].
///
/// ## Ce qu'est une mutation « structurelle », et ce qu'elle n'est pas
///
/// Celle qui peut changer l'**inventaire** que l'application affiche : le nombre de sites, de points
/// d'écoute, de passages ou d'observations. Un import, la création ou la suppression d'un site, la
/// réactivation d'un passage, une restauration.
///
/// **Pas** toute écriture en base. Une validation, un commentaire, un verdict, une disposition de
/// colonnes ne changent aucun de ces nombres : les annoncer ferait recalculer quatre `COUNT(*)` pour
/// un affichage identique. L'inventaire des gestes qui émettent, et leur granularité, est établi par
/// #3542.
///
/// ## La règle d'appel : tu écris, tu signales
///
/// **Après validation**, jamais avant : un signal posé sur une transaction qui échoue ensuite fait
/// afficher un état qui n'existe pas. C'est la seule règle.
///
/// Il y en avait une seconde à l'origine (#3541) : *une fois par opération métier, pas par ligne
/// écrite*. Elle est **abandonnée** depuis #3542, et pour une raison mesurée : la frontière d'une
/// opération métier n'est pas visible depuis l'endroit qui écrit. `RapprochementSites` crée les sites
/// **en boucle** en appelant le même service qu'un ajout manuel ; ce service ne peut pas savoir s'il
/// sert un geste ou une synchronisation de deux cent cinquante.
///
/// Demander à l'émetteur de reconnaître cette frontière, c'était donc éloigner l'appel du point
/// d'écriture et le rendre oubliable, alors que l'omission silencieuse est précisément le défaut que
/// ce mécanisme corrige. La rafale se règle **chez le lecteur**, qui amortit les signaux rapprochés
/// (cf. `RevisionDonnees`) : un endroit, sous test, au lieu d'une vigilance dans chaque appelant.
///
/// Une implémentation peut être appelée depuis **n'importe quel fil** : c'est à elle de rejoindre le
/// fil d'affichage si elle en a besoin.
@FunctionalInterface
public interface JournalMutations {

    /// Annonce qu'une mutation structurelle vient d'être validée.
    void mutationStructurelleValidee();
}
