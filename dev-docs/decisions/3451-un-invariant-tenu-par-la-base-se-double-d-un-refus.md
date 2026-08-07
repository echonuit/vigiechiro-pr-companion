# ADR 3451 - Un invariant tenu par la base se double d'un refus dans le code

- **Statut** : Accepté - 2026-08-07
- **Chantier** : #3451, suite de l'[ADR 3406](3406-une-nuit-porte-le-fuseau-de-son-site.md)
- **Vérification** : certaine - `CorrespondanceParticipationTest#une_borne_manquante_refuse_le_depot`

## Contexte

`CorrespondanceParticipation` assemblait le corps envoyé à Vigie-Chiro avec, pour chaque borne de nuit,
un repli silencieux :

```java
if (passage.dateEnregistrement() == null || passage.heureDebut() == null) {
    return null;   // le champ disparaît simplement du corps envoyé
}
```

Une mesure de mutation a signalé ces deux branches comme non couvertes. J'en ai d'abord conclu à un
trou produit - « un passage aux métadonnées incomplètes se dépose amputé de ses bornes ». **C'était
faux**, et la vérification tenait dans quinze lignes de SQL : `V01__schema.sql` déclare
`recording_date`, `start_time` et `end_time` en `TEXT NOT NULL`, les deux chemins de dépôt chargent
leur passage par le DAO, et la seule substitution d'horaires (`realignerSurLesPreuves`) écrit en base
**avant** l'envoi - une valeur nulle buterait sur la contrainte bien avant d'atteindre la plateforme.

## Décision

L'invariant *« une nuit a ses trois bornes »* n'est donc tenu que par **SQLite**. Il est désormais
**doublé d'un refus** dans le code : `versParticipation` lève une `RegleMetierException` plutôt que de
laisser partir un corps amputé.

Le garde est posé sur l'**entonnoir** (`versParticipation`), traversé par les deux chemins d'écriture,
et non chez les deux appelants connus - il vaut donc aussi pour ceux qui n'existent pas encore.

## Pourquoi garder un refus que rien ne peut déclencher

C'est la question qu'un lecteur futur posera, et la réponse est dans la **portée** de l'invariant.

Un `Passage` ne vient pas forcément de la base : le code en construit depuis la plateforme
(`SynchronisationParticipation`), en mémoire pour les captures, dans les services. Aucun de ces chemins
n'atteint le dépôt aujourd'hui. Le jour où l'un le fera - c'est précisément ce que #3442 prépare, en
dérivant des horaires depuis des coordonnées - le repli silencieux enverrait une nuit **sans bornes**
sur la plateforme nationale, et personne ne le verrait.

Un silence ne se remarque pas ; un refus, si. La nuit est l'unité de traitement du produit
([ADR 0009](0009-la-nuit-est-l-unite-bornee-a-midi.md)) et ses heures **décident de la partition** :
une nuit sans bornes n'est pas une nuit.

Ce garde n'est d'ailleurs pas du code mort au sens où on l'entend d'habitude : il est **atteignable
par construction en mémoire**, donc testable pour de vrai. Ses trois cas ont été vus **rouges** en
retirant l'appel - et eux seuls sur les seize du fichier.

## Conséquences

- les branches `return null` de `debutVc` et `finVc` **disparaissent** : l'invariant est énoncé une
  fois, à l'entrée, au lieu d'être re-testé partiellement à trois endroits. Il ne reste aucun code
  inatteignable derrière le garde ;
- le message nomme ce que l'observateur peut faire (réimporter, réaligner sur les enregistrements)
  plutôt que de décrire l'état interne ;
- `RegleMetierException` et non `IllegalStateException` : c'est un **refus** que l'IHM traduit en
  message, pas une panne technique. Le contrat de l'exception le prévoit explicitement.

## Ce que cette ADR apprend au-delà de son cas

**Une couverture de mutation dit « aucun test ne couvre cette ligne ». Elle ne dit pas « cette ligne
est atteignable ».** Confondre les deux produit une issue fausse, et le cycle de chantier avertit déjà
qu'« une issue fausse coûte plus cher que le trou qu'elle prétend signaler » - il l'écrit pour les
`grep`, ça vaut aussi pour PIT.

Le réflexe utile n'est pas d'écrire le test manquant, c'est de demander d'abord **par où** on
arriverait sur cette ligne. Quand la réponse est « par nulle part, grâce à une contrainte ailleurs »,
la vraie question devient : *cette contrainte est-elle au bon endroit, et que se passe-t-il le jour où
on la contourne ?*
