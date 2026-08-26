---
type: adr
title: "Une javadoc non relue est une dette, tenue par son empreinte"
status: stable
article: A9
chantier: "#4468 (l'appareillage de relecture, chantier #4394)"
decided_at: 2026-08-25
verification: probable
enforced_by:
  - "scripts/adr/4468-javadoc-non-relue.py"
ratchet: 0
inv_key: cliquet-relecture
verified:
  - by: machine:ci
    at: 2026-08-25
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-25
---

# Une javadoc non relue est une dette, tenue par son empreinte

## Contexte

Le cliquet A30 compte les **lignes de prose au-delà de huit par bloc**. C'est une mesure de
longueur, et elle ne dit rien de trois défauts que seule la lecture trouve, tous rencontrés sur ce
corpus :

- des **blocs fusionnés** : deux javadoc collées sur un membre, laissant son voisin muet ;
- des **références mortes** : `[#messageProperty()]` sur une classe qui n'en porte aucune, ou
  « injoignable, c'est un code 1 » quand c'est le code 2 ;
- des **fichiers entiers sans accents**, plus des îlots dans une centaine d'autres.

Aucun ne dépasse huit lignes, aucun n'est visible d'un motif, et « zéro commentaire non relu » ne se
prouvait donc avec rien.

Un manifeste de fichiers relus règle la moitié du problème : il dit ce qui reste, mais rien de ce
qui **change après coup**. Un fichier marqué relu dont la javadoc est ensuite réécrite reste marqué,
et le manifeste affirme alors qu'on a lu une prose qui n'existe plus. Deux fichiers l'ont montré
(`ApercuFx`, `DepotVigieChiro`) : marqués relus, ils avaient perdu tous leurs accents sans que rien
ne le signale.

## Décision

Le manifeste `scripts/methode/relus.txt` porte, pour chaque fichier relu, l'**empreinte de sa
javadoc** au moment de la relecture. Un cliquet compte les fichiers qui n'y figurent pas avec
l'empreinte du jour, et refuse toute remontée.

Deux faits en découlent, et ce sont les seuls que le cliquet tient :

- un fichier **neuf** arrive absent du manifeste, donc suspect : il ne peut pas passer sans lecture ;
- une javadoc **réécrite** change d'empreinte, son fichier redevient suspect, et il faut le relire
  pour le remarquer.

L'empreinte ignore l'indentation, qui appartient au formateur : une passe de `spotless` ne rouvre
pas la dette du dépôt entier. Elle couvre en revanche les étiquettes de contrat : réécrire un
`@param` est une modification de javadoc.

Le grain est le **fichier**, non la ligne de prose - à l'inverse de A30, qui mesure un
raccourcissement. Ici l'unité de travail est la lecture, et un fichier n'entre au manifeste que
lorsque tous ses blocs ont été jugés. Compter les lignes non lues récompenserait de plus la
suppression à l'aveugle : effacer de la prose sans l'avoir lue ferait baisser le compte.

## Conséquences

Toute modification de javadoc demande un `--marque` du fichier touché :

```
python3 scripts/methode/couverture-relecture.py --marque <fichier…>
```

C'est le coût assumé de la garantie : mécanique, et payé par qui vient de lire le bloc.

Le cliquet est `probable` et non `certaine`, et ce n'est pas une prudence de forme : une ligne du
manifeste est une **affirmation humaine** - « j'ai ouvert ce fichier et jugé chacun de ses blocs » -
qu'aucun script ne vérifie. Le cliquet borne ce qui reste à affirmer, pas la sincérité de ce qui l'a
été.

Un cliquet à zéro ne serait plus une marge mais un **refus** : un fichier neuf, ou une javadoc
réécrite sous une entrée existante, ferait rougir la CI et se nommerait dans le message d'échec. Ce
dépôt n'y est pas : il ouvre à la totalité, et la descente est le travail du lot 2.

**La population couvre les deux arbres dès l'ouverture**, production et tests, pour la raison
qu'expose l'ADR 4359 : un commentaire de classe de test se lit comme un autre et vieillit pareil.
Les clés du manifeste sont relatives à la racine du dépôt, pour qu'une entrée dise de quel arbre elle
vient.

**Le cliquet ouvre à 1 979 fichiers**, soit la totalité : **32 158 lignes de prose** restent à
juger, et le manifeste part **vide**. Il est aujourd'hui à
<!--inv:cliquet-relecture-->0<!--/inv--> : tout l'arbre courant a été ouvert et jugé, et toucher à
l'une de ses javadoc rougit tant qu'elle n'a pas été relue.

Il partait vide et ce n'est pas un oubli. Les douze tranches de #4394 ont lu des **blocs** choisis pour
leur poids, jamais tous les blocs d'un fichier. Les inscrire reviendrait à affirmer une lecture qui
n'a pas eu lieu, c'est-à-dire à mentir sur la seule chose que ce dispositif prétend tenir.

La ligne d'origine a relevé, en soldant ses propres tests, un défaut que la production n'avait pas :
des **nombres écrits à la main que rien ne recompte**, décrivant le monde d'avant la migration qu'ils
avaient servi à justifier. C'est une chose à chercher en lisant, pas une mesure de ce dépôt.

Deux mutations, montées sur le corpus réel de chaque arbre, l'ont vu rouge : réécrire une ligne de
javadoc dans un fichier marqué relu, et déposer un fichier neuf. Le compteur
`couverture-relecture.py` reste l'outil de lecture (`--reste`, `--marque`) ; ce n'est pas lui qui
garde, c'est le cliquet.
