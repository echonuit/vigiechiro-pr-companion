# Les clips perceptifs, tournés sous Windows

!!! warning "Spike. Cette page est jetable."

    Elle répond à une question, elle ne garde rien. Ses clips viennent de la pré-version
    **`clips-windows-spike`**, qui n'est ni `clips-recette` ni une version du produit. Si le spike
    ne se transforme pas en chantier, cette page, `spike-clips-windows.yml` et cette pré-version
    disparaissent ensemble.

## La question

La session [S10 · le poste Windows](../recette/sessions/s10-le-poste-windows.md) n'a jamais eu de
clips. Ce n'est pas un oubli : le banc bash filme le **bureau X**, et il lui faut Xvfb, openbox,
xdotool et l'absence de `WAYLAND_DISPLAY`. Aucun de ces quatre n'existe sous Windows.

Le banc en Java pur ne filme pas un bureau. Il photographie le **graphe de scène** par
`Scene.snapshot`, dans le mode Monocle headless où les tests tournent déjà, et où
`suite-sous-windows-et-macos.yml` fait tourner la suite entière depuis #3526.

La question est donc : **ce banc-là produit-il les neuf clips perceptifs sur un runner Windows, et
qu'y voit-on ?**

## Ce qu'il faut regarder ici

La phrase sous chaque clip dit **ce qu'il faut y voir**, mot pour mot celle de
[Cas perceptifs](../recette/clips-perceptifs.md), pour que les deux pages se comparent clip par
clip. Mais le verdict qu'on cherche ici n'est pas celui du produit : c'est celui du **banc**.

Deux questions, dans cet ordre :

1. Le clip montre-t-il la même chose que son jumeau tourné sous Linux ? Si oui, le poste Windows
   peut avoir ses clips, et la session S10 cesse d'être aveugle.
2. Le clip montre-t-il ce que l'application fait *vraiment* ? Un banc qui déplace un menu produit un
   clip qu'on juge, et on juge alors le banc en croyant juger le produit.

### S1-26 · la modale de connexion s'ouvre

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-windows-spike/ScenarioPerceptifConnexionTest.la_modale_de_connexion_s_ouvre.mp4"></video>

Rien ne doit se replacer après coup : la saisie est en place dès l'ouverture.

### S1-27 · pendant la récupération

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-windows-spike/ScenarioPerceptifConnexionTest.la_recuperation_ne_pousse_rien_hors_du_cadre.mp4"></video>

Rien ne sort du cadre avant que le bandeau d'état ait pris sa place.

### S1-37 · récupérer un carré

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-windows-spike/ScenarioPerceptifRecuperationCarreTest.la_recuperation_ramene_sur_mes_sites.mp4"></video>

L'enchaînement « je récupère, la fenêtre se ferme, la fiche s'ouvre » paraît naturel.

### S4-33 · le refus de dépôt

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-windows-spike/ScenarioPerceptifRefusDepotTest.le_compte_rendu_dit_les_refus_et_conseille_la_reconnexion.mp4"></video>

La phrase se lit d'un trait, et le conseil de reconnexion ne se noie pas dans le constat.

### S6-25 · une puce fraîchement ajoutée

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-windows-spike/ScenarioPerceptifFiltresTest.une_puce_fraichement_ajoutee_n_ecarte_rien.mp4"></video>

La table ne bouge pas tant qu'aucune valeur n'est choisie.

### S6-26 · rouvrir une liste après un autre filtre

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-windows-spike/ScenarioPerceptifFiltresTest.rouvrir_une_liste_apres_un_autre_filtre_montre_moins_de_valeurs.mp4"></video>

Elle offre moins de valeurs qu'à la première ouverture, et celles qui restent sont bien celles que
l'autre filtre laisse passer.

### S6-27 · une valeur devenue impossible

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-windows-spike/ScenarioPerceptifFiltresTest.une_valeur_cochee_devenue_impossible_se_distingue.mp4"></video>

Elle reste cochée, rangée à part, et se **distingue à l'œil** d'une valeur ordinaire à taille d'écran
habituelle. C'est ce dernier point que le test ne sait pas trancher.

### S6-28 · une vue rejouée sans l'une de ses valeurs

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-windows-spike/ScenarioPerceptifFiltresTest.rejouer_une_vue_dont_une_valeur_a_disparu_fait_paraitre_le_bandeau.mp4"></video>

Le bandeau paraît, et la phrase nomme la valeur manquante sans jargon ni clé technique.

### S6-29 · tout effacer

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-windows-spike/ScenarioPerceptifFiltresTest.tout_effacer_rend_la_table_entiere.mp4"></video>

La table revient entière et le tri d'origine est remis, en un seul clic.

## Ce que le tournage a donné

