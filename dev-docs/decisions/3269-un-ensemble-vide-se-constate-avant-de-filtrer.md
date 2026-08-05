# ADR 3269 - Un ensemble **vide au départ** se constate avant de filtrer (précise 3082)

- **Statut** : Accepté - 2026-08-05
- **Chantier** : #3269, clôture des suites du chantier #3092
- **Vérification** : certaine - `CliInventaireTest#base_vide_ne_se_lit_pas_comme_un_lieu_absent`

## Contexte

L'[ADR 3082](3082-designer-refuse-qualifier-rend-vide.md) partage les critères en deux : celui qui
**désigne** une chose refuse quand elle n'existe pas, en nommant ce qui existe ; celui qui **qualifie**
rend légitimement un ensemble vide.

Elle raisonne sur un ensemble **peuplé**. Elle ne dit rien du cas où il n'y a **rien à filtrer du
tout**, et c'est là que sa règle se retourne :

```
$ vigiechiro lister-passages --lieu 640380      # base fraîche, aucun passage
Aucun passage pour « 640380 » parmi ceux retenus. Lieux présents : aucun.
```

La phrase est vraie mot à mot et **fausse d'intention**. Elle met en cause la valeur tapée, alors que la
valeur n'est pour rien dans le résultat : il n'y a aucun passage, et n'importe quel lieu aurait produit
le même refus. Le code de sortie vaut 2, ce qui fait échouer un script d'installation là où la réponse
correcte est « rien à traiter ».

