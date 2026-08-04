# ADR 3168 - Un audit qui ne peut pas trancher **montre sans juger**, et s'abstient plutôt que d'inventer

- **Statut** : Accepté - 2026-08-04
- **Chantier** : #3168, lot 4 du chantier #3151
- **Vérification** : certaine - `AuditDepartementDuPointTest#legitime_et_suspecte_indiscernables`

> Les trois volets de la décision sont tenus par trois tests distincts : celui cité ci-dessus pour
> l'indiscernabilité, `AuditDepartementDuPointTest#severite_informative` pour le contrat de sortie, et
> `RegionsFrancaisesTest#abstentions` pour l'abstention d'écriture. Le garde des ADR n'accepte qu'une
> référence ; la décision en a plusieurs.

## Contexte

Depuis [ADR 2791](2791-la-commune-se-derive-du-gps-et-s-attache-au-point.md), le département d'un point
se lit de **deux façons** : par le numéro de son carré (ses deux premiers chiffres,
[ADR 2351](2351-un-nombre-de-contacts-se-lit-contre-un-referentiel-cite.md)) et par le préfixe du code
INSEE de sa commune. Cette ADR-là annonçait le diagnostic et le **différait** sans rien en décider.

Le lot 4 l'a écrit, et trois questions se sont posées, dont les réponses ne se devinent pas.

## Décision 1 : montrer l'écart sans le trier

Une divergence est **légitime** en bord de carré : un carré fait 10 km de côté et peut chevaucher deux
départements. Elle peut aussi trahir un GPS relevé au mauvais endroit ou un numéro de carré mal recopié.

L'audit **ne dispose d'aucun moyen de les départager** : il n'a ni la géométrie du carré, ni la distance
au bord. Il émet donc **le même constat** dans les deux cas, et le dit dans son détail (« l'écart est
peut-être normal »). Un test sème délibérément les deux situations côte à côte - un point du 640380
tombé dans les Landes, un carré du Bas-Rhin dont le point serait à Marseille - et vérifie qu'elles sont
**indiscernables** en sortie.

C'est un choix, pas un renoncement. Un tri approximatif serait pire que pas de tri : il donnerait à
l'utilisateur l'impression que la machine a déjà écarté les faux positifs, et le mènerait à ignorer les
constats restants aussi vite que s'ils étaient tous du bruit. Ici, c'est l'utilisateur qui connaît son
terrain ; l'application lui apporte le rapprochement qu'il ne pouvait pas faire, pas un verdict.

## Décision 2 : la sévérité informative est un **contrat de sortie**, pas une nuance d'affichage

`audit-coherence` rend **1** dès qu'un constat est en erreur. Le chevauchement de département étant le
cas **normal** d'un carré de 10 km, un `AVERTISSEMENT` - a fortiori une `ERREUR` - ferait échouer la
commande sur une base parfaitement saine, et casserait tout script qui l'appelle.

`Severite.INFO` n'est donc pas ici un réglage de couleur : c'est ce qui garde la commande utilisable.
Deux tests le tiennent, un par surface (`aDesErreurs()` reste `false`, et le code de sortie reste 0).

## Décision 3 : le constat vit sur le **point**, donc dans l'audit global seulement

`auditerPassage` répond à « cette nuit est-elle bien rangée ? » après réparation (#1347). Un département
divergent est une propriété du **point**, que rejouer un import ne change pas : l'y émettre le
répéterait à chaque nuit du même point sans jamais rien apprendre de neuf.

Il sort donc de `auditerTout()` - la porte que prennent l'écran Diagnostic **et** la commande - aux
côtés du balayage des dossiers orphelins, l'autre contrôle de portée non-passage.

## Décision 4 : deux écritures qu'on ne sait pas départager ne divergent pas

Les deux lectures ne s'écrivent pas dans le même alphabet : un carré corse porte `20` là où l'INSEE
écrit `2A`/`2B`, et l'outre-mer porte `97` contre `971`. Comparer les chaînes telles quelles ferait de
**chaque point corse** une divergence.

`RegionsFrancaises.memeDepartement` rend donc `true` dans ces cas, et **c'est une abstention, pas une
équivalence** : `20` ne dit pas laquelle des deux Corses, `97` ne dit pas lequel des six départements
d'outre-mer. La méthode n'affirme que les écarts qu'elle sait **démontrer**.

C'est le même principe que la décision 1, appliqué un cran plus bas : entre se taire et affirmer sans
savoir, un outil de diagnostic se tait.

## Conséquences

- La règle « les deux premiers chiffres du numéro sont le département » vit désormais en **un seul
  endroit**, `RegionDuCarre.departement`. Elle s'écrivait en trois exemplaires : celui-là, la fiche site,
  et `CarreGeo` - cette dernière **sans aucun appelant**, trouvée en interrogeant le graphe du dépôt
  plutôt qu'en relisant le code.
- Le prochain contrôle d'audit qui comparera deux dérivations d'une même donnée trouvera le patron
  écrit : une classe dédiée par confrontation (`AuditDepartementDuPoint`, comme `BalayageDisque` et
  `AuditEnLigne`), branchée dans `auditerTout`, et une catégorie de constat qui rejoint les puces de
  filtre par `CategorieConstat.values()`.
- Deux silences sont acquis et documentés : un point **sans commune résolue** ne produit rien (pas de
  seconde lecture, et l'absence de résolution est un état normal), un **numéro de carré illisible** non
  plus.

## Alternatives écartées

- **Trier légitime et suspect par une heuristique de distance** (le point est-il près du bord du
  carré ?). L'emprise réelle d'un carré n'est pas connue du modèle, et une approximation ferait passer
  un tri douteux pour un tri fait : l'utilisateur cesserait de regarder les constats restants.
- **Un repère permanent dans la fiche du point**, en plus de l'audit. Se justifie si le cas est
  fréquent ; personne ne sait encore s'il l'est, et l'audit est précisément ce qui le dira. La question
  se repose avec une mesure plutôt qu'une intuition.
- **Un `AVERTISSEMENT`** pour donner du poids au constat. Aurait fait rendre 1 à `audit-coherence` sur
  le cas normal ; le poids se serait payé en scripts cassés.
- **Comparer les deux écritures à l'égalité stricte**, en normalisant seulement la Corse. L'outre-mer
  aurait produit une divergence à chaque point, pour une différence de longueur d'écriture.
