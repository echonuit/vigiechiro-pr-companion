# Périmètre MVP

Le périmètre du **produit minimum viable** est défini par arbitrage des [27 stories du story mapping](Story%20mapping.md) selon la méthode [MoSCoW](https://fr.wikipedia.org/wiki/M%C3%A9thode_MoSCoW) :

- **MUST** (M) : sans cette story, le MVP ne tient pas debout. À livrer en phase 1.
- **SHOULD** (S) : très souhaitable. À livrer si la vélocité le permet.
- **COULD** (C) : confort, peut être repoussé sans casser le produit.
- **WON'T** (W) : explicitement exclu de cette version.

## Arbitrages

| Story | Pts | M | S | C | W | Justification de l'arbitrage |
|---|--:|:--:|:--:|:--:|:--:|---|
| **E1 - Gérer mes sessions** | | | | | | |
| E1.S1 Voir le journal | 3 | ✅ | | | | Sans liste, on ne sait pas où on en est. |
| E1.S2 Détail d'une session | 3 | ✅ | | | | Pré-requis pour toutes les autres actions. |
| E1.S3 Commentaire libre session | 2 | | ✅ | | | Utile pour Léa, mais pas bloquant pour Marie. |
| E1.S4 Marquer « Validation terminée » | 2 | ✅ | | | | Pré-requis pour l'export propre. |
| E1.S5 Supprimer une session | 3 | ✅ | | | | Indispensable pour récupérer d'un import erroné. |
| E1.S6 Tagger par chantier | 8 | | | ✅ | | Utile pour Karim, gros effort, pas critique pour Marie. |
| **E2 - Importer une nuit** | | | | | | |
| E2.S1 Importer un dossier | 8 | ✅ | | | | Sans import, application vide = inutile. |
| E2.S2 Parser le LogPR | 3 | ✅ | | | | Inclus dans E2.S1, pas vraiment optionnel. |
| E2.S3 Parser le THLog | 2 | ✅ | | | | Idem. |
| E2.S4 Charger le CSV Tadarida | 5 | ✅ | | | | Sans CSV, pas d'observations à valider. |
| E2.S5 Reprendre un import interrompu | 3 | | | ✅ | | Pratique mais l'utilisateur peut relancer. Ajout post-MVP. |
| **E3 - Écouter un évènement** | | | | | | |
| E3.S1 Lecture audio ralentie | 8 | ✅ | | | | Sans écoute, on ne valide pas un évènement douteux. |
| E3.S2 Régler la vitesse | 3 | | ✅ | | | Bonus, ×10 par défaut suffit pour le MVP. |
| E3.S3 Forme d'onde | 5 | | | ✅ | | Confort visuel, demandable pour la v1.1. |
| E3.S4 Navigation observations adjacentes | 2 | | ✅ | | | Améliore le flux mais on peut cliquer dans la liste. |
| **E4 - Valider les classifications** | | | | | | |
| E4.S1 Liste des observations | 3 | ✅ | | | | Vue centrale du travail de validation. |
| E4.S2 Filtrer les observations | 5 | ✅ | | | | Sans filtre, infaisable sur 4000 lignes. Marie restera sur les bruits. |
| E4.S3 Valider une observation | 3 | ✅ | | | | Action métier centrale. |
| E4.S4 Corriger une observation | 3 | ✅ | | | | Action métier centrale. |
| E4.S5 Commentaire sur observation | 2 | | ✅ | | | Utile pour Léa, pas critique pour Marie. |
| **E5 - Exporter pour VigieChiro** | | | | | | |
| E5.S1 Exporter le CSV | 3 | ✅ | | | | Sortie obligatoire, sinon le travail ne sert à rien. |
| E5.S2 Récapitulatif d'export | 2 | | ✅ | | | Confort. À ajouter rapidement. |
| E5.S3 Marquer comme « Exportée » | 3 | ✅ | | | | Trace nécessaire pour ne pas exporter en double. |
| **E6 - Suivre la santé du PR** | | | | | | |
| E6.S1 Courbe T°/H | 5 | | ✅ | | | Demande de Karim. Pas critique pour Marie. |
| E6.S2 Niveau de batterie | 2 | | ✅ | | | Idem. |
| E6.S3 Liste évènements LogPR | 3 | | | ✅ | | Bonus diagnostic, post-MVP. |
| E6.S4 Comparer 2 sessions | 3 | | | | ✅ | Hors scope phase 1 - feature avancée pour Karim. |

## Récapitulatif

| Catégorie | Stories | Story points |
|---|--:|--:|
| **MUST** (MVP) | 14 | **49** |
| **SHOULD** | 6 | 18 |
| **COULD** | 6 | 27 |
| **WON'T** | 1 | 3 |
| **Total** | 27 | 97 |

**Le MVP strict représente 49 story points.**

## Deux cibles de livraison

Pour des équipes de 5-6 étudiants sur 3 semaines, la **cible normale** n'est pas le MVP strict mais le **MVP étendu** ci-dessous. Le MVP strict reste le filet de sécurité minimal en dessous duquel l'évaluation devient préoccupante.

### 🎯 Cible primaire - MVP étendu (~63 pts)

C'est ce que vous **devez viser par défaut**. Il s'agit du MVP strict augmenté des 5 SHOULD prioritaires qui rendent l'application réellement plaisante à utiliser :

| Story ajoutée au MUST | Pts | Pourquoi |
|---|--:|---|
| E3.S2 Régler la vitesse de lecture | 3 | Sans ça, ×10 fixe, problématique pour les espèces très aiguës. |
| E3.S4 Navigation observations adjacentes | 2 | Décuple la fluidité du parcours de validation. |
| E4.S5 Commentaire libre sur observation | 2 | Léa l'attend explicitement. Léger à implémenter. |
| E6.S1 Courbe T°/H | 5 | Sans diagnostic, l'application ne couvre pas le parcours P5. |
| E6.S2 Niveau de batterie | 2 | Léger en complément de E6.S1 (mêmes données). |

**Total MVP étendu** : 14 stories MUST + 5 SHOULD prioritaires = **19 stories, 63 story points**.

### 🛡️ Cible de repli - MVP strict (49 pts)

À ne défendre qu'en cas de difficulté manifeste après le sprint 2. Un MVP strict livré proprement, testé, avec une CI verte et un historique Git soigné vaut **mieux** qu'un MVP étendu bâclé. Si vous devez retrancher, retirez les SHOULD dans l'ordre **inverse** de leur valeur ajoutée perçue : E3.S2 et E3.S4 d'abord (confort audio), puis E4.S5 (commentaire obs), puis E6.S2 et E6.S1 (diagnostic).

### 🚀 Cible étirée - SHOULD restants + COULD opportunistes

Si le sprint 2 termine en avance, vous pouvez attaquer :

- E1.S3 Commentaire libre session (2 pts) - 5 minutes de travail vu E4.S5.
- E2.S5 Reprendre un import interrompu (3 pts) - améliore la robustesse, plaisant à présenter en soutenance.
- E1.S6 Tagger par chantier (8 pts) - bel argument à présenter à l'oral, valorisé par Karim.
- E5.S2 Récapitulatif d'export (2 pts) - finition rapide.

**Ne pas** essayer d'attaquer E3.S3 (forme d'onde, 5 pts) ni E6.S4 (comparer 2 sessions, 3 pts) : leur intérêt est réel mais leur coût d'intégration dépasse leur valeur dans le cadre d'une SAE.

## Stratégie

1. **Ne pas commencer par l'IHM** : commencer par le code métier (parsing, modèles, persistance JDBC) et le tester unitairement, avant d'attaquer JavaFX.
2. **Verticaliser** : livrer chaque story de bout en bout (parser → persister → afficher → tester) plutôt que de stratifier (toute la couche d'accès, puis toute la couche métier, puis toute l'IHM).
3. **Tenir l'ordre suivant** :
   - Sprint 1 : E2 (import), E1.S1 + E1.S2 (voir les sessions et leur détail).
   - Sprint 2 : E4 (validation), E3.S1 (écoute audio), E3.S2 + E3.S4 (raffinements audio).
   - Sprint 3 : E5 (export), E1.S4 + E1.S5, E4.S5, E6.S1 + E6.S2, polish + soutenance.
4. **Réviser les estimations en équipe** au début du sprint 1 par planning poker. Si vous avez des écarts > 2 points entre estimations individuelles, c'est qu'il y a une compréhension non partagée à clarifier.
