---
type: adr
title: "Un dispositif qui peut ne rien vérifier le dit"
status: stable
article: A3
chantier: "#2748, lot #2724 du chantier #2720 ; couvre #2887, #3287, #3345, #3382"
decided_at: 2026-08-06
verification: certaine
enforced_by:
  - ".github/scripts/veille_contrat_api.py"
verified:
  - by: machine:ci
    at: 2026-08-06
---

# Un dispositif qui peut ne rien vérifier le dit

## Contexte

L'étape 0 du lot a réuni, **par concept et non par mot-clé**, quatre issues sans rapport apparent :

| Issue | Ce qui restait vert sans avoir vérifié | Cause |
|---|---|---|
| #2748 | le contrat API hebdomadaire | l'absence d'exécution est invisible |
| #3287 | un renvoi `workflow_run` entre workflows | un lien par chaîne que rien n'apparie |
| #2887 | les sites MkDocs sur une PR | la garde ne tourne pas là où elle servirait |
| #2892 | `EphemerideSolaireTest` | les dates choisies sont celles où la fonction ne réagit pas |

Deux autres s'y sont ajoutées en cours de lot : #3345 (une étape de CI qui sortait avant son contrôle
et rendait `0`) et #3382 (un épinglage **cohérent** mais périmé d'une majeure, que Dependabot n'a
jamais proposé).

Le symptôme est unique et il est le pire qui soit : **un vert qui n'a rien vérifié ressemble
exactement à un vert qui a vérifié**. Aucune de ces six situations n'a été trouvée par la CI. Toutes
l'ont été en regardant, passage par passage, ce que l'étape avait réellement fait - ce que personne ne
fait sans raison de le faire.

## Décision

**Un dispositif de vérification doit être capable de dire qu'il n'a rien vérifié, et ce cas doit être
distinct de son succès.** Trois règles en découlent.

### 1. Une mesure vide n'est pas un zéro

Un balayage qui ne trouve rien parce qu'il n'a **rien lu** doit échouer, pas rassurer. Chaque garde
porte donc des refus explicites, séparés de son verdict normal :

- `veille-contrat-api.sh` distingue « historique vide » (la question n'a pas été posée) de « aucune
  exécution réelle » (elle l'a été, la réponse est mauvaise) ;
- `verifie-fraicheur-actions.sh` refuse de conclure sur une version qu'il n'a pas pu résoudre, plutôt
  que de la compter comme à jour ;
- le job `second-compilateur` exige de voir **deux** passes `Compiling with eclipse` : une faute dans
  le profil ferait retomber la compilation sur `javac` et le job resterait vert **en n'ayant rien
  comparé**.

### 2. Le détecteur se surveille lui-même

Ces gardes reconnaissent ce qu'ils cherchent par un **nom** - une étape de workflow, un libellé, un
paquet. Ce nom vit ailleurs, et le renommer casserait la détection **en silence**, refabriquant le
défaut corrigé.

Un garde refuse donc de conclure quand il ne retrouve plus sa cible nulle part, et le dit ainsi :
c'est **lui** qui est en cause, pas ce qu'il surveille. `veille-contrat-api.sh` le fait sur le nom de
l'étape du contrat.

### 3. La fraîcheur se mesure, elle ne se persiste pas

`#2748` proposait d'enregistrer la date de la dernière vérification réelle - artefact, badge, issue
épinglée. Aucune ne le fait : **l'historique des passages EST cette date**, et l'API GitHub la rend.

Un fichier commité, un artefact (90 jours) ou un cache (7 jours) deviendraient chacun une **seconde
chose à surveiller**, dont la première panne serait, ici encore, un silence. Le même raisonnement
s'applique à #3382 : la fraîcheur d'un épinglage se lit en comparant à l'amont, pas dans un registre
qu'on tient à la main.

## Conséquences

- `api-live.yml` rougit au-delà de **21 jours** sans vérification réelle, et affiche à **chaque**
  passage, vert compris, la date de la dernière ;
- `securite-dependances.yml` compare chaque SHA épinglé à la dernière release amont ;
- les autotests hors ligne de ces gardes tournent dans `lint.yml` à **chaque PR** : c'est le seul
  endroit où on les voit à l'œuvre entre deux passages hebdomadaires.

### Rougir trop s'apprend aussi vite que se taire

L'asymétrie de `verifie-fraicheur-actions.sh` est délibérée : **avertissement** sur un retard dans la
même majeure, **rouge** sur une majeure entière. L'amont publie pour des raisons qui ne nous regardent
pas ; un garde qui rougirait à chaque release amont serait désactivé en trois semaines, et on serait
revenu au point de départ en ayant payé le trajet.

Le même arbitrage a été rendu pour le second compilateur : ses **1293 avertissements** ne bloquent
pas, ses erreurs oui. Et pour `api-live.yml`, dont l'expiration d'un jeton de 14 jours face à un
passage hebdomadaire reste **verte** : tolérer une expiration est juste, ne plus jamais vérifier ne
l'est pas.

### Un vert peut être vide sans que personne ait mal fait

Aucun des six cas ne vient d'une négligence. Les épinglages étaient cohérents, Dependabot tournait, la
CI était verte, et l'avertissement « jeton à renouveler » était une décision réfléchie. Le défaut
n'est jamais dans le dispositif qu'on regarde : il est dans ce qu'aucun dispositif ne dit.
