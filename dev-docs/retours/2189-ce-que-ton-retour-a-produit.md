# Ce que ton retour de la 2.189.0 a produit

> Planche de restitution pour Samuel, après son retour de Corse du 27 août 2026 et sa session du 29
> sur la **2.189.0**. Écrite le 3 septembre, contre la **2.192.0**.
>
> Les clips viennent de bancs qui rejouent le geste à chaque version : ils ne sont pas des
> enregistrements d'écran faits une fois, mais ce que le produit fait aujourd'hui.
>
> Elle dit trois choses, et les sépare : ce qui est **corrigé**, ce qui **ne l'est pas encore**, et ce
> qui **ne viendra pas**. Un point tu, ou promis sans l'être, se paierait à ta première nuit.

> Les captures sont celles du dépôt, régénérées à chaque version : ce sont les écrans d'aujourd'hui,
> pas des copies d'écran prises une fois.

## Comment lire les légendes

Chaque entrée dit ce que l'artefact **montre** et ce qu'il **ne montre pas**. Ce n'est pas une
précaution de style : deux fois pendant ce chantier, une image produite a été prise pour une image qui
prouve - une capture périmée qui semblait à jour, un clip qui aurait laissé croire qu'on voit une
infobulle s'ouvrir.

---

## Ce qui est corrigé

### 1 · L'import part de la racine de ta carte SD

Tu écrivais « un message d'erreur à la racine de ma carte SD ». Le journal d'application en portait la
trace exacte : une `NullPointerException` sur un chemin de racine, que le garde testait mal.

**Corrigé.** Et plus largement que prévu : l'audit a trouvé le même défaut sur **trois** écrans, dont
un qui cassait devant une confirmation de suppression.

*Pas d'image : c'est un plantage qui n'arrive plus, et une absence ne se photographie pas.*

### 2 · Ton appui sur une touche n'est plus une anomalie

Tu es venu regarder l'écran de l'enregistreur pendant la nuit. Le firmware écrit alors
`Wakeup by PINPUSH`, et Companion te le reprochait : « réveil non programmé », dans les anomalies. Il
en fabriquait même une nuit de plus, et marquait la précédente tronquée.

**Corrigé.** Le réveil par touche est reconnu comme **voulu**.

<video controls width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioReveilParBoutonTest.un_reveil_par_bouton_n_est_pas_une_anomalie.mp4"></video>

**Ce que le clip montre** : ton appui **paraît** au journal des évènements, qui relate, et **ne paraît
pas** aux anomalies, qui accusent. C'est la paire qui compte - une liste d'anomalies vide, seule,
prouverait aussi bien que le journal n'a pas été lu.
**Ce qu'il ne montre pas** : le firmware lui-même, ni les trois autres motifs de réveil.

### 3 · L'alerte horaire dit le protocole, et non son contraire

Elle prévenait quand le protocole était **respecté**, et se taisait quand il était violé. Exactement à
l'envers.

**Corrigé**, et la règle est écrite : le protocole est un **plancher**. Enregistrer plus que la fenêtre
exigée n'est pas un défaut.

Les deux images ci-dessous se lisent **ensemble** : c'est leur écart qui montre la correction, et
chacune seule ne prouverait rien.

