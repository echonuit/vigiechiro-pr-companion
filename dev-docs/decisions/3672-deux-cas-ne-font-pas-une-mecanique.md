# ADR 3672 - Deux cas ne font pas une mécanique : un libellé dérivé se relibelle

- **Statut** : Accepté - 2026-08-14
- **Chantier** : #3672 (lot 2 du chantier #3536), après #3455
- **Vérification** : certaine - `SiteDetailRenommageViewTest#renommer_relibelle_l_etape`
- **Prolonge** : [ADR 3539](3539-un-etat-de-controle-se-lie-il-ne-se-photographie-pas.md)

## Contexte

Le `libelle` d'une `EtapeNavigation` est un `String` **immuable**, figé à l'empilement. Sur quinze
empilements, **treize** passent une constante - « Mes sites », « Importer une nuit » - et ne peuvent
donc pas se périmer. Deux le dérivent de la donnée, et les deux ont produit le même défaut à onze
jours d'écart :

| Écran | Libellé | Ce qui le périme |
|---|---|---|
| M-Passage | « Détails du passage N° X » | renommer la nuit (#3455) |
| Fiche site | « Carré N » | renommer le carré (#3672) |

L'arbitrage 3 du chantier #3536 avait tranché : **les libellés d'historique ne deviennent pas des
propriétés observables**, avec une clause de réouverture explicite - « si un deuxième cas apparaît, la
décision se rouvre ».

Le deuxième cas est arrivé. La décision a été rouverte, et **re-tranchée dans le même sens**.

## Décision

**Deux cas sur quinze ne valent pas une mécanique générale.** Un libellé dérivé de la donnée se
relibelle localement, en trois points qui vont ensemble :

1. l'écran **déclare** son libellé (`libelleFil()`), et sa classe `Navigation*` le **lit** à
   l'empilement au lieu de le fabriquer - une seule définition, sinon les deux divergent au premier
   changement ;
2. après une écriture qui touche l'identité, le rappel repasse par un **point d'entrée du
   contrôleur**, qui appelle `actualiserFil` → `Navigateur#actualiserLibelleCourant` ;
3. si l'écriture change un **agrégat détenu** par le ViewModel, celui-ci le **relit** : recharger ses
   listes ne suffit pas.

Le troisième point est ce que #3672 a ajouté à #3455, et il est plus large qu'un libellé.
`SiteDetailViewModel#rafraichir()` rechargeait points, passages, bandeau et statut sans jamais relire
le `Site`. Or tout ce qui parle d'identité en dérivait : le numéro **affiché dans l'en-tête**, le
département qui s'en déduit, le titre, le `ContexteSite` transmis à un passage ouvert depuis la fiche,
et la cible de « Voir sur la carte ». Le libellé de navigation gelé était la conséquence la **moins**
voyante de la même cause.

## Pourquoi ne pas généraliser, alors qu'il y a deux cas

Rendre les libellés observables demanderait de transformer `EtapeNavigation` - un `record` - en
porteur de propriété, de faire suivre le chrome, et de tenir un cycle d'abonnement de plus. Pour
**deux** écrans dont la correction locale tient en une méthode et un appel, c'est disproportionné.

⚠️ Et la clause de réouverture ne se rejoue pas indéfiniment : à un **troisième** cas, la question
redevient légitime, parce que la duplication du geste commencerait alors à coûter plus que la
mécanique.

## Le piège, et pourquoi le second cas ne s'est pas vu

M-Passage déclare `EmplacementNavigation` : son fil d'Ariane est **vivant**, il redemande son
emplacement à chaque affichage. Un libellé d'étape figé y produit une **contradiction visible** - le
fil dit « N° 2 », le bouton Retour « N° 7 ». C'est elle qui a fait remarquer #3455.

La fiche site ne le déclare pas : le fil retombe sur son repli historique et lit le **même** libellé
figé. Les deux s'accordent alors **sur la mauvaise valeur**. Rien ne se contredit à l'écran, donc rien
n'alerte : #3672 n'a été trouvée ni par l'usage ni par un test, mais par le balayage #3545.

⚠️ **Corollaire, contre-intuitif** : déclarer `EmplacementNavigation` sur un écran dont le libellé
d'étape est figé **fabrique** la contradiction au lieu de la corriger. Les deux se posent ensemble ou
pas du tout.

## Conséquences

- Le garde de #3702 rend l'arbitrage **exécutable** : il refuse un libellé calculé sans son
  relibellage, et nommera donc le troisième cas au lieu de le laisser passer inaperçu comme le second.
- Un ViewModel qui **détient** un `record` d'entité doit le relire après toute écriture qui le touche.
  `rafraichir()` recharge des listes ; il ne rafraîchit pas ce qu'on tient déjà.
- ⚠️ Le mécanisme correct **existait** dans `SiteDetailViewModel#modifierSite`, qui réassignait
  l'agrégat depuis le retour du service. Il était **mort** depuis que #1431 avait déporté l'édition
  dans une modale portant son propre ViewModel, et sa doc-comment disait encore « bouton header
  ✏ Modifier ». Trois tests l'exerçaient et passaient : ils n'ont jamais pu rougir, et le défaut qu'ils
  semblaient couvrir vivait à côté, sur le chemin réel. Retiré à la passe 7, avec la seule couverture
  qui lui était propre rapatriée au niveau du service.
