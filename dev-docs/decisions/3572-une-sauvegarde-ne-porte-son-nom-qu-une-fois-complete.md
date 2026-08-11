# ADR 3572 - Une sauvegarde ne porte son nom qu'une fois complète

- **Statut** : Accepté - 2026-08-11
- **Chantier** : #3572, suite de la clôture du lot 1 (#3559)
- **Vérification** : certaine - `ServiceSauvegardeTest#interrompue_elle_ne_se_fait_pas_passer_pour_complete`

## Contexte

La sauvegarde complète était le seul chemin d'écriture volumineuse **sans garde d'espace** : l'import,
le compacteur de dépôt et la restauration en avaient un, elle non. Or c'est elle qui écrit le plus, et
vers un support **choisi par l'utilisateur** - une clé, un disque externe, souvent plus petits que le
dossier de travail.

Ce que la vérification a montré dépasse le manque de garde, et c'est la vraie raison de cette ADR :

1. la copie échoue à mi-parcours, et le dossier `vigiechiro-sauvegarde-complete-…` **reste** ;
2. `InventaireSauvegardes.natureDe` classe par **préfixe de nom** : il est listé comme `COMPLETE` ;
3. le manifeste s'écrit **en dernier** : ce dossier n'en a pas ;
4. restaurer une sauvegarde sans manifeste emprunte `replacerSansManifeste`, le chemin d'avant #2726 -
   dossiers déversés à la racine du dossier de travail, chemins persistés non corrigés.

**Une sauvegarde tronquée se faisait donc passer pour une sauvegarde d'ancien format**, et se
restaurait en mode dégradé sans que personne ne l'ait décidé. L'absence de manifeste voulait dire deux
choses opposées, et rien ne les distinguait.

⚠️ L'[ADR 3514](3514-restaurer-n-efface-pas-ce-que-la-sauvegarde-ignore.md) avait pourtant tranché le
cas voisin : un manifeste **présent mais abîmé** est un refus explicite. Le manifeste **absent** n'avait
jamais été interrogé.

## Décision

**Une sauvegarde se construit sous un nom de chantier, et n'est renommée qu'une fois le manifeste
écrit.** Le garde d'espace la précède et refuse, chiffré, avant la première copie.

Les deux moitiés ne se remplacent pas :

- le **garde** ferme le cas courant - le support trop petit - avant qu'un octet soit écrit ;
- le **renommage tardif** ferme tous les autres : disque débranché, processus tué, coupure de courant.

### Pourquoi renommer plutôt que nettoyer à l'échec

Un nettoyage dans un `catch` ne survit pas à ce qui n'exécute aucun code. Une coupure de courant, un
`kill -9`, un démontage brutal ne laissent aucune chance à un bloc de rattrapage. Le renommage, lui, ne
demande rien à la panne : tant qu'il n'a pas eu lieu, le dossier n'est pas une sauvegarde.

C'est le geste que l'[ADR 3563](3563-le-regime-de-restauration-suit-la-place-disponible.md) applique à
la restauration, **dans l'autre sens** : étaler sous un nom provisoire, puis basculer.

### Le marqueur est en tête du nom, pas en suffixe

`InventaireSauvegardes` classe sur le **préfixe**. Un `…-complete-….en-chantier` serait donc encore
reconnu comme complet, et la décision n'aurait rien changé. C'est le genre de détail qui rend un
dispositif inopérant sans que rien ne le signale.

## Conséquences

- La collision de noms se cherche sur le nom **définitif** : deux sauvegardes de la même seconde se
  marcheraient dessus au renommage, pas à la création.
- La mesure d'un dossier vient d'`ArborescenceFichiers.octets`, remontée depuis `InventaireSauvegardes`
  où elle était privée. Une seconde implémentation aurait été la **huitième** variante du même parcours
  d'arborescence dans ce dépôt (#3574).
- ⚠️ Le garde d'espace n'a **pas** de test `bats` : il dépend de la place réellement libre, que le
  harnais ne peut pas contraindre, et injecter un faux espace par propriété système serait une porte
  dérobée en production. Le renommage, lui, y est éprouvé.
