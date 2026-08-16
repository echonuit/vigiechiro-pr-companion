# P4 - Préparer le dépôt 📦

[← Retour au sommaire des parcours](index.md) · **Section B - Chaîne de production**

> **Persona principal** : Marie / Karim / Samuel. **Objectifs qualité visés** : [O7 Intégrité](../../Objectifs%20qualités/Objectifs%20qualités/O7.md), [O8 Confidentialité](../../Objectifs%20qualités/Objectifs%20qualités/O8.md).

Marie a importé et vérifié une nuit (parcours [P2](P2%20-%20Importer%20une%20nuit%20d%27enregistrement.md) et [P3](P3%20-%20Vérifier%20l%27enregistrement%20par%20échantillonnage.md)). Elle veut maintenant **préparer le dépôt de cette nuit et le déposer sur Vigie-Chiro** - directement depuis l'application quand elle est connectée, ou par un repli navigateur sinon.

1. Marie sélectionne un passage `OK` ou `Utilisable` (un passage `Inexploitable` est bloqué, R14).
2. Elle clique sur « **Vérifier et préparer le dépôt** ». L'application vérifie la cohérence du passage :
    - tous les enregistrements originaux ont-ils bien été transformés en séquences d'écoute ?
    - le préfixe `CarXXXXXX-AAAA-PassN-YY-` est-il présent et conforme sur tous les fichiers (R6, R7, R8) ?
    - le journal du capteur et le relevé climatique sont-ils présents ?
3. L'application affiche un **récapitulatif du dépôt** : nombre de séquences d'écoute, taille totale, forme du dépôt (archives ZIP ou séquences WAV).
4. **Connectée à Vigie-Chiro**, Marie **téléverse directement depuis l'application** : celle-ci crée la participation, envoie les séquences au bon format, et **reprend là où elle s'est arrêtée** si la connexion coupe. Le passage passe à `Déposé` une fois **tout** en ligne. Puis Marie **lance la participation**, ce qui déclenche l'analyse Tadarida côté serveur.
5. **Sans connexion**, un **repli** reste possible : « Ouvrir le dossier » puis dépôt depuis le navigateur sur <https://vigiechiro.herokuapp.com/>, suivi de « **Marquer déposé** » pour tracer la date à la main.
6. Marie attend ensuite 24-48 h le retour Tadarida pour entamer le parcours [P7](P7%20-%20Valider%20les%20résultats%20Tadarida.md).

## Ce que le dépôt encaisse, et ce qu'il rend

Les deux enrichissements annoncés ici sont **livrés** (EPIC #2350).

- **Le téléversement encaisse une coupure.** Une interruption réseau momentanée faisait échouer l'unité en cours, que l'utilisateur devait relancer à la main. Le réessai est désormais **gradué par intention** - on insiste quand quelqu'un attend, on renonce vite sur un sondage - avec un aléa sur la temporisation et le respect de l'en-tête `Retry-After` ; au-dessus d'un seuil, le téléversement est découpé en parts réessayables une à une. Le geste humain a disparu pour un incident qui ne le méritait pas (#2354).
- **La fin de dépôt rend des comptes en chiffres**. La question laissée ouverte par la clôture de l'EPIC #2350 - la bande et la table diraient en partie la même chose - est tranchée par un **partage** : la table garde le **détail par fichier**, la bande porte le **verdict**, les **proportions**, le **volume téléversé** (que rien ne disait) et l'**action suivante**. Le bandeau d'une ligne a disparu pour le succès d'un dépôt. Trois fins sont distinguées, et la troisième est celle qui piégeait : un dépôt **interrompu** a une tentative sans échec et des archives manquantes, donc une part « Restantes » sans laquelle la barre serait pleine et verte. Voir [M-CompteRendu](../Maquettes/M-CompteRendu.md).
