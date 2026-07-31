# ADR 3082 - Un critère qui **désigne** refuse, un critère qui **qualifie** rend vide (précise 0014)

- **Statut** : Accepté - 2026-07-31
- **Chantier** : #3082, suites de la clôture de #2967
- **Vérification** : certaine - `FiltresActiviteTest#ce_qui_qualifie_rend_vide_sans_refuser`

> La moitié « refuse » de la règle est tenue par le test jumeau du même fichier,
> `#ce_qui_designe_refuse_et_nomme` : le garde des ADR n'accepte qu'une référence, la règle en a deux.

## Contexte

Une commande qui filtre peut rendre un ensemble vide pour deux raisons opposées : l'utilisateur a **mal
désigné** quelque chose (un lieu mal tapé, une nuit qui n'existe pas), ou il a **posé une question dont
la réponse est « rien »** (aucune nuit opportuniste, aucune espèce prioritaire).

Les deux produisaient le même fichier vide en code 0. Un script enchaîne, et l'expert reçoit une archive
creuse sans que rien ne l'ait signalé.

#2971 avait tranché le cas par cas : `--lieu` refuse, `--proba-min` ne refuse pas. La raison en était
écrite, mais comme une particularité de ces deux options. #3059, en portant cinq critères d'un coup, a
montré que c'était une **règle** : trois refusent, deux rendent vide, et la ligne de partage se prédit.

## Décision

**Ce qui DÉSIGNE refuse**, en nommant ce qui est présent. Un nom de lieu, une date, un taxon parent se
tapent de travers ; l'ensemble vide est alors une **faute de frappe**, pas une réponse. Le refus liste ce
qui existe **dans ce qu'il a reçu** — donc après les filtres précédents — parce que la correction consiste
toujours à lire cette liste.

**Ce qui QUALIFIE rend vide**, sans refuser. La nature d'une nuit, l'enjeu d'une espèce, un seuil
numérique ne peuvent pas désigner quelque chose d'inexistant : « aucune nuit opportuniste » est une
réponse, et souvent celle qu'on cherchait. Refuser obligerait à savoir d'avance ce qu'on va trouver.

**Le test est la question « cette valeur peut-elle être fausse ? »** Un lieu, oui. Un booléen, non. Un
seuil, non : il est hors bornes ou valide, et hors bornes se refuse à la saisie, pas au résultat.

## Conséquences

- La règle **se prédit** au moment d'ajouter une option, au lieu de se rejouer à chaque fois.
- Un critère qui qualifie doit **dire** quand son résultat vide vient d'un état dégradé plutôt que des
  données : c'est le prolongement de l'[ADR 3048](3048-la-parite-dune-sortie-machine-est-de-dire.md), et
  le cas rencontré est `--a-enjeu` sur un référentiel vide (#3079).
- Un seuil numérique reste à part : il ne refuse pas sur le vide, mais **nomme la meilleure valeur du
  lot** (`avertissementSeuilTropHaut`, #2971), ce qui transforme un silence en information actionnable.

## Alternatives écartées

- **Tout refuser.** Rendrait `--a-enjeu` inutilisable pour sa question la plus courante : « y en a-t-il ? ».
- **Tout accepter.** C'était l'état de départ pour `--lieu` : un lieu mal tapé produisait une archive vide
  en code 0, et le script continuait.
- **Un drapeau `--strict`.** Déplace la décision sur l'appelant, qui n'a aucun moyen de savoir laquelle
  des deux causes l'attend.
