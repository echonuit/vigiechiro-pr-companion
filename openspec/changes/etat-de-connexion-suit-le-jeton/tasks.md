## 1. Rouge

- [x] 1.1 `JetonPerimeOuRefuseViewTest` : les trois écrans, jeton périmé et `401`, modale rouverte, dernier jour valide
- [x] 1.2 `DepotApresUnJetonRefuseTest` : dix unités, la première reçoit le `401`, toutes se réarment et le compte rendu dit « Reconnectez-vous »

## 2. Code

- [x] 2.1 Le transport signale un `401` de l'API à la source du jeton ; `StockageConnexion.oublierSi` ; la liaison de production
- [x] 2.2 `EtatConnexion.relire()`, appelé au retour des gestes des trois écrans et à l'ouverture de la modale
- [x] 2.3 `CauseRefus` range « non connecté » en droits
- [x] 2.4 Les messages nomment la reconnexion et l'endroit

## 3. Ce qui montre et ce qui dit

- [ ] 3.1 `docs/connexion-vigiechiro.md`, `docs/ecrans/sites.md`, `dev-docs/patterns.md`
- [ ] 3.2 Les captures qui montrent un de ces messages ou boutons, s'il y en a

## 4. Vérification

- [ ] 4.1 Mutations vues rouges : signal du transport retiré ou élargi, oubli rendu inconditionnel, liaison sans oubli, classement du dépôt rendu, relecture retirée de la modale et de chaque écran, messages rendus à l'ancien
