# ADR 0010 - Les dialogues bloquants (confirmation, compte rendu) sont des ports injectables

- **Statut** : Accepté - rétroactif
- **Chantier** : #789/#790 (affordance et validation), socle occupation #1014, notificateur #1405
- **Vérification** : probable - `scripts/adr/0010-dialogue-hors-port.py` (cliquet : 4)

## Contexte

Certains gestes exigent une **interaction bloquante** : confirmer une action destructrice, afficher un compte rendu que l'utilisateur doit acquitter. La voie évidente - `Alert.showAndWait()` appelé en dur dans un contrôleur - a deux défauts rédhibitoires :

1. elle **fige les tests** : `showAndWait()` bloque le fil JavaFX, et un test TestFX/E2E headless reste **gelé** en l'attendant ;
2. elle rend le geste **intestable** : impossible de vérifier « a-t-on bien demandé confirmation avant de supprimer ? » sans piloter une vraie fenêtre.

## Décision

Un dialogue bloquant est un **port injecté**, jamais un appel direct :

- **`Confirmateur`** (et `ConfirmateurModifiable`) pour les confirmations ;
- **`Notificateur`** (et `NotificateurModifiable`) pour les comptes rendus.

En production, l'implémentation ouvre l'`Alert`. En test, on injecte un double qui **répond** (oui/non) ou **enregistre** l'appel, sans ouvrir de fenêtre.

## Conséquences

- Les tests vérifient le **contrat** (« confirmation demandée avant l'action », « compte rendu émis avec tel message ») sans geler, et sans dépendre du rendu.
- ⚠️ **Un port ne vaut que s'il est unique.** Précision ajoutée le 2026-08-05 (#3335) : la substitution repose sur le fait que le code testé et le test désignent **la même instance**. Depuis que les écrans se découpent en sous-vues ([ADR 2745](2745-une-vue-riche-se-decoupe-en-sous-vues.md)), une sous-vue qui fabriquerait son propre porteur ferait porter le double du test parent sur un autre objet : le `showAndWait()` figerait le test headless, ou celui-ci passerait en ne vérifiant rien. Une sous-vue **reçoit** donc le porteur de son parent. Gardé par `DecisionsRespecteesTest#une_sous_vue_ne_se_procure_pas_ce_qui_doit_etre_unique`.
- Le même port sert le socle d'occupation (#1014) : voile de fenêtre, opération critique (#906), confirmations - tout passe par des collaborateurs injectables.
- Coût : un port de plus à câbler par surface interactive. C'est le prix de la testabilité, et il est modeste.

## Alternatives écartées

- **`Alert.showAndWait()` en dur.** Gèle les tests headless et rend le geste invérifiable - la raison même de cette décision.
- **Tester en pilotant la vraie fenêtre.** Fragile, lent, et impossible en headless ; on ne teste plus la logique mais la plomberie JavaFX.