Le piège s'est posé **deux fois** dans les mêmes suites, sur deux commandes écrites à quinze jours
d'intervalle : `lister-passages` (#3269, premier palier) puis `lister-especes` et `lister-carres`
(#3269, second palier). La seconde fois, il a été **cherché** parce que la première l'avait appris.

## Décision

Une commande **dont l'ensemble source peut être vide sans que rien ne soit anormal** le constate avant
d'appliquer ses critères de données, et rend alors un résultat vide en **succès**, dans le format
demandé.

C'est une garde **de commande**, pas de filtre, et elle porte deux conditions :

1. **Après la validation des arguments.** Une `--nature` inconnue, une `--nuit` illisible, une plage
   horaire à demi donnée sont des fautes d'**usage** : elles se refusent quelle que soit la base. La
   première écriture de cette ADR posait la garde avant tout, et faisait sauter ces refus - la suite
   `bats` l'a montré sur trois cas.
2. **Seulement là où la commande a une phrase de remplacement.** `lister-passages` sait dire « Aucun
   passage enregistré », les deux inventaires savent rendre un CSV d'en-têtes. Une commande qui n'a rien
   à dire de mieux garde le refus.

```java
List<LignePassage> toutes = registre.lister();
if (toutes.isEmpty()) {
    sortie.println(json ? "[]" : "Aucun passage enregistré.");
    return 0;
}
List<LignePassage> passages = restreindre(toutes);
```

Trois situations restent donc **distinctes**, et se lisent différemment :

| Situation | Sortie | Code |
|---|---|---|
| Rien en base | « Aucun passage enregistré », ou le CSV réduit à son en-tête | 0 |
| Des lignes, mais aucune ne passe les filtres | « Aucun passage ne correspond aux filtres (N au total) » | 0 |
| Une valeur **désignée** absente des lignes | refus qui nomme les valeurs présentes | 2 |

## Pourquoi

**Les trois situations appellent trois conduites.** Rien en base : importer. Rien après filtrage :
élargir. Valeur absente : corriger la frappe. Les confondre coûte à chaque fois le geste suivant, et la
première confusion est la plus chère, parce qu'elle accuse l'utilisateur d'une faute qu'il n'a pas
commise.

**Un refus doit rester une information sur ce qu'on a demandé.** Un message qui parle des lieux quand la
cause est l'absence totale de données déplace le sujet. Sur une sortie machine, il est pire encore : le
code 2 dit « ton invocation est fautive », alors que rien ne l'est.

**Un CSV vide reste un CSV.** Le résultat vide garde ses colonnes et son format : un script qui demande
du CSV en reçoit, et lit zéro ligne. Rendre un texte d'excuse à la place obligerait chaque appelant à
distinguer les deux cas.

**La garde se pose une fois, au bon endroit.** Elle vit avec les filtres, pas dans chaque commande :
pour les deux inventaires, c'est le premier geste d'`InventaireFiltre#appliquer`, que les deux
commandes partagent. Répartie dans les commandes, elle serait oubliée par la troisième.

## Conséquences

- **Toute commande qui gagne un critère qui désigne** doit poser cette garde. Aucun garde automatique ne
  l'impose : c'est une revue à faire, et cette ADR en est le rappel. Le signal à chercher dans une revue
  est un `parX` qui lève sur ensemble vide sans qu'aucun appelant n'ait constaté le vide avant.

### L'audit exhaustif, pour ne pas le refaire

Toutes les commandes appelant un filtre qui **désigne**, éprouvées sur le vrai binaire et une base
neuve (passe 7 de la clôture des suites de #3092) :

| Commande | Sur base vide | Verdict |
|---|---|---|
| `lister-passages --lieu` | code 0, « Aucun passage enregistré » | **garde posée** |
| `lister-especes` / `lister-carres --lieu` | code 0, CSV d'en-têtes | **garde posée** |
| `solde-saison --lieu` | code 0, « Aucun point suivi » | sain : `FiltresSaison.parLieu` **qualifie**, il ne lève pas |
| `lister-observations --lieu` | code 2, « Lieux présents : aucun » | **garde écartée**, voir ci-dessous |
| `exporter-activite --lieu` | code 2, « Lieux présents : aucun » | **garde écartée**, voir ci-dessous |
| `exporter-sons --lieu` | code 2, « Passage introuvable » | refus **antérieur** et correct |

Deux lignes méritent leur explication.

**`solde-saison` surprend** : la commande ressemble aux autres, jusqu'au nom de la méthode qu'elle
appelle, mais son filtre ne désigne pas. **Un filtre ne se juge pas à son nom** - `parLieu` existe dans
deux classes, et une seule refuse.

**Les deux gardes écartées l'ont d'abord été posées, puis retirées.** Sur ces commandes, l'ensemble
source vide n'est pas un état anodin : `--passage 1` peut désigner un passage **inexistant**, et un
succès silencieux masquerait alors que l'identifiant est faux. Le refus de #3082, lui, échoue
bruyamment. Sa suite `bats` l'énonce explicitement (« le taxon parent DÉSIGNE, donc il refuse sur une
base vide »), et l'écarter aurait renversé une décision prise sciemment - le commentaire du cas dit
qu'un premier jet attendait un code 0 et que c'était l'erreur.

La vraie réponse serait sans doute une troisième : « Passage 1 introuvable », distinguant le passage
**absent** du passage **vide**. Aucune des deux positions ne la donne, et c'est la limite assumée de
cette ADR.
- **Un test sur base vide vaut un test sur base peuplée.** Les deux fichiers de test concernés sèment
  donc **à la demande** plutôt que dans leur `@BeforeEach`, pour qu'un cas au moins voie la base vide.
  Un `@BeforeEach` qui sème toujours rend ce défaut **structurellement** intestable :
  `CliListerPassagesFiltresTest` porte le cas jumeau, sous le même nom, pour l'autre commande.
- Le compte affiché par « aucune ligne ne correspond » porte sur l'**ensemble non filtré**, ce que
  l'[ADR 3092](3092-un-filtre-ne-change-que-ce-quon-regarde.md) exige déjà des décomptes.

## Alternatives écartées

**Faire rendre une liste vide aux critères qui désignent, sur ensemble vide.** Écarté, et **essayé** :
poser la règle dans les filtres eux-mêmes (`if (retenues.isEmpty() && !source.isEmpty())`) la rendait
cohérente partout d'un seul geste. Mais elle défaisait alors l'ADR 3082 sur les commandes où celle-ci
avait été posée délibérément, et sans le vouloir. Un lieu mal tapé sur une base peuplée doit continuer
de refuser ; un `--passage` inexistant aussi. La garde reste donc **au niveau de la commande**, qui
seule sait si son vide est anodin.

**Laisser le refus, et corriger seulement sa phrase** (« aucun passage en base, donc aucun pour ce
lieu »). Écarté : le code de sortie resterait 2, et c'est lui que les scripts lisent. Une phrase juste
sur un code faux ne répare que la moitié visible.

**Un drapeau `--autoriser-vide`.** Écarté : il demande à l'utilisateur de prévoir un état qu'il ne
connaît pas encore, pour obtenir le comportement qui aurait dû être le défaut.
