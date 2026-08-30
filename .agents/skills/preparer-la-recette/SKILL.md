---
name: preparer-la-recette
description: Use at closure pass 6b, once the tests cover the delivered usages, to make sure every capability the chantier added knows how it gets verified by hand. Three pieces make a case replayable, and one missing turns it back into an intention.
license: GPL-3.0-or-later
metadata:
  langue: fr
  origine: dev-docs/cycle-de-chantier.md
---

# Préparer la recette

## Loi d'airain

```
UNE CAPACITÉ N'EST PAS FINIE TANT QU'ON NE SAIT PAS COMMENT LA VÉRIFIER À LA MAIN
```

Et tant que ce « comment » n'est pas écrit **là où on le retrouvera**. La recette n'est pas le
déversoir de ce qui a résisté à l'automatisation : c'est l'endroit où vit le **procédé de
vérification** d'une capacité.

## Annoncer

« J'utilise la compétence preparer-la-recette sur les capacités ajoutées par <le chantier>. »

## Pourquoi une lettre et pas un numéro

Elle **prolonge** la passe 6 sans être une étape indépendante : on ne prépare pas la recette avant
d'avoir écrit les tests. La règle générale reste qu'une passe porte un numéro ; la lettre est
l'exception, et elle se justifie par cette continuité.

Le coût l'a confirmée sans la décider : renuméroter aurait touché **161 citations dans 73 fichiers**,
dont 39 ADR et 20 fichiers de production (#4839).

## Le critère qui dit qu'une case est finie : la rejouabilité

Quelqu'un qui n'a pas fait le chantier doit pouvoir la refaire, dans six mois, autant de fois que
nécessaire, et obtenir la même chose. Trois pièces, et **il en manque une seule** pour que la case
redevienne une intention.

| Pièce | Ce qu'elle répond | Ce qui arrive si elle manque |
|---|---|---|
| le **geste** | qu'est-ce que je fais ? | la case se rejoue différemment à chaque campagne |
| l'**observation attendue** | qu'est-ce que je dois voir ? | on coche « ça marche » sans référence |
| la **fixture** | sur quelles données ? | la donnée se bricole, donc le résultat ne se compare pas |

## Fonction de garde

```
1. DESIGNER  la session PROPRIETAIRE : un ecran se deroule en entier dans UNE
             seule session, ailleurs il n est qu ecran de transit.
2. DIRE      en issue, et non en silence, quand la session est partielle ou
             jamais jouee.
3. ECRIRE    les cases a leur place, en points `Sxx-NN` groupes par etape.
             UNE CASE = UN FAIT OBSERVABLE.
4. FOURNIR   de quoi les jouer : etendre la SPEC DU GENERATEUR si aucune
             fixture ne porte le cas.
5. RELIRE    le statut de la session dans l index, qui vieillit tout seul.
```

## 1. La session propriétaire, et ce qui arrive quand on se trompe

Une case déposée dans la mauvaise session sera jouée **deux fois ou jamais**.

L'état de chaque session se lit dans l'index de la recette, **et nulle part ailleurs**. La première
version de la page du cycle recopiait la liste : les deux copies ont divergé en **quelques heures**,
S7 ayant été écrite le jour même où le paragraphe affirmait qu'elle n'existait pas.

**Un inventaire ne se duplique pas, il se cite.**

Toutes les sessions ne sont pas au même état. Quand le chantier touche un écran d'une session
partielle ou jamais jouée, cela se **dit en issue** : sinon la capacité est réputée vérifiable par un
script qui ne la couvre pas.

## 3. Une case est un fait observable, écrit pour qui n'était pas là

> « le bandeau annonce la nuit du 22/04 et le nombre de fichiers retenus » se rejoue.
>
> « vérifier que l'import marche » non.

Jamais un contrôle groupé : une case, un fait.

## 4. La fixture se génère, elle ne se bricole pas

Les cartes SD de recette sont **générées** depuis quelques kilo-octets de spec, précisément pour
revenir à l'identique.

Si aucune fixture existante ne porte le cas, une nuit sans GPS, un journal qui contredit les WAV, un
volume qui déborde, **étendre la spec du générateur fait partie de cette passe**. Une donnée
fabriquée à la main pour l'occasion ne se retrouvera pas à la campagne suivante, et la case
deviendra injouable sans que personne ne l'ait décidé.

## Ce que la passe empêche, et qui se déguise bien

L'ancienne formulation, *ce qui ne peut pas être automatisé va en recette*, laissait « pas
automatisable » devenir silencieusement « **pas vérifié** ».

La règle actuelle ferme aussi la variante suivante, plus difficile à voir : une case **écrite mais
injouable**, faute de fixture ou faute de session où la déposer. Elle a l'apparence d'une
vérification prévue, et elle se coche.

## Ce que cette passe rend possible plus loin

Le cas `Sxx-NN` posé ici est **ce que la passe 8 regarde**. Le clip d'un cas perceptif porte le
verdict, celui d'un cas asserté vérifie que le test joue ce que son nom annonce.

Sans cette passe, il n'y a rien à filmer, et c'est l'argument qui place les clips en passe 8 plutôt
qu'en passe 4 ([ADR 4903](../../../dev-docs/decisions/4903-la-place-d-un-clip-se-deduit-de-l-ordre.md)).

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « Ce n'est pas automatisable, ça ira en recette » | C'est ainsi que « pas automatisable » devient « pas vérifié » |
| « La case est écrite, la passe est faite » | Trois pièces. Sans la fixture, elle est écrite et injouable |
| « Je note l'état des sessions ici » | Un inventaire ne se duplique pas, il se cite. Deux copies ont divergé en quelques heures |
| « Cette session est partielle, tant pis » | Cela se dit en issue, sinon la capacité passe pour vérifiée |
| « Je fabrique la donnée pour l'occasion » | Elle ne reviendra pas à la campagne suivante. La spec du générateur s'étend |
| « Une case pour vérifier que l'écran marche » | Une case, **un fait observable**, écrit pour qui n'était pas là |
