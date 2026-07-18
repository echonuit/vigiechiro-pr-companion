# ADR 0021 — Le double-clic est le miroir de l'action principale, et il rend compte quand il n'aboutit pas

- **Statut** : Accepté — 2026-07-18
- **Chantier** : EPIC #1792 (#1794, #1834, #1837)

## Contexte

Les tables de l'application avaient des gestes divergents : le double-clic ouvrait l'écoute sur un écran, le passage sur un autre, rien ailleurs ; la « Fiche de l'espèce » s'atteignait au clic droit sur l'Inventaire et par le menu ☰ sur Sons & validation. Le chantier #1792 a unifié ces gestes, en ouvrant le double-clic à la fiche sur les vues espèces.

Cette unification a immédiatement produit un défaut, remonté de l'usage réel sur une vraie nuit (#1834). Sur Sons & validation, la quasi-totalité des lignes sont des **pseudo-taxons** (« Bruit », « Oiseau ») qui n'ont **pas de fiche**. Le double-clic y était volontairement **inerte** : rien ne s'ouvrait, et rien ne l'expliquait. L'utilisateur en a conclu, légitimement, que la fonctionnalité était cassée. L'Inventaire avait exactement le même trou (#1837).

Le menu contextuel, lui, traitait déjà le cas : son item se grise et porte le motif dans son libellé (« Fiche de l'espèce (aucune fiche disponible) »), selon le patron d'affordance de [ADR 0010](0010-dialogues-bloquants-sont-des-ports.md) et #789.

La cause de l'asymétrie est structurelle, pas un oubli : **un menu montre son état avant le clic, un double-clic n'a rien à montrer avant le geste**. Un item grisé se voit ; un double-clic qui ne fera rien est indiscernable d'un double-clic qui va agir. Son silence ne se distingue donc pas d'une panne.

## Décision

Deux règles, indissociables.

**1. Le double-clic est le miroir de l'action principale du menu de ligne**, jamais une action qu'on ne trouve nulle part ailleurs. N'ayant aucune affordance propre, il ne peut pas être le seul chemin vers une capacité : tout ce qu'il déclenche reste atteignable par un chemin visible (menu contextuel, bouton, ☰).

**2. Une action ouverte au double-clic rend compte quand elle n'aboutit pas.** Elle ne peut pas se contenter d'être inerte. Concrètement, `ActionFicheEspece.ouvrir` **rend un booléen** au lieu d'être silencieuse, et `ouvrirOuSignaler(espece, siAucuneFiche)` construit le motif - qui **nomme le taxon tel que la table l'affiche** - et le route vers le canal de l'écran.

Le compte rendu est **non modal** : `RetourOperation` en sévérité `INFO` (« action refusée ou guidage, sans échec technique ») rendu par `BandeauRetour`. Un double-clic est un geste courant et souvent accidentel ; sur une table de milliers de lignes majoritairement du bruit, une boîte modale bloquante serait pire que le silence qu'elle prétend corriger.

## Conséquences

- Le motif est construit **une seule fois**, dans `ActionFicheEspece`, depuis l'`EspeceIdentifiee` que les écrans lui passent déjà. Les appelants ne fournissent que le canal de signalement : un seul libellé, deux écrans.
- `RetourOperation` et `BandeauRetour`, nés dans la vue audio, sont **remontés dans `commun`** : « rendre compte sans bloquer » n'a rien de propre à un écran. Le style suit dans `design.css` sous `.bandeau-retour`.
- L'Inventaire y gagne au passage la **sévérité** qui lui manquait : son retour d'export tombait dans un libellé nu, sans couleur ni fermeture, quand la même information était déjà colorée sur Sons & validation.
- La règle s'applique **à tout geste sans état visible**, pas seulement au double-clic : un raccourci clavier, un glisser-déposer posent le même problème d'affordance et appellent la même réponse.
- Purement présentationnel : aucune capacité métier introduite, donc sans objet côté CLI ([ADR 0014](0014-parite-cli-ihm.md)).

## Alternatives écartées

- **Laisser le double-clic silencieux**, au motif que le clic droit explique déjà. C'est l'état qui a produit le rapport de bug : l'utilisateur qui double-clique n'a aucune raison d'aller vérifier dans un menu qu'il n'a pas ouvert.
- **Un dialogue modal** (`Notificateur`, qui s'appuie sur `Alert.showAndWait()`). Correct sur le papier, insupportable à l'usage : chaque double-clic accidentel sur l'une des milliers de lignes « Bruit » aurait bloqué l'application.
- **Griser ou masquer les lignes sans fiche.** Détourne un signal de présentation vers une information qui n'a rien à voir : une observation « Bruit » est une donnée parfaitement valide, qu'on valide et commente comme les autres.
- **Ne pas ouvrir la fiche au double-clic du tout**, et s'en tenir au menu. Aurait évité le défaut mais perdu l'harmonisation recherchée : le geste le plus naturel sur une ligne serait resté inerte partout.
