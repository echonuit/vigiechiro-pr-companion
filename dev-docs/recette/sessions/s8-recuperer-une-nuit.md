# S8 · Récupérer une nuit déposée

> **Écran propriétaire** : aucun en propre — la session traverse **Connexion**, **Mes sites**, **Toutes
> mes nuits** et la **fiche passage**.
> **Features** : reconstruction-passages, passage, sites, connexion. · **Statut : à jouer.**
> Retour à la [méthode](../index.md).

## Objectif

Rejouer le parcours **P12** de bout en bout sur un vrai compte, tel que l'EPIC #2554 l'a refait : une
base neuve se connecte, récupère ses nuits **avec leur contenu**, et une carte SD leur rend leur audio.

Ce que cette session couvre et qu'aucun test ne peut couvrir : la **durée** réellement ressentie, la
lisibilité des barres, le comportement du bouton **Annuler** sur une opération longue, et le fait qu'une
vraie carte SD soit reconnue.

## Environnement

- **Compte réel**, carré **130711**, point **Z41** (4 participations, dont 2 analysées et 2 non).
- **Base neuve** (workspace de recette, jamais celui de production).
- **Carte SD réelle** de l'enregistreur **PR1997632**.
- ⚠️ Session **en lecture seule** côté plateforme : aucun dépôt, aucune écriture. Si un geste se met à
  écrire, c'est un constat en soi.

## Le script (une case = un fait observable)

**Étape 1 · La connexion devient une opération longue**

1. Coller le jeton : une **modale de progression** s'ouvre par-dessus la modale de connexion.
2. La barre **avance** et son libellé nomme la nuit en cours (« Nuits k / N »), il ne reste pas figé.
3. Une **estimation du temps restant** apparaît une fois l'avancement mesurable.
4. Le bouton **Annuler** est atteignable pendant toute l'opération (pas masqué, pas hors fenêtre).
5. ⚠️ Point de jugement : **une modale par-dessus une modale** est-elle acceptable ici, ou faut-il
   fondre la barre dans la modale de connexion ?
6. À la fin, la modale de connexion annonce l'identité **et** le résumé de ce qui a été récupéré.

**Étape 2 · Ce que le compte rendu affirme**

7. Le résumé distingue les nuits **récupérées** de celles **en attente d'analyse Vigie-Chiro**.
8. Couper le réseau avant une synchronisation, la relancer : le résumé dit « **non récupérée(s), à
   réessayer** », et **jamais** « en attente d'analyse ». (C'est le défaut P1-C du chantier.)
9. Les nombres annoncés correspondent à ce que la table montre réellement.

**Étape 3 · Annuler, et reprendre**

10. Lancer « Synchroniser depuis VigieChiro » depuis **Mes sites**, puis **Annuler** en cours.
11. Le message dit que ce qui a été récupéré **est conservé** et que la suivante reprendra le reste.
12. La table ne montre **aucune nuit à moitié faite** (une nuit a ses observations, ou n'en a aucune).
13. Relancer la synchronisation : elle **reprend** le travail restant, sans recréer ce qui existe.

**Étape 4 · La fiche d'une nuit récupérée**

14. La nuit apparaît avec ses **observations** et son décompte de séquences.
15. « Audio » indique **ABSENTE (0 / N)**.
16. « Réactiver ce passage » est **actif** (c'est le défaut d'origine du chantier).
17. Les heures de la nuit sont marquées **attestées par les enregistrements**, pas « déclarées ».

**Étape 5 · Réactiver depuis la carte SD**

18. Désigner la carte : la question « copier ou référencer » apparaît. ⚠️ Sur la voie **bruts**, la
    réponse est **sans effet** (constat #2577) : vérifier ce que l'utilisateur comprend.
19. La modale de réactivation montre ses **deux barres de phase**.
20. À la fin, « Audio » passe à **COMPLETE (N / N)** et la nuit est **écoutable**.
21. Ouvrir une séquence : le son correspond bien à l'observation affichée.

**Étape 6 · Le rattrapage**

22. Menu ☰ › **Compléter une nuit récupérée** : seules les nuits **sans contenu** y figurent.
23. L'état de liste ne prétend plus qu'elles « n'existent pas sur cette machine ».
24. Compléter une nuit : elle disparaît de la liste et gagne ses observations.

## Ce que la session doit mesurer

- **Durée** de la première synchronisation, et nombre de nuits traitées. Repère mesuré au banc :
  10,5 s pour 2 nuits (≈ 9 000 séquences, 24 542 observations). C'est ce chiffre qui a permis d'écarter
  un bornage du balayage ; s'il ne tient pas en usage réel, la décision est à rouvrir.

## Verdict par axe (dernière passe)

À remplir en séance.
