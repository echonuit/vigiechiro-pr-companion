# ADR 3450 - Une propriété de fuseau se tient en rejouant, pas en relisant

- **Statut** : Accepté - 2026-08-07
- **Chantier** : #3450, suite de l'[ADR 3406](3406-une-nuit-porte-le-fuseau-de-son-site.md)
- **Vérification** : certaine - `FuseauDExecutionTest#le_fuseau_annonce_est_celui_qui_tourne`

## Contexte

L'[ADR 3406](3406-une-nuit-porte-le-fuseau-de-son-site.md) a corrigé le fuseau des nuits d'écoute.
Elle n'a rien posé qui empêche le défaut de revenir.

Ce dépôt a une réponse habituelle à cela, et elle a bien servi : un garde qui porte sur la **forme** du
défaut plutôt que sur la liste des cas connus - `ScenesHabilleesTest`, `PoliceCouvreLIhmTest`,
`HabillageTest`, le contrôle de troncature. Le cinquième s'écrivait tout seul : refuser
`ZoneId.systemDefault()` appliqué à une heure **locale**, l'autoriser sur un `Instant`. La distinction
est juste, et les quatre appels restants sont bien tous des `Instant.atZone(...)`.

## Ce qui a fait abandonner ce garde

Confronté aux **lignes fautives d'origine**, il aurait manqué les deux moitiés du défaut.

| Moitié | Ce qui était écrit | Pourquoi la détection passe à côté |
| --- | --- | --- |
| écriture | `.atZone(ZoneId.systemDefault())` | les types locaux sont **deux lignes plus haut**, dans la signature de `rfc1123Utc(LocalDate, LocalTime)` |
| lecture | `.atZoneSameInstant(ZoneId.systemDefault())` | convertir un instant absolu **a l'air juste** ; ça l'est partout ailleurs dans ce produit |

Un garde qui ne voit **ni l'un ni l'autre** est pire que pas de garde : il fait croire la propriété
tenue ([ADR 2748](2748-un-dispositif-qui-peut-ne-rien-verifier-le-dit.md)).

Ce qui a effectivement mordu, c'est la **CI**. `boucle_ecriture_lecture_est_un_point_fixe` a rougi sur
#3434 parce que le fuseau du runner n'est pas celui du poste de développement - où
`ZoneId.systemDefault()` **est** `Europe/Paris`, si bien que les deux moitiés du code y parlaient le
même fuseau par accident, et qu'aucune reproduction locale n'était possible.

## Décision

La propriété à tenir est **comportementale**, pas syntaxique : *ce que le produit calcule pour une nuit
ne dépend pas du fuseau de la machine*. Elle se tient en **rejouant** la suite sous un autre fuseau, pas
en relisant le code.

Un job `fuseau-alternatif` rejoue donc **toute** la suite sous `America/Cayenne`, en parallèle de
`build`.

**Toute la suite, et non une liste de classes sensibles.** Une liste ne voit que ce qu'on y a mis et se
démode en silence ; ici, la « forme » du défaut est le fuseau lui-même, et toute classe qui en dépendra
rougira - y compris celles qui n'existent pas encore. C'est la leçon de
l'[ADR 3412](3412-un-alias-n-est-pas-une-police.md), appliquée à un garde qu'on ne pouvait pas écrire
textuellement.

**`America/Cayenne`**, pour trois propriétés qu'aucun autre choix ne réunit :

- c'est un **territoire français**, donc un cas réel du produit (#3442) et non une valeur de test ;
- l'écart avec `Europe/Paris` **franchit minuit** : un défaut y change la **date**, pas seulement
  l'heure. C'est le signal le plus fort, et c'est exactement ce que la mesure de #3406 avait montré ;
- il **n'observe pas l'heure d'été**, là où `Europe/Paris` l'observe : un décalage fixe ne se comporte
  pas comme un décalage saisonnier.

## Le dispositif se vérifie lui-même

Une première version passait le fuseau par `-Duser.timezone` sur la ligne Maven. Elle aurait pu être
**verte avec toute la suite tournant sous le fuseau du runner** : surefire fabrique ses propres JVM
(`forkCount=1C`), et une propriété donnée à Maven n'y descend pas forcément.

Le fuseau passe donc par l'**environnement**, hérité des forks. Et `VC_FUSEAU_ATTENDU` arme
`FuseauDExecutionTest`, qui vérifie **depuis l'intérieur de la suite** que la JVM tourne bien sous le
fuseau annoncé. Hors du job, la variable est absente et le cas est **ignoré**, ce que la suite affiche
plutôt que de le taire.

Le contrôle a été vu dans ses **trois** états avant d'être cru : ignoré sans la variable, vert quand la
zone est appliquée, et **rouge** quand elle ne l'est pas.

## Conséquences

- **le coût est assumé** : ~5 min de CI de plus par PR, en parallèle. Arbitrage rendu explicitement,
  contre deux options moins chères (une liste de classes à tenir ; épingler un fuseau non-Paris
  partout, qui aurait rendu un poste aveugle à ce que la CI voit) ;
- **93 classes de test de modèle** rejouées localement sous `America/Cayenne` avant livraison :
  **967 cas, aucun échec**. La suite était déjà indépendante du fuseau ; ce job garantit qu'elle le
  reste ;
- **il rend #3442 traitable.** Dériver le fuseau du site de ses coordonnées touche les deux moitiés,
  écriture et lecture ; sans ce job, le point fixe posé par #1860 se re-casserait sans bruit.

## Ce que ce job ne voit pas

Un défaut qui donnerait le **même** résultat sous les deux fuseaux. Il compare deux points, il ne
balaie pas l'espace des fuseaux. C'est écrit dans le workflow, à côté du job.

## Ce que cette ADR apprend au-delà de son cas

Quand le défaut n'a pas de **forme textuelle** stable, chercher sa forme **comportementale** : non pas
« quelle écriture est interdite ? » mais « sous quelle variation le résultat doit-il rester le même ? ».
La seconde question se pose à un environnement, pas à un analyseur, et elle ne se démode pas.
