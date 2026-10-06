# Comparer deux tournages

Depuis [#4258](https://github.com/echonuit/vigiechiro-pr-companion/issues/4258), chaque version porte
les clips de ses deux bancs sur son tag. Savoir ce qui a bougé entre la dernière version et le
tournage courant demandait d'ouvrir cinquante lecteurs et de s'en souvenir. Le flux **comparer deux
tournages** fait le tri ; le regard fait le reste.

## Lancer une comparaison

Le flux `comparer-tournages.yml` (`workflow_dispatch`) prend quatre entrées :

| Entrée | Ce qu'elle attend |
|---|---|
| `avant` | ce à quoi on compare : un tag de version (`v2.188.0`), `clips-recette`, `clips-plateforme-de-test-precedent`, ou un numéro d'exécution de `tournage-recette.yml` |
| `apres` | ce qu'on regarde, mêmes valeurs |
| `banc` | `bash` ou `java`, pour choisir le préfixe sur les tags de version. Sans effet sur une exécution |
| `tolerance` | la tolérance de couleur, en pourcentage. 5 par défaut |

L'usage courant est `avant` = la dernière version, `apres` = `clips-recette`, ce qui montre **ce que le
travail non publié change à l'écran**.

Le résultat s'écrit dans le **résumé du job** et les images partent dans un artefact, gardé quatorze
jours.

Les clips de la **plateforme de test** se comparent d'un tournage au suivant : `avant` =
`clips-plateforme-de-test-precedent`, `apres` = `clips-plateforme-de-test` (#5854). Chaque tournage
sur cette cible recopie la pré-version sous le premier nom avant de l'écraser, et les notes des deux
disent l'exécution et le commit qui les ont tournées : c'est là qu'on lit ce qu'on compare. Deux
tournages du même commit mesurent du bruit ; deux commits différents, ce que le second a changé.

### Comparer une branche à `main` avant fusion

Une valeur purement numérique est un **numéro d'exécution** de `tournage-recette.yml`
([ADR 5930](../decisions/5930-une-comparaison-reprend-une-execution-avec-les-refus-de-la-mesure.md)).
On tourne `main`, on tourne la branche, et on donne les deux numéros :

```bash
gh workflow run tournage-recette.yml --ref main -f session=toutes -f plateforme=ubuntu
gh workflow run tournage-recette.yml --ref <branche> -f session=toutes -f plateforme=ubuntu
gh workflow run comparer-tournages.yml --ref <branche> -f avant=<exécution de main> -f apres=<exécution de la branche>
```

L'atelier se lance depuis la branche tant qu'elle n'est pas fusionnée : c'est son fichier de planchers
qui est lu, et il peut porter une ligne que `main` n'a pas encore. Les planchers sont ceux du dépôt,
lus avec l'instrument du flux : plus besoin de conteneur sur un poste.

Le résumé nomme les deux côtés avant l'index : le numéro, le commit et la branche de chaque exécution.

Une exécution n'est reprise que si elle a conclu en succès et porte un artefact de clips, et un seul.
Ce sont les refus de `mesurer-les-planchers.yml`, moins celui du même commit : deux commits sont
l'objet d'une comparaison. Un artefact est gardé **quatorze jours**. Passé ce délai, l'atelier le dit,
et le geste est de relancer un tournage du commit voulu.

Une paire mixte est permise, une version d'un côté et une exécution de l'autre. Deux exécutions de
populations différentes, l'une ordinaire et l'autre de la plateforme de test, n'ont en revanche aucun
cas commun : tout sort « apparu » ou « disparu », et rien n'est comparé.

**Une comparaison sans aucun cas commun échoue**, et le dit (#5934). L'outil sort en 1, avec « Aucun
cas commun aux deux tournages : rien n'a été comparé ». Il écrit quand même l'index, un avertissement
en tête, et l'atelier le verse au résumé avant de rendre ce code : la liste des cas apparus et
disparus est tout ce qu'il reste à lire, et c'est elle qu'on veut le jour où tous les clips ont été
renommés. Un seul cas commun suffit à en refaire une comparaison, qui sort en 0.

!!! note "Pourquoi rien n'est committé"

    La comparaison « dernière version contre tournante » change dès que l'un des deux bouge. Une page
    committée serait périmée au prochain tournage manuel, et une page périmée sur un sujet visuel est
    pire qu'une page absente : on la croit.

## Ce qu'on obtient, par cas

Trois signaux, du plus fiable au moins fiable.

**La présence.** Le cas est dans les deux tournages, ou **apparu**, ou **disparu**. C'est le signal le
plus sûr et le moins cher, et c'est souvent celui qui compte.

**L'image finale.** Les deux dernières images accolées (`<cas>.avant-apres.png`), leur carte des
différences (`<cas>.ou.png`, rouge là où ça bouge), et la part de pixels changés.

**La durée.** Un scénario qui s'allonge a presque toujours changé.

## Comment lire le chiffre

**Il trie, il ne prouve pas.** Mesuré sur deux tournages du **même commit** :

| changement | part mesurée | rapport au plancher |
|---|---|---|
| rien (deux tournages identiques) | ≤ 0,01 % | le plancher |
| un chiffre (12 × 18 px) | 0,021 % | ×2 |
| un mot (60 × 18 px) | 0,101 % | ×10 |
| un libellé (220 × 18 px) | 0,364 % | ×36 |
| un encart (400 × 120 px) | 4,212 % | ×420 |

Un mot changé vaut dix fois le plancher : le chiffre le sort du lot. Un caractère changé n'en vaut que
deux : le chiffre ne le distingue plus, et c'est la **carte des différences** qui le localise.

## Pourquoi une tolérance de couleur

Sans elle, le plancher n'est pas de 0,01 % mais de **16 %**.

Deux tournages du même commit rendent le même écran avec un **anticrénelage** légèrement différent. La
carte des différences le montre sans ambiguïté : le rouge est sur le texte et sur les bordures, jamais
sur les aplats. Un décalage de mise en page a été soupçonné, puis **écarté par la mesure** - décaler
l'image aggrave l'écart au lieu de le réduire.

Une tolérance de 5 % absorbe cet anticrénelage sans rendre l'instrument aveugle, comme le tableau
ci-dessus le montre.

!!! warning "La tolérance se mesure, elle ne se fige pas"

    `compare_tournages.py --plancher <A> <B>` remesure le plancher sur place, en comparant deux
    tournages qu'on sait identiques.

## Le plancher du runner, mesuré

**Six** tournages des 95 clips ordinaires, sur le **même commit** (`f9c9e1218`), lancés sur six
runners GitHub distincts, soit **quinze paires** par clip. Mesurés le 5 octobre 2026 par l'atelier
`mesurer-les-planchers.yml`, avec l'instrument du flux, ffmpeg 6.1.1 et ImageMagick 6.9.12-98
(#5885).

**Le fichier s'est complété depuis, et les paires ne sont plus uniformes.** Chaque mesure ajoute les
siennes à tous les clips des tournages qu'on lui donne (#5956, #5893), et un clip sorti de la table
des clips sans plancher en porte moins que les autres, sa ligne étant neuve. La quatrième colonne de
`planchers-tournages.tsv` dit,
pour chaque clip, sur combien de paires son plancher a été pris - et un plancher tiré de peu de
paires en prouve d'autant moins. Le décompte des lignes par nombre de paires ne s'écrit pas ici :
chaque mesure le déplace, et le fichier le dit mieux que cette page.

Le fichier mêle deux populations, et chaque compte de cette page nomme la sienne. Les clips
**ordinaires** qui ont un plancher sont <!--inv:clips-ordinaires-a-plancher-->95<!--/inv-->. Ceux de
la **plateforme de test** sont <!--inv:clips-de-la-plateforme-de-test-a-plancher-->5<!--/inv-->, et
ont leur propre section. Les clips **sans plancher**, ceux qui ont deux fins, sont au nombre de
<!--inv:clips-sans-plancher-->0<!--/inv--> (voir plus bas). Ces trois comptes sont relus à chaque
demande contre l'outil et son fichier : un clip corrigé qui quitte la table fait rougir cette page
tant qu'elle ne le dit pas. Un cas est de la plateforme de test quand sa méthode, ou sa classe, en
porte le tag : ni le nom de sa classe ni le tag de sa seule classe ne suffisent à le dire.

La distribution des planchers des clips ordinaires, **relevée le 6 octobre 2026** sur le fichier de
`main` à `358205ef5`, qui en portait alors 89. C'est une mesure datée : elle ne suit pas le fichier,
et se relève quand l'argument qu'elle sert en a besoin.

| plancher à 5 % de tolérance | cas |
|---|---|
| > 1 % | 1 |
| 0,5 à 1 % | 9 |
| 0,1 à 0,5 % | 50 |
| 0,05 à 0,1 % | 8 |
| < 0,05 % | 21, dont 5 à zéro |

**Médiane : 0,167 %.** Le pire vaut 1,033 %, sur
`ScenarioJournalAbsentTest.sans_journal_la_nuit_est_inconnue`.

!!! danger "Un seuil global mentirait dans les deux sens"

    Retenir le pire plancher, 1,033 %, comme seuil unique **aveuglerait 79 cas pour se protéger de
    dix** : un libellé entier changé, qui vaut 0,364 %, passerait sous le seuil sans être vu.

    Retenir la médiane laisserait au contraire la moitié des cas crier au changement à chaque
    tournage.

    Un écart se lit donc contre **le plancher de son propre cas**, pas contre un seuil unique.

!!! warning "Quelques tournages voient un mode qui sort une fois sur quatre, pas une fois sur vingt"

    Un cas dont le plancher est ressorti à 0,000 % sur quinze paires n'est pas prouvé stable : il
    l'était ces six fois-là. Quatre clips n'ont montré leur seconde fin que dans **un** tournage sur
    les quatre premiers (#5911), et la paire unique qui avait précédé cette mesure ne l'avait pas vue.

!!! warning "Le contrôle hors échantillon, et ce que les paires y changent"

    Le plancher d'un cas est le pire de ses propres paires : les tournages qui l'ont mesuré restent
    dessous par construction. Le contrôle se fait donc sur deux **autres** tournages du même commit,
    comparés contre le fichier.

    | planchers pris sur | dans leur plancher | de 1 à 2 fois | au-delà du double |
    |---|---|---|---|
    | six paires (quatre tournages) | 66 | 14 | 8 |
    | quinze paires (six tournages) | 81 | 4 | 3 |

    Les trois qui restent valent 0,015 %, 0,195 % et 0,300 % d'écart. Le premier s'affiche « ×15 »
    parce que son plancher vaut 0,001 % : un rapport élevé sur un plancher proche de zéro se lit
    avec son écart absolu, et la carte des différences tranche.

    Ce reste n'est pas un défaut de ces clips. Un maximum pris sur quinze valeurs est dépassé par
    une seizième de temps en temps, et chaque passage de l'atelier ajoute des paires.

!!! note "Ce que valaient les planchers d'avant"

    Le fichier portait 51 planchers ordinaires, médiane **0,009 %**, pris sur un poste. Remesurés
    avec l'instrument du flux, 43 des 50 clips communs montent, et la médiane est dix-sept fois plus
    haute. Deux tournages du même commit, comparés par le flux contre ces planchers, sortaient 32
    clips sur 51 à plus du double de leur plancher.

### Les clips de la plateforme de test, mesurés

L'[ADR 5641](../decisions/5641-les-tests-connectes-ont-deux-cibles.md) ne levait le refus de comparer
les clips connectés, pour la plateforme de test, que si la mesure le permettait. Elle a été prise le
5 octobre 2026 : **six** tournages du commit `82f90b2a6`, sur six runners, soit quinze paires,
mesurées avec l'instrument du flux (#5870).

| clip | cas | son plancher, sur quinze paires |
|---|---|---|
| `ScenarioConnectePublicationTest` | S4-90, S4-92 | 0,005 % |
| `ScenarioConnecteConnexionTest` | S8-01, S8-05, S8-06 | 0,134 % |
| `ScenarioConnecteActualisationTest` | S4-98 | 0,302 % |
| `ScenarioConnecteAnnonceImportTest` | S2-59, S2-60 | 0,325 % |
| `ScenarioConnecteLancementTest` | S4-47 | 0,503 % |

Aucun ne dépasse le pire plancher des clips ordinaires, 1,033 % au relevé du 6 octobre 2026 : ces
clips se comparent.

!!! danger "Un plancher se mesure avec l'instrument du flux, pas avec celui du poste"

    Le même script, sur la même paire de clips, ne rend pas le même chiffre partout. Mesuré sur une
    paire de ces tournages : le clip de la connexion sort à **0,020 %** sur un poste (ffmpeg 8,
    ImageMagick 7) et à **0,134 %** sur le runner de `comparer-tournages.yml` (ffmpeg 6.1.1,
    ImageMagick 6.9.12) ; celui de l'import à 0,040 % et 0,325 %.

    Les premiers planchers de ces clips (#5797) avaient été pris sur un poste. Le flux les lisait
    ensuite avec ses propres outils, et annonçait quatre clips sur cinq « au-dessus de leur plancher »
    pour deux tournages du même commit. Une partie de ce qu'on a d'abord pris pour un défaut des clips
    était cet écart d'instrument.

    Les planchers se mesurent donc par l'atelier `mesurer-les-planchers.yml`, qui joue avec
    l'instrument du flux, et l'outil refuse depuis de lire un plancher pris par un autre (#5885,
    voir « Le fichier de planchers »).

!!! danger "Peu de paires ne voient pas le second mode d'un clip"

    La première comparaison lancée par le flux (#5854) a rendu le clip de `S4-47` à **20,044 %**
    entre deux tournages du même commit. Le contenu était le même, la page n'était pas défilée au même
    endroit : le scénario amenait sa carte dans le cadre avant qu'elle ait pris sa hauteur finale, et
    JavaFX garde alors le décalage en pixels. Les quatre tournages de la première mesure étaient
    tombés du même côté.

    Un plancher bas ne prouve donc pas qu'un clip n'a qu'un mode. Ce qui l'établit est la **cause**,
    reproduite par un banc (`GesteVisibleBasDePageTest`), et son remède : un clip dont le verdict est
    le dernier élément de sa page finit par `GesteVisible.allerAuBasDeLaPage` (#5870). Les quinze
    paires ci-dessus ne montrent plus ce mode.

    Un verdict qui est ailleurs dans sa page se **pose** par `GesteVisible.poserDansLeCadre`, et
    l'assertion de fin relit cette position : « dans le cadre » est vrai à plusieurs endroits de la
    page, et un clip y a gagné une seconde fin que six tournages n'avaient pas montrée (#6069).

!!! note "Une dernière image qui change tout"

    Avant correction, le clip de l'import s'arrêtait pendant la transformation, à un endroit de la
    page différent à chaque tournage : **19,275 %** d'écart entre deux tournages du même commit, sur
    un poste. Une fois son compte rendu amené dans le cadre, 0,040 % avec le même instrument. Un
    plancher aberrant désigne d'abord un clip qui ne finit pas sur ce qu'il doit montrer.

### Ce que le plancher par cas a corrigé, à son introduction

L'exemple date de #4287, et ses planchers sont ceux de l'époque, pris sur un poste : ils ne sont plus
dans le fichier. Le raisonnement, lui, tient toujours.

Sur une comparaison réelle, deux cas dépassaient 1 % et semblaient donc être les vrais changements.
Rapportés à leur propre plancher, ils se séparent :

- `les_boutons_disent_ce_qui_les_empeche` : 1,561 % pour un plancher de 0,101 %, soit **quinze fois**
  son bruit. Et l'image le confirme - une infobulle « Suppression impossible : ce site porte des
  passages » est apparue, ce qui correspond au correctif #4253.
- `chaque_carte_ouvre_ce_qu_elle_annonce` : 1,799 % pour un plancher de 0,809 %, soit **deux fois**
  seulement. Sa carte des différences ne montre que du rouge sur le texte et les bordures, jamais un
  changement localisé : c'est du bruit de rendu, et le chiffre brut le faisait passer pour un
  changement.

## Le fichier de planchers

Les planchers mesurés vivent dans `.github/assets/planchers-tournages.tsv`. Son en-tête dit par quel
instrument ils ont été pris, puis vient une ligne par cas :

```
# Instrument : ffmpeg 6.1.1 · ImageMagick 6.9.12-98
ScenarioAccueilTest.chaque_carte_ouvre_ce_qu_elle_annonce	0.000	0.412	6
```

Le cas, le plancher de sa **première** image, celui de sa **dernière**, et **le nombre de paires de
tournages qui les ont produits**.

Le flux de comparaison le passe automatiquement, et chaque cas est alors classé par son **rapport à
son propre bruit** plutôt que par son écart absolu. Le résumé compte les cas « au-dessus de leur
propre plancher », qui est le nombre à regarder.

### Un plancher appartient à l'instrument qui l'a pris

L'outil lit sa propre version de ffmpeg et d'ImageMagick et la confronte à celle de l'en-tête, dans
les deux sens (#5885) :

- il **refuse de comparer** contre des planchers pris par un autre instrument ;
- il **refuse de compléter** un fichier pris par un autre instrument.

Le refus nomme les deux instruments. Il n'y a pas d'avertissement à la place : le même clip rend
0,020 % sur un poste et 0,134 % sur le runner, et un index classé contre le mauvais sol a l'air aussi
juste que l'autre.

C'est la version amont qui compte (`6.1.1`), pas la révision du paquet (`6.1.1-3ubuntu5`) : un
correctif de sécurité reporté par la distribution ne fait pas remesurer cent clips.

!!! warning "Sur un poste, la comparaison avec planchers est donc refusée"

    C'est voulu. Pour regarder une paire chez soi, comparer **sans** fichier de planchers : les écarts
    sortent en valeur absolue, et ne se lisent contre aucun sol. Pour les lire contre leurs planchers,
    donner les deux numéros d'exécution à `comparer-tournages.yml` : c'est la section « Comparer une
    branche à `main` avant fusion », plus haut.

### Le mesurer : l'atelier `mesurer-les-planchers.yml`

Les planchers se mesurent par un atelier, qui joue sur la même image de runner et installe les mêmes
paquets que la comparaison. Il ne tourne rien : il reçoit des **numéros d'exécution** de
`tournage-recette.yml`.

| Entrée | Ce qu'elle attend |
|---|---|
| `executions` | au moins deux tournages **du même commit**, séparés par des espaces |
| `temoins` | facultatif : deux autres tournages du même commit, **hors** de la mesure |
| `repartir_de_zero` | oublier le fichier du dépôt au lieu de le compléter |

Toutes les paires sont jouées : quatre tournages en font six. Le **pire** plancher observé est gardé,
et le compte de paires s'ajoute à celui du fichier.

L'atelier **n'écrit rien sur le dépôt**. Il rend le fichier dans un artefact, et une demande le
committe : un plancher qui monte rend la comparaison moins sensible, et cela se relit.

**Il refuse des tournages de commits différents.** Un plancher est le bruit entre deux tournages
identiques. Pris entre deux commits, il rangerait un changement du produit parmi le bruit, et la
comparaison ne verrait plus jamais ce changement.

**Les témoins contrôlent hors échantillon.** Le plancher d'un cas est le pire de ses propres paires :
un tournage qui a servi à le mesurer reste dessous par construction. Les deux témoins n'y ont pas
servi, et l'atelier écrit leur comparaison dans son résumé. Un cas loin au-dessus de son plancher y
dit que ce plancher est pris sur trop peu de paires, pas que le produit a changé.

Trois usages, et le geste de chacun :

| Ce qu'on veut | Le geste |
|---|---|
| ajouter des paires | lancer l'atelier sur `main`, avec de nouveaux tournages |
| remesurer un clip dont l'écran a changé | retirer sa ligne sur une branche, lancer l'atelier **sur cette branche** |
| l'image du runner a changé de version | `repartir_de_zero`, une fois par famille de tournages |

Les clips ordinaires et ceux de la plateforme de test ne sortent pas des mêmes tournages : ce sont
deux lancements, le second complétant le fichier rendu par le premier.

!!! warning "Le pire, et non la moyenne"

    Un plancher qui sous-estime le bruit fabrique des faux positifs, c'est-à-dire exactement ce qu'on
    cherche à éviter. Mieux vaut rater un petit changement sur un cas instable que crier au changement
    à chaque tournage.

!!! note "Mesurer ailleurs que dans l'atelier"

    `compare_tournages.py --planchers <fichier> <A> <B> [<C> ...]` fait la même mesure, et écrit
    l'instrument de la machine qui la joue. Un conteneur `ubuntu:24.04` avec les paquets `ffmpeg` et
    `imagemagick` de la distribution rend les chiffres du runner à la troisième décimale : c'est ce
    qui a rempli le fichier avant que l'atelier existe. Sur un poste, le fichier obtenu porte
    l'instrument du poste, et le flux refusera de le lire.

### Les clips auxquels on refuse un plancher

Un plancher mesure le bruit d'un clip qui **finit sur son verdict**. Un clip peut avoir deux fins :
deux tournages du même commit y diffèrent alors de 3 à 26 %, parce qu'il s'arrête pendant un import,
pendant un fondu, ou sur une page que rien n'a posée. Lui écrire ce chiffre rendrait la comparaison
aveugle, sur lui, à tout changement plus petit.

Un tel clip est nommé dans l'outil (`SANS_PLANCHER`), avec son issue. La mesure affiche son écart
sans l'écrire, et la comparaison l'annonce « sans plancher », suivi du numéro. Sa ligne s'en retire
avec l'issue qui la porte. Un test confronte cette section à la table dans les deux sens : un clip
qui y entre doit paraître ici dans un tableau, son nom entre accents graves et son issue en dernière
colonne.

**La table est vide.** Les sept clips que
l'[ADR 5911](../decisions/5911-un-clip-a-deux-fins-n-a-pas-de-plancher.md) y avait rangés en sont
sortis, une fois leur verdict tenu à l'image (#5952, #5893, #5911).

**L'un d'eux y est revenu, puis en est ressorti** (#6069). Son plancher avait été pris sur six
tournages qui finissaient tous de la même façon ; les six suivants ont montré sa seconde fin deux
fois. Un mode qui sort deux fois sur douze ne se voit pas à coup sûr sur six tournages, et un
plancher mesuré sans lui est faux sans que rien ne le dise. Sa seconde sortie s'est jugée sur douze.

Le critère n'est pas un seuil : c'est la **dernière image**, regardée. Un clip y entre quand deux
tournages du même commit ne montrent pas le même écran, pas quand son chiffre est haut.

### Quatre silences que le fichier ne produit pas

Un cas **absent** du fichier est annoncé « plancher inconnu ». Le prendre pour stable reviendrait à
inventer une mesure qui n'a pas été faite.

Un cas auquel on **refuse** un plancher est annoncé « sans plancher », avec son issue : ce n'est pas
le même silence, et les deux ne se réparent pas au même endroit.

Un fichier **annoncé mais introuvable** fait échouer la comparaison. Sans ce refus, les cinquante cas
diraient tous « plancher inconnu » et personne n'irait chercher le chemin fautif.

Un fichier **pris par un autre instrument**, ou qui ne dit pas le sien, fait échouer la comparaison
elle aussi.

## Les deux bouts, et ce que la seconde paire a appris

Depuis l'[ADR 4296](../decisions/4296-on-compare-les-deux-bouts-du-clip.md), la comparaison porte sur la
**première** image du clip autant que sur la dernière. Chacune a son plancher, et le classement retient
le plus grand des deux rapports.

La première image est **plus stable que la dernière** : sur 95 clips et quinze paires de tournages, soit
1 425 mesures avec l'instrument du flux, son plancher vaut **0,000 % sans exception** (#5885). Elle
n'est pas pour autant aveugle - les premières images de deux cas différents diffèrent de 2,4 à 3 %.

!!! warning "Un plancher haut peut n'être qu'un mauvais tirage, ou un clip à deux fins"

    Avec deux paires, prises sur un poste, `chaque_carte_ouvre_ce_qu_elle_annonce` avait rendu
    **0,809 %** puis **0,073 %**, onze fois moins. On en avait conclu que ce plancher n'était pas une
    propriété du cas, mais un mauvais tirage.

    La règle du **pire observé** le gardait pourtant, et c'est son prix assumé : un plancher ne
    redescend jamais, donc **un seul mauvais tirage aveugle un cas pour de bon**. Avec assez de
    paires, un centile vaudrait mieux qu'un maximum (#4309).

    Six paires, avec l'instrument du flux, ont dit ce que deux ne pouvaient pas : ce clip a un fond de
    1,6 à 2,2 % et une seconde fin à **26 %**, prise pendant un fondu, un tournage sur quatre (#5911).
    Ce n'était ni un tirage ni du bruit. Il n'a plus de plancher tant qu'il ne finit pas sur une
    image posée.

### Les images du début ne sortent que si le début a bougé

Son plancher valant zéro partout, produire ces montages systématiquement ferait cinquante fichiers
identiques qui noieraient les deux ou trois qui comptent. Vérifié sur une comparaison réelle : 51
montages de fin, **zéro** montage de début.

## Ce que la méthode ne voit pas

**L'image finale compare la destination, pas le chemin.**

La dernière image est le seul instant où deux tournages sont comparables sans dépendre de leur cadence :
au milieu, l'image n°40 de l'un et l'image n°40 de l'autre montrent deux moments différents. Mais un cas
dont l'objet est une **transition** - « la modale s'ouvre sans saut » - garderait une fin identique
alors que son milieu aurait bougé.

La durée est le seul garde-fou bon marché contre cela, et il est grossier. C'est une limite assumée, pas
un oubli.

## Une mesure impossible n'est pas « rien n'a changé »

Deux dossiers vides font **échouer** la comparaison au lieu de rendre « aucun cas ne bouge », et une
mesure qui échoue se compte à part dans le résumé.

Et un **outil absent** est une panne d'installation, pas une mesure : le script refuse de commencer
et nomme ce qui manque, au lieu de rendre cinquante « ? » qui se liraient comme cinquante cas stables.
Les deux ne se réparent pas au même endroit, donc ils ne doivent pas se lire pareil.

La mesure des planchers a le même refus depuis #5847. Un dossier absent ou vide, ou des tournages
qui n'ont aucun clip en commun, la font échouer en nommant ce qui manque, et le fichier n'est pas
touché. Avant cela, deux chemins faux rendaient « 0 cas », « plancher le plus haut : 0 % » et un code
de sortie nul, ce qui se lit comme un excellent résultat.

Ce n'est pas de la prudence de principe : le premier jet de cet outil rendait « ? » sur les sept cas
d'un vrai tournage, et son index annonçait tranquillement « aucun cas ne bouge ». La cause était que
`identify` écrit `1.152e+06` pour une toile de 1280 × 900, et le test d'entier qui suivait refusait la
mesure. Un instrument cassé qui se présente en succès est pire que pas d'instrument
([ADR 2748](../decisions/2748-un-dispositif-qui-peut-ne-rien-verifier-le-dit.md)).

Le premier lancement réel l'a montré deux fois. Le workflow n'installait pas ImageMagick : `compare`
était introuvable, la part de pixels valait « ? », et les cinquante cas d'un vrai tournage se rangeaient
en « mesure impossible » - **le job restant vert**. Le compteur de mesures impossibles a dit la panne ;
sans lui, le résumé aurait annoncé « aucun cas ne bouge », et la chaîne aurait eu l'air éprouvée.
