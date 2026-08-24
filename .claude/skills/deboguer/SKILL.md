---
name: deboguer
description: Use on any defect, test failure or unexpected behaviour, before proposing a fix. Root cause before remedy, the first test reproduces the defect, and the twin hunt that stops a fix from leaving a second site behind.
license: GPL-3.0-or-later
metadata:
  langue: fr
  origine: dev-docs/cycle-de-chantier.md
---

# Déboguer

## Loi d'airain

```
PAS DE CORRECTIF SANS CAUSE RACINE, ET LE PREMIER TEST REPRODUIT LE DÉFAUT
```

Une correction n'a pas de « comportement attendu » tant qu'on n'a pas compris le défaut. Corriger
un symptôme est un échec, pas un raccourci.

## Annoncer

« J'utilise la compétence deboguer sur <le symptôme>, cause racine d'abord. »

## Fonction de garde

```
1. LIRE       le symptome exact, et le message d echec en entier. Pas son resume.
2. REPRODUIRE par un test qui echoue PARCE QUE le produit est faux, et qui passera
              au vert quand il ne le sera plus.
3. CHERCHER   le jumeau : « qui d autre fait la meme chose ? », pas « ou est ce symptome ? ».
4. ELARGIR    le test qui reproduit a TOUS les jumeaux trouves.
5. CORRIGER   seulement alors.
```

Sauter l'étape 3, c'est laisser un site derrière soi.

## Le premier test reproduit

Il échoue **parce que le produit est faux**, et il passe au vert quand il ne l'est plus. Un test de
caractérisation reste du rouge d'abord.

**Pour un garde de forme**, le corollaire : on ne sait ce qu'il faut interdire qu'une fois le défaut
lu. Le garde s'écrit donc **après l'analyse mais avant le correctif**, et se confronte aux **lignes
fautives d'origine**. C'est ce qui a fait abandonner un garde textuel sur les fuseaux horaires : il
aurait manqué les deux moitiés du défaut.

## Chercher le jumeau, avant de corriger

Un défaut a rarement un seul site. La question n'est pas « où est ce symptôme ? » mais **« qui
d'autre fait la même chose ? »**.

C'est le moment d'interroger le graphe plutôt que de se fier à un `grep` sur le nom de la méthode
fautive :

```bash
graphify query "qui d'autre <fait la chose fautive> ?"
```

Un `grep` cherche des chaînes ; le jumeau porte souvent un autre nom. Sur un cas réel, le graphe a
désigné **deux services** qui écrivaient au même endroit sans que l'issue les nomme. Sur un autre,
une **troisième** écriture d'une règle, qui ne citait aucun des identifiants cherchés.

La forme du défaut prime sur son symptôme.

## Un rouge inattendu est une trouvaille

Un test qui échoue pour une autre raison que celle attendue vient de dire quelque chose. **Lire le
message avant de corriger.** Le réflexe de corriger jusqu'au vert efface l'information.

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « Je vois le problème, je corrige » | Vous voyez le symptôme. La cause est ailleurs une fois sur deux |
| « Le test passe maintenant » | Passait-il avant ? Si oui, il ne reproduisait rien |
| « C'est un cas isolé » | Cherchez le jumeau. Un défaut a rarement un seul site |
| « `grep` ne trouve rien d'autre » | Le jumeau porte un autre nom. Interrogez le graphe |
| « Ce n'est pas le rouge attendu, je continue » | Ce rouge-là est la trouvaille |
| « C'est urgent, je corrige d'abord » | Systématique est plus rapide que tâtonner |
