# P4 - Préparer le dépôt 📦

[← Retour au sommaire des parcours](index.md) · **Section B - Chaîne de production**

> **Persona principal** : Marie / Karim / Samuel. **Objectifs qualité visés** : [O7 Intégrité](../../Objectifs%20qualités/Objectifs%20qualités/O7.md), [O8 Confidentialité](../../Objectifs%20qualités/Objectifs%20qualités/O8.md).

Marie a importé et vérifié une nuit (parcours [P2](P2%20-%20Importer%20une%20nuit%20d%27enregistrement.md) et [P3](P3%20-%20Vérifier%20l%27enregistrement%20par%20échantillonnage.md)). Elle veut maintenant **préparer le dépôt de cette nuit sur le portail Vigie-Chiro** et l'y téléverser manuellement.

1. Marie sélectionne un passage `OK` ou `Utilisable` (un passage `Inexploitable` est bloqué, R14).
2. Elle clique sur « **Vérifier et préparer le dépôt** ». L'application vérifie la cohérence du passage :
    - tous les enregistrements originaux ont-ils bien été transformés en séquences d'écoute ?
    - le préfixe `CarXXXXXX-AAAA-PassN-YY-` est-il présent et conforme sur tous les fichiers (R6, R7, R8) ?
    - le journal du capteur et le relevé climatique sont-ils présents ?
3. L'application affiche un **récapitulatif du dépôt** : nombre de séquences d'écoute, taille totale, chemin du dossier prêt sur le disque. Un bouton « **Ouvrir le dossier dans l'explorateur** » permet à Marie de retrouver les fichiers immédiatement.
4. Marie ouvre le dossier, sélectionne toutes les séquences et les téléverse manuellement sur <https://vigiechiro.herokuapp.com/> via son navigateur (l'application **ne dialogue pas avec le portail**, c'est un dépôt manuel - R8 implicite).
5. Une fois le téléversement effectué côté Vigie-Chiro, Marie revient dans l'application et clique sur « **J'ai déposé** » pour marquer la date de dépôt. Le passage passe au statut `Déposé`.
6. Marie attend ensuite 24-48 h le retour Tadarida pour entamer le parcours [P7](P7%20-%20Valider%20les%20résultats%20Tadarida.md).

## Enrichissements prévus

> Ces évolutions sont **décidées et maquettées, pas encore livrées**. Elles prolongent ce parcours sans en modifier les étapes actuelles.

- **Le téléversement encaisse une coupure.** Une interruption réseau momentanée fait aujourd'hui échouer l'unité en cours, que l'utilisateur doit relancer à la main. Un réessai gradué et un découpage du téléversement en parts réessayables suppriment ce geste pour un incident qui n'en mérite aucun (#2354).
- **La fin de dépôt rend des comptes en chiffres**, sur le même patron que l'import : voir [M-CompteRendu](../Maquettes/M-CompteRendu.md) (#2358).
