# ADR 3483 - Un aperçu pose ce qu'il ne peut pas reproduire

- **Statut** : Accepté - 2026-08-07
- **Chantier** : #3483, suite de l'[ADR 3439](3439-un-masque-se-derive-de-la-scene-il-ne-se-recopie-pas.md)
- **Vérification** : certaine - `ArchitectureTest#capture_pose_son_temps_ecoule`

## Contexte

L'[ADR 3439](3439-un-masque-se-derive-de-la-scene-il-ne-se-recopie-pas.md) a retiré un masque de
23 × 24 pixels qui ne couvrait aucune carte : il cachait les deux chiffres d'une **estimation de temps
restant**. Le masque parti, l'écart est redevenu visible, et il restait l'un des **trois** derniers
écarts entre un rendu d'intégration continue et un rendu de poste - le seul des trois imputable au
produit, les deux autres étant la version du JDK et le nom de l'utilisateur dans un chemin.

`apercu-import-decompression-volume.png` annonçait « ~13 s restant » en CI et « ~15 s » ici, pour le
même état posé.

## Le défaut

`ProgressionOperation` lit l'horloge deux fois : `demarrer()` pose `System.nanoTime()`, `appliquer()`
en retranche la valeur courante. L'estimation extrapole cet écoulé. Pour une opération réelle, c'est
exactement ce qu'il faut.

Pour une capture, l'écoulé mesuré est le **temps que la machine a mis à rendre l'image précédente** :

```java
vm.marquerExtractionEnCours();       // demarrer() : t0
dormir(2500);
vm.progression().appliquer(...);     // écoulé = 2,5 s          -> « ~10 s »
rendre(scene, ...decompression.png); // durée VARIABLE
vm.progression().appliquer(...);     // écoulé = 2,5 s + rendu   -> « ~13 s » ou « ~15 s »
```

Ce qui rend ce défaut long à voir : le chiffre affiché est **plausible**. Une estimation de 13 s à
20 % d'avancement ne ressemble pas à une erreur ; elle ressemble au produit. Rien dans l'image ne dit
qu'elle a été écrite par le processeur qui l'a rendue.

## Ce qui a été écarté

**Supprimer l'estimation**, comme le fait déjà `SuiviProgression.apercu` en n'appelant pas `demarrer`.
Le geste est bon là où il est - un aperçu de dialogue n'a rien à estimer - mais ici l'estimation
**est** ce que les captures documentent : #146 pour la décompression, #2733 pour l'état où le compteur
ne bouge plus et où seul le volume écrit dit que ça avance. Une image sans estimation ne montrerait
plus la fonctionnalité qu'elle est là pour montrer.

**Injecter une horloge**, sur le patron d'`Horloge`/`HorlogeFigee` qui règle le même problème pour les
dates. C'est le geste habituel du dépôt, et c'était le premier réflexe. Il
suppose de porter une horloge jusqu'à `ProgressionOperation`, construit par `new` en **huit** endroits
dont cinq ViewModels, donc de changer cinq constructeurs et la douzaine de fichiers de test qui les
appellent - pour un besoin qui n'existe que dans l'outillage.

**Reposer la référence et redormir** avant chaque capture. L'écoulé resterait à la merci de
l'ordonnanceur : à 20 % d'avancement, 125 ms de retard suffisent à faire basculer l'arrondi d'une
seconde. Un déterminisme qui tient tant que la machine n'est pas chargée n'en est pas un.

## Décision

`ProgressionOperation.appliquer(Progression, Duration)` reçoit le temps écoulé **de l'appelant**.

Un outil de capture pose un état : le libellé, la fraction, et l'écoulé, qui n'est pas moins un
paramètre de l'image que les deux autres. Le laisser à l'horloge, c'est laisser la vitesse de la
machine décider du contenu du PNG.

Ce n'est pas un fac-similé, au sens que l'[ADR 3439](3439-un-masque-se-derive-de-la-scene-il-ne-se-recopie-pas.md)
reproche aux masques recopiés : la chaîne affichée reste calculée par `avecTempsRestant`, le code de
production, sur le chemin de production. Seule la **mesure** du temps, que la capture ne peut pas
reproduire, devient une donnée d'entrée.