Deux tournages, verts tous les deux : le
[premier](https://github.com/echonuit/vigiechiro-pr-companion/actions/runs/32549560456), puis le
[second](https://github.com/echonuit/vigiechiro-pr-companion/actions/runs/32550194365) une fois les
respirations rendues au banc. Les chiffres ci-dessous sont ceux du second, qui est celui que la page
montre.

| | |
|---|---|
| Clips produits | **9 sur 9**, et une fenêtre a paru dans chacun |
| Verdict des cas | **9 tests verts**, 0 échec |
| Index par cas | **9 lignes sur 9** (`forkCount=1`) |
| Durée de Maven | **2 min 41 s** (1 min 31 s sans les respirations) ; le job entier tient en 4 minutes |
| Xvfb, openbox, xdotool | **aucun**, et rien à leur place |
| `ffmpeg` | **absent** de l'image `windows-latest` ; `choco install` le pose en 22 s |
| Remuxage `mkv` vers `mp4` | sans objet : le banc écrit du `mp4` |

Les clips sont sur la pré-version
[`clips-windows-spike`](https://github.com/echonuit/vigiechiro-pr-companion/releases/tag/clips-windows-spike).
Trois d'entre eux ont été **retéléchargés depuis cette adresse**, puis ouverts image par image et
mesurés : cette page ne repose pas sur la seule promesse du job, ni sur des fichiers restés dans le
runner.

Le rendu Windows n'est pas au pixel près celui de Linux : à polices embarquées identiques, un bouton
mesure quelques pixels de plus, assez pour que « Choisir un ... » y devienne « Choisir un taxo... ».
Sans conséquence pour un cas perceptif, à savoir si un cas juge un jour une largeur.

## Ce que le spike a trouvé

### 1. Aucun clip ne respirait

C'est le défaut que le premier tournage Windows a livré, et il ne s'est vu qu'à l'oeil.

`Respiration` existe depuis une revue des clips publiés qui disait : « elles sont parfois un peu
rapides et ne permettent pas forcément de comprendre ce que l'on voit ». Elle ne s'arrête que si
`Seance.filmee()`, et `Seance.filmee()` ne lisait que `recette.reperes` : la propriété du banc
**bash**, posée par le seul profil `recette-filmee`. Le banc Java pose `recette.film`, et n'a aucun
repère à consigner puisqu'il écrit un fichier par test.

Les neuf clips du premier tournage n'ont donc respiré nulle part. Plan utile, hors les 2,0 s de
carton, compté en images sur **les deux tournages Windows** pour que les deux colonnes viennent du
même banc :

| Cas | Sans respiration | Avec |
|---|---:|---:|
| S6-28 · rejouer une vue amputée | **0,7 s** | 4,8 s |
| S1-26 · la modale s'ouvre | 1,4 s | 7,2 s |
| S6-29 · tout effacer | 1,8 s | 7,7 s |
| S6-26 · rouvrir une liste | 4,0 s | 15,5 s |
| S6-27 · une valeur devenue impossible | 4,1 s | 15,6 s |
| **les neuf** | **22,4 s** | **89,8 s** |

Sept images pour un cas qu'un humain doit trancher. `S6-28` demande de voir **paraître** un
bandeau : sept images ne montrent pas une apparition, elles montrent deux états.

Ce qu'il faut retenir dépasse le réglage. Rien ne rougissait, et rien ne **pouvait** rougir : les
clips existaient, les neuf tests passaient, l'index les comptait tous les neuf, et le garde du job
constatait qu'une fenêtre avait paru dans chacun. Tous les signaux disponibles disaient oui. Le seul
endroit où ce défaut se voyait, c'est l'oeil de qui regarde, et c'est précisément la raison d'être
d'un cas perceptif.

`SeanceTest` reprend ce que l'oeil a vu : neutraliser la reconnaissance de `recette.film` fait rougir
un cas sur quatre, et c'est le bon.

### 2. Un menu s'ouvrait au milieu de l'écran

C'est le défaut qui comptait, et il a été trouvé **avant** d'aller sous Windows, sur le tournage
Linux de contrôle.

`CameraDeScene` compose toutes les fenêtres visibles et les **centre** sur la toile. Centrer est le
bon geste pour la fenêtre principale : sous Monocle, `Window.getX()` situe la fenêtre sur un écran
virtuel étranger à la toile, et lire cette coordonnée avait déjà coûté un bord amputé de cinquante
pixels.

Mais un menu, une infobulle, la liste d'un `ComboBox` ne sont pas des nœuds de la scène : ce sont
des `PopupWindow` à part entière, que `Window.getWindows()` rend au même titre que la fenêtre
principale. Les centrer revient à les **détacher du bouton qui les ouvre**.

Mesuré au pixel sur le clip de `S6-27` : le menu du bouton « + Filtre » était dessiné de x = 582 à
x = 697. Son centre tombe à 639,5, c'est-à-dire le centre exact d'une toile de 1280 pixels, et c'est
la preuve qu'il était centré et non placé. Après correctif, il occupe x = 492 à x = 605, bord gauche
aligné sur celui de son bouton : **90 pixels plus à gauche**, et sous le bouton au lieu de flotter
sur la table.

Ce défaut est grave pour cette page-ci en particulier. `S6-26` et `S6-27` demandent à un humain de
juger **une liste de valeurs qui s'ouvre**. Une liste posée ailleurs qu'à sa place se lit comme un
défaut du produit : le banc aurait fait rougir un cas que rien ne rendait rouge.

Le correctif ne lit toujours aucune coordonnée absolue. Il lit une **différence** :

```java
Window proprietaire = proprietaireDe(fenetre);   // PopupWindow::getOwnerWindow
x = decalage(largeur, (int) proprietaire.getScene().getWidth())
        + (int) Math.round(fenetre.getX() - proprietaire.getX());
```

L'absolu ment, le relatif non : `fenetre.getX() - proprietaire.getX()` est le même vecteur dans
n'importe quel repère, y compris l'écran virtuel de Monocle. Après correctif, le menu se pose sous
son bouton.

### 3. L'index par cas perd des lignes dès qu'il y a plusieurs forks

Le banc bash imposait une seule JVM parce qu'il n'y a qu'un écran X. Le banc Java n'a pas cette
contrainte : filmer le graphe de scène rend les forks parallèles parfaitement légitimes, et c'est
un gain, pas un détail.

`IndexDesCas` ne suit pas. Chaque JVM tient son propre index et écrit le **même** fichier en fin de
session : la dernière qui finit efface les autres. Mesuré en local avec `forkCount=1C`, quatre
forks : l'index final portait **5 lignes sur 9**, et rien dans la page ne disait qu'il en manquait
quatre.

Un index amputé se lit exactement comme un index complet. C'est le défaut qu'il faut corriger avant
d'ouvrir les forks : un fragment par JVM, fusionné en fin de tournage.

Ce tournage-ci emploie donc `forkCount=1`, ce qui rend l'index entier sans masquer le défaut.

### 4. La console Windows n'écrit pas les accents du banc

Le banc annonce chaque clip par `film : <nom> · <n> image(s), une fenêtre a paru`. Dans le journal
du runner Windows, cette ligne paraît `film : <nom> ? <n> image(s), une fen?tre a paru` : la sortie
standard de la JVM n'y est pas en UTF-8.

Sans conséquence sur les clips, mais décisive pour tout garde qui LIT ce journal. Le garde de ce job
compte les clips muets en cherchant `aucune`, en ASCII pur. Cherché sous sa forme accentuée, le
motif n'aurait jamais correspondu, le compteur serait resté à zéro, et un tournage dont tous les
clips s'arrêtent à leur carton se serait présenté comme un tournage sans reproche.

### 5. Ce que le banc Java retire du dispositif Windows

| Le banc bash exige | Le banc Java exige |
|---|---|
| Xvfb, openbox, xdotool, `x11-utils` | rien |
| l'absence de `WAYLAND_DISPLAY` | rien |
| `glass.platform=gtk`, `testfx.robot=awt`, `java.awt.headless=false` | rien : le mode headless du dépôt |
| un remuxage `mkv` vers `mp4` pour qu'un navigateur lise | rien : le banc écrit du `mp4` |
| une seule JVM, imposée par l'écran unique | rien, une fois l'index corrigé |
| un seuil de luminance recalibré à chaque gestionnaire de fenêtres | rien : `Window.getWindows()` |

Reste `ffmpeg`, et c'est tout. `Encodeur` est l'interface où le remplacer si l'on veut un jour
encoder en Java.

## Les limites, assumées

- **Le curseur n'est pas rendu.** Un clip montre l'effet d'un clic, jamais le clic.
- **Les boîtes natives `FileChooser` restent invisibles.** Sans conséquence : TestFX ne sait pas les
  piloter non plus, donc elles sont déjà derrière un port.
- **Le rendu passe par Prism SW.** Quelques effets et mélanges diffèrent à la marge du rendu
  matériel. Sans conséquence pour un film de recette ; à vérifier si un cas perceptif juge un jour
  au pixel près.

## Ce qui reste à décider

Ce spike ne décide de rien. Il rend une réponse et deux défauts nommés. Ce qu'il rend possible, si
la réponse convient :

- brancher les clips de la session **S10**, qui n'en a jamais eu ;
- corriger `IndexDesCas` pour ouvrir les forks parallèles, et regagner le temps que le banc bash
  paie pour un écran unique ;
- décider ce que devient `lance-test-filme.sh`, ses 113 Kio et ses cinq préconditions.

Le spike qui l'a précédé, et qui dit ce que le banc Java remplace :
[Filmer la recette depuis le graphe de scène](film-depuis-le-graphe-de-scene.md).