![Le protocole n'est pas couvert : un avertissement](https://raw.githubusercontent.com/echonuit/vigiechiro-pr-companion/main/.github/assets/apercu-diagnostic-protocole-non-couvert.png)

**Ce que la première montre** : l'avertissement se déclenche quand l'enregistrement **ne couvre pas**
la fenêtre demandée - de 30 minutes avant le coucher à 30 minutes après le lever.

![Le protocole est couvert et dépassé : une information](https://raw.githubusercontent.com/echonuit/vigiechiro-pr-companion/main/.github/assets/apercu-diagnostic.png)

**Ce que la seconde montre** : quand l'enregistrement couvre la fenêtre **et la dépasse**, l'écran rend
une simple **information**, en bleu. C'est là que se lit le retournement : dépasser le protocole
n'est pas un défaut, et ne se signale plus comme tel.
**Ce que ni l'une ni l'autre ne montre** : ce que tu voyais avant. Les deux images sont d'aujourd'hui ;
l'ancienne alerte n'a pas été photographiée.

### 4 · Le dépôt manuel ouvre ton dossier, pas un navigateur

Quand le dépôt échouait, Companion annonçait ouvrir le dossier des archives et ouvrait une **page web**
listant tes ZIP. Tu as vu une page sans savoir qu'en faire.

**Corrigé.** Le repli ouvre le gestionnaire de fichiers.

*Pas d'image, et il ne peut pas y en avoir : ce qui a changé se passe **hors** de l'application.*

### 5 · Une nuit sans preuve ne se dit plus « complète »

Ta carte tournait plusieurs nuits. Le journal du capteur est **circulaire** : il perd les plus
anciennes. Companion présentait pourtant les trois nuits comme complètes - l'absence de preuve était
lue comme une preuve, et le badge le plus rassurant allait à la nuit dont on savait le moins.

**Corrigé.** Trois états désormais, et « inconnue » n'est pas une nuance d'« incomplète ».

![La table des nuits et ses badges](https://raw.githubusercontent.com/echonuit/vigiechiro-pr-companion/main/.github/assets/apercu-import-multi-nuits.png)

<video controls width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioCarteMultiNuitsTest.la_table_des_nuits_et_ses_badges.mp4"></video>

**Ce que l'image et le clip montrent** : la première nuit est **complète**, les deux suivantes de
**complétude inconnue**. C'est la distinction qui compte.
**Ce qu'elle ne montre pas** : le libellé entier du second badge, que la colonne tronque en
« complétude inco… ». C'est un défaut connu, ouvert, et il n'est pas corrigé.

### 6 · Une carte protégée en écriture le dit

**Corrigé**, et le bandeau dit trois choses dans cet ordre : que l'import de cette nuit **fonctionne**,
le geste à faire sur la carte, et que c'est la **prochaine** nuit qui est en jeu.

![Le bandeau du support en lecture seule](https://raw.githubusercontent.com/echonuit/vigiechiro-pr-companion/main/.github/assets/apercu-import-lecture-seule.png)

<video controls width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioBandeauLectureSeuleTest.le_support_en_lecture_seule_s_annonce_et_l_import_aboutit.mp4"></video>

**Ce que l'image montre** : le bandeau entier. **Ce que le clip ajoute** : l'import qui **aboutit**
derrière - le bandeau informe, il ne bloque pas.
**Ce qu'elle ne montre pas** : la cause. Un verrou mécanique poussé sans y penser donne le même
symptôme qu'une carte en fin de vie, et Companion ne prétend pas les distinguer.

### 7 · La complétude d'une nuit se conserve jusqu'au diagnostic

Trouvé en écrivant la recette de ce chantier : la complétude était **calculée** à l'inspection, portée
jusqu'à la base, et **jetée une ligne avant** d'y entrer. Le diagnostic ne pouvait donc jamais rien en
dire.

**Corrigé.** Tu ne l'avais pas signalé - personne ne pouvait le voir.

### 8 · Une nuit interrompue en son milieu est signalée

Une carte pleine, une batterie vide, un arrêt subi : la nuit s'arrête avant son terme et rien ne le
disait.

![Le second encart, sur une nuit interrompue](https://raw.githubusercontent.com/echonuit/vigiechiro-pr-companion/main/.github/assets/apercu-diagnostic-nuit-interrompue.png)

<video controls width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioNuitInterrompueTest.une_nuit_interrompue_le_dit.mp4"></video>

**Ce que l'image et le clip montrent** : sous la cohérence horaire, un **second encart** qui dit que le
journal montre une nuit interrompue. Deux axes distincts : le premier dit si l'enregistrement couvre la fenêtre
exigée, celui-ci s'il s'est interrompu.
**Ce qu'elle ne montre pas** : la **cause** de l'interruption. Le journal dit qu'elle a eu lieu, pas
pourquoi.

---

## Ce qui n'est pas corrigé

Deux points de ton retour restent ouverts. Tu les rencontreras encore.

**Le 403 au téléversement.** Ton journal le montre intact les 15 et 29 août, sur deux sites. Le rejeu
en boucle qui l'accompagnait a disparu, mais le refus lui-même est là. Le chantier est ouvert et il
n'est pas instruit.

**Un import peut casser sur un verrou local.** Ton journal porte un incident grave remonté à l'écran
pendant un import, le 14 août - que tu n'avais pas signalé. Le délai d'attente sur la base ne couvre
pas tous les cas. Ouvert.

---

## Ce qui ne viendra pas

**Le fond de carte satellite.** Tu l'as demandé pour situer un carré. Après instruction, il a été jugé
**non faisable à coût raisonnable**, et la demande est close. Ce n'est pas un report.

**Le contournement** : les coordonnées du point sont lisibles dans Companion, et se collent dans
n'importe quel fond de carte tiers. Ce n'est pas équivalent - il faut sortir de l'application - mais
cela répond au besoin de situer.

---

## Deux points où le produit avait déjà changé

**Le numéro de carré.** Tu écris avoir dû créer ton site sur le portail pour le connaître. C'est
exactement ce qu'un chantier a livré le **28 août** - cinq jours après la version que tu as jouée, qui
est du 23. La suivante te l'apporte.

**Plusieurs nuits sur une carte.** La capacité existe depuis longtemps : la table d'import liste chaque
nuit, et chacune devient un passage distinct. Tu posais la question parce que le défaut de la racine
t'empêchait d'y arriver, pas parce qu'elle manquait.
