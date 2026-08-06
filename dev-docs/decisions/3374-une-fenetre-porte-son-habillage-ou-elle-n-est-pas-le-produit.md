# ADR 3374 - Une fenêtre porte son habillage, ou elle ne montre pas le produit

- **Statut** : Accepté - 2026-08-06
- **Chantier** : #3374, suite de l'[ADR 3361](3361-la-typographie-est-embarquee.md)
- **Vérification** : certaine - `ScenesHabilleesTest#toutes_les_scenes_passent_par_habillage`

## Contexte

L'[ADR 3361](3361-la-typographie-est-embarquee.md) a embarqué Noto Sans dans le jar pour que le
produit cesse de dépendre de la police de la machine. La conclusion était juste, la portée non :
**enregistrer une famille ne la sélectionne pas.** C'est `base.css` qui la demande, par
`-fx-font-family`, et seuls `MainView.fxml` et `EcranReglages.fxml` la déclaraient.

Onze scènes naissent dans l'application. La fenêtre principale porte `base.css` ; les **dix autres**
- modales de point, de site, de rattachement, de connexion, de qualification, dialogues de
progression, export du graphe, choix de sauvegarde - naissent d'un `new Scene(vue)` sur un FXML qui
déclare `palette.css` et `design.css`, jamais `base.css`.

Les outils de capture subissaient le même sort, pour la même raison : ils montent la vue **seule**,
sans le chrome qui la portait.

## La mesure qui a tranché

Après #3364, la CI a régénéré les 138 aperçus. **37 ont changé** : exactement ceux qui montent le
chrome. Comparaison du rendu de la CI à celui d'un poste de développement, aux mêmes options
(`-Dglass.platform=Headless -Dprism.order=sw -Djava.awt.headless=true`) :

| Aperçu | Porte `base.css` ? | Écart CI contre local |
| --- | --- | --- |
| `apercu-accueil.png` | oui (chrome) | **0 pixel sur 896 800** |
| `apercu-saison.png` | non | 176 926 px sur 613 600 (28,8 %), écart max 224/255 |
| `apercu-audit.png` | non | 197 612 px sur 691 200 (28,6 %), écart max 230/255 |

**Zéro pixel** sur l'écran qui porte la feuille : les deux environnements produisent le **même
fichier**. Il n'y a donc rien à aligner côté anticrénelage - hypothèse envisagée, et écartée par
cette ligne. Toute la divergence tenait au choix de la police.

Elle écarte aussi un diagnostic antérieur, qui lisait dans l'écart de 224/255 la preuve de deux
polices différentes : c'en était bien une, mais entre la vue **avec** et **sans** `base.css`, pas
entre les deux machines.

## Décision

Un point de passage unique, `Habillage`, définit ce que porte toute fenêtre du produit - la police
embarquée, `palette.css`, `base.css`, dans l'ordre du chrome - et **les fenêtres comme les captures**
s'en servent. Un aperçu montre alors l'écran de l'utilisateur par **construction**, non parce qu'on y
a pensé.

C'est le raisonnement déjà tenu par `Modales` pour la fermeture par Échap : plutôt qu'une consigne
recopiée dans chaque façade de navigation, un seul patron appelé à la création de chaque fenêtre.
Ajouter `@base.css` aux dix FXML aurait marché, et se serait défait au onzième.

## Conséquences

- **Les modales cessent de rendre dans une autre police que la fenêtre qui les porte.** C'était le
  défaut d'origine, resté entier à côté de #3361 ;
- **le verdict du garde de troncature devient transportable** : ce qui tient en CI tient en local, et
  réciproquement. C'est ce qui avait coûté trois allers-retours à la seule clôture du chantier #3151 ;
- 108 aperçus sur 138 changent à la première régénération. C'est la mesure de ce qui ne montrait pas
  le produit.

### L'ordre n'est pas cosmétique, et le mauvais échoue en silence

`base.css` consomme `-couleur-fond`, défini par `palette.css`. Posée à un **autre niveau** que
`palette.css` - sur la scène quand la palette est sur le nœud racine - la couleur ne se résout pas, et
JavaFX **avale la règle** en journalisant un `ClassCastException` sur `-fx-background-color`. La
fenêtre s'ouvre sans son fond, sans que rien n'échoue. Un premier essai l'a produit. `Habillage`
insère donc `base.css` juste après `palette.css`, **dans la liste où celle-ci vit**.

### Ce que le garde a trouvé et que la lecture n'avait pas vu

`ScenesHabilleesTest` a désigné quatre outils construisant une scène hors du chemin commun. Trois
posaient `palette.css` + `base.css` **à la main** - trois copies du même helper, chacune avalant en
silence une feuille absente - mais **aucun n'installait la police** : `base.css` y demandait une
famille non enregistrée. Parmi eux `MesureBandeauRetour`, qui mesure des hauteurs de texte enroulé :
il mesurait un bandeau qui n'existe pas.

Le garde lit les **sources**, pas le comportement : un test d'intégration devrait ouvrir chacune des
onze fenêtres, ce que la moitié refuse en headless. La contrepartie est assumée, comme pour les
inventaires de `cli-surface.bats`. Il vérifie en outre qu'il **sait encore voir** ce qu'il cherche,
faute de quoi il certifierait une absence qu'il ne saurait plus constater.
