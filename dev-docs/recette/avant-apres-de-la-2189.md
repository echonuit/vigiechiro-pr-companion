# Ce que la 2.189.0 montrait, et ce qu'elle montre maintenant

Le retour de terrain de la 2.189.0 a produit des correctifs. Cette page dit comment on **fabrique** la
preuve qu'ils changent quelque chose à l'écran, et ce que les paires obtenues montrent.

Elle ne remplace pas [le retour lui-même](../retours/2189-ce-que-ton-retour-a-produit.md), qui
s'adresse à l'observateur. Celle-ci s'adresse à qui refera l'exercice.

## Le problème que ça résout

Un avant/après suppose le même cas joué sur deux versions. Or le cas qui démontre un correctif est
presque toujours écrit **avec** le correctif : il n'existe pas dans la version d'avant, donc il n'y a
rien à comparer. Un tournage de la version d'avant ne contient pas ce clip, et il ne peut pas le
contenir.

L'avant se **fabrique** donc, et de deux façons selon l'âge du correctif.

| Le correctif est | La méthode | Pourquoi |
|---|---|---|
| récent, et ses fichiers n'ont pas rebougé | on applique l'**inverse de sa production** sur `main` | le harnais d'aujourd'hui reste en place, rien à greffer |
| plus ancien | on part de **son parent** et on y **greffe le scénario** | la rustine inverse est refusée, les fichiers ayant bougé depuis |

Dans les deux cas on ne défait que la **production** : le scénario, lui, reste celui d'aujourd'hui.
C'est ce qui rend les deux clips comparables.

Le test échoue alors, et **c'est voulu** : il constate le défaut qu'on vient de remettre. Le clip est
produit quand même, l'enregistreur indexant délibérément le film d'un cas rouge.

## Le piège qui invalide tout, et le témoin qui le dit

**Ne jamais comparer un clip filmé en local à un clip pris dans une pré-version.** Mesuré le
2026-09-04, à 5 % de tolérance :

| Ce qui est comparé | Écart sur l'image finale |
|---|---|
| local contre `clips-recette`, correctif défait | 92,3 % |
| local contre `clips-recette`, **code intact** | **92,3 %** |

La seconde ligne est le témoin : sans aucun changement de code, l'écart est le même. Ce sont le rendu,
les polices et les décorations du poste contre ceux du runner. Un gros chiffre ressemble à une preuve,
et le vrai signal est ici **deux ordres de grandeur plus petit**.

Filmer les deux côtés du même côté de la barrière. Le contrôle qui le dit : comparer une pré-version
**avec elle-même** doit rendre un nul parfait, et c'est le cas.

## Les paires obtenues

Écarts mesurés en local contre local, à 5 % de tolérance. Les clips sont plus bas, sur la pré-version [`clips-avant-apres`](https://github.com/echonuit/vigiechiro-pr-companion/releases/tag/clips-avant-apres).

| Défaut | Écart | Ce que l'avant montrait | Ce que l'après montre |
|---|---|---|---|
| #4981 réveil par bouton | 3,12 % | « Réveil non programmé : Wakeup by PINPUSH » porté aux **anomalies** | « Aucune anomalie détectée » |
| #5093 nuit interrompue | 3,60 % | l'encart **n'existe pas** | « cette nuit s'est interrompue avant son terme » |
| #4988 plages du diagnostic, chantier #4984 | 8,04 % | rien sous les heures de la nuit | « Protocole : 20:00 à 07:15 · Enregistré : 20:25 à 07:47 » |

### Les deux défauts qui n'ont pas de paire à eux

**#4990, la nuit dite complète**, partage son correctif avec #5093. Défaire ce correctif ne change
**rien** à `ScenarioCarteMultiNuitsTest`, dont les écrans ne bougent pas de 0,25 %. Ce n'est pas que le
défaut soit invisible : c'est que ce scénario-là regarde ailleurs. La paire de #5093 le démontre pour
les deux.

Le contrôle qui a permis de l'affirmer : avec le correctif défait, `ServiceImportTest` échoue bien. Le
défaut ÉTAIT remis. Sans cette vérification, « le scénario ne bouge pas » serait indiscernable de
« mon retour en arrière n'a rien cassé ».

**#3461, la racine de carte SD**, n'a aucun clip : ses cas sont écrits en `S10-09` à `S10-11` et
attendent d'être filmés. Un avant/après ne se fabrique pas pour un cas qui n'existe pas en clip.

## Les clips, à regarder
Chaque paire se lit dans l'ordre : l'avant, puis l'après. Le test de l'avant **échoue**, et c'est ce qui prouve que le défaut y est bien.

### #4981 · un appui sur une touche n'est pas une anomalie

**Avant.** L'appui sur une touche est porté aux **anomalies**, et deux alertes ambre s'ensuivent.

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-avant-apres/4981-reveil-par-bouton--AVANT.mp4"></video>

**Après.** « Aucune anomalie détectée », et la seconde alerte devient l'information « fin de nuit normale ».

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-avant-apres/4981-reveil-par-bouton--APRES.mp4"></video>

### #5093 · une nuit interrompue le dit

**Avant.** Une seule alerte : l'encart de la nuit interrompue **n'existe pas**.

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-avant-apres/5093-nuit-interrompue--AVANT.mp4"></video>

**Après.** L'encart paraît : « cette nuit s'est interrompue avant son terme ».

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-avant-apres/5093-nuit-interrompue--APRES.mp4"></video>

### #4988 · les deux plages du diagnostic

**Avant.** Rien sous les heures de la nuit : le verdict est à croire sur parole.

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-avant-apres/4984-plages-du-diagnostic--AVANT.mp4"></video>

**Après.** « Protocole : 20:00 à 07:15 · Enregistré : 20:25 à 07:47 », l'exigé face au tenu.

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-avant-apres/4984-plages-du-diagnostic--APRES.mp4"></video>

## Ce que ces paires ne prouvent pas

Elles montrent que l'écran **change**. Elles ne disent pas qu'un observateur **comprend** ce qui a
changé : cette question se répond en regardant, et le chiffre n'y répond pas. Un clip dont la réponse
est non n'est pas un clip, c'est une vidéo valide et vide.

Elles ne disent rien non plus des correctifs qui ne touchent aucun écran. Le refus 403 et le verrou
local n'ont pas de paire et ne peuvent pas en avoir.