Les trois états capturés posent 2,5 s / 3,5 s / 2,5 s, soit « ~10 s », « ~14 s » et « ~17 s ». Le
deuxième est délibérément plus grand que le premier : c'est le même avancement à un moment plus tard,
et l'estimation qui grandit pendant que le compteur ne bouge pas est précisément ce que #2733
documente. Les trois valeurs tombent loin d'un demi, donc à l'abri d'un basculement d'arrondi.

## Le garde

`ArchitectureTest#capture_pose_son_temps_ecoule` : aucune classe de `..outils..` n'appelle
`appliquer(Progression)`, la surcharge qui lit l'horloge.

Il porte sur la **forme** du défaut - tout outil de capture, pas les trois appels trouvés - comme
l'exige l'[ADR 3412](3412-un-alias-n-est-pas-une-police.md). Vu rouge avant d'être vu vert : en
réintroduisant l'ancien appel, il nomme le fichier, la méthode et la ligne.

`..perf..` en est exclu à dessein. Un banc de mesure lit l'horloge parce que **mesurer la machine** est
sa raison d'être, et il ne publie pas d'image.

## ⚠️ Ce que le test a appris : la séquence compte

En figeant les trois états, le test a **échoué** : le troisième annonçait « ~10 s » et non « ~17 s ».

La fraction est **monotone** (#814) : 0,126 posé après 0,20 ne redescend pas, et l'estimation se
calcule sur 0,20. La capture, elle, est juste - `marquerEnCours()` appelle `demarrer` entre les deux et
remet la fraction à zéro. Mais rien ne le disait, et rien ne l'aurait signalé : un outil qui poserait
une fraction plus basse **sans** repartir afficherait l'estimation de l'état précédent, dans une image
qui aurait l'air normale. Le test suit donc la séquence réelle de `CaptureImport`, `demarrer` compris.

## Conséquences

- **Deux rendus consécutifs sont identiques au bit près** sur les onze aperçus d'import, alors que
  `decompression-volume` et `en-cours` variaient d'une exécution à l'autre ;
- l'écart avec la version publiée tombe à **9 × 11 pixels** sur `decompression-volume`, un seul glyphe,
  celui du chiffre changé ; tous les autres écarts d'import tiennent **entièrement dans le rectangle
  de carte** dérivé par `ZoneCarteApercu`, c'est-à-dire dans le bruit de tuiles déjà exclu ;
- les divergences CI/poste passent de **3 à 2**, et les deux qui restent sont environnementales, plus
  du tout du produit ;
- `dormir(2500)`, appelé deux fois, disparaît : l'outil ne dort plus pour laisser une estimation
  s'établir, il la pose. Cinq secondes de moins par exécution de la galerie.

## Ce que cette ADR apprend au-delà de son cas

Une capture peut reproduire ce qu'elle **construit** : une base semée, un fuseau épinglé, une police
embarquée, une locale imposée. Elle ne peut pas reproduire ce qu'elle **subit**. Le temps qui passe en
est un cas, et la réponse vaut au-delà : ce qu'un aperçu ne peut pas reproduire doit devenir une
**entrée** qu'il pose, jamais une valeur qu'il lit.

La liste des choses subies n'est pas close. Elle s'est réduite écran par écran - la locale et le fuseau
([ADR 3389](3389-ce-que-l-application-affiche-tient-dans-la-police-embarquee.md)), la police
([ADR 3374](3374-une-fenetre-porte-son-habillage-ou-elle-n-est-pas-le-produit.md)), la mesure de texte
([ADR 3417](3417-la-galerie-rend-comme-une-machine-accordee-au-produit.md)), les masques
([ADR 3439](3439-un-masque-se-derive-de-la-scene-il-ne-se-recopie-pas.md)) - et chacune de ces passes a
trouvé la suivante en retirant ce qui la cachait.
