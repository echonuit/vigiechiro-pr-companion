# Glossaire

Les termes du domaine employés dans cette documentation et dans l'application.

| Terme | Définition |
|---|---|
| **Carré** | Maille de suivi Vigie-Chiro, identifiée par un numéro à six chiffres. Dans l'application, un carré et ses points d'écoute forment un [site](ecrans/sites.md). |
| **Dépôt** | Envoi des données d'une nuit vérifiée sur la plateforme Vigie-Chiro. L'application **téléverse directement** (envoi reprenable) puis lance l'analyse ; le dépôt navigateur reste un **repli** hors connexion. Voir l'écran [Préparer le dépôt](ecrans/lot.md). |
| **Importer** | Faire **entrer des données dans l'application**, quelle qu'en soit la provenance : une carte SD, un fichier CSV, ou les résultats d'une participation sur Vigie-Chiro. C'est le verbe employé quand ce qui arrive **n'existait pas encore** sur votre poste. À distinguer de **Récupérer**. |
| **Journal du capteur** | Fichier d'évènements produit par l'enregistreur (démarrage, arrêt programmé...), exploité notamment par le [diagnostic](ecrans/diagnostic.md). |
| **Passage** | Une **nuit d'enregistrement** sur un point d'écoute, identifiée par une année et un numéro de passage. C'est l'unité de travail de l'application. Voir l'écran [Passage](ecrans/passage.md). |
| **Passage archivé** | Passage dont l'**audio n'est plus là où l'application l'attendait** : vous l'avez rangé ailleurs, effacé, ou son support n'est pas branché. C'est un **constat**, pas un geste - l'application ne supprime jamais vos fichiers. Ses observations et ses validations restent **consultables**, mais on ne peut plus l'**écouter**. Il **redevient actif** de lui-même si les fichiers reparaissent au même endroit, ou par une « réactivation » s'ils ont bougé. Voir l'écran [Passage](ecrans/passage.md). |
| **Passive Recorder (PR)** | Enregistreur autonome posé sur le terrain, qui capte les ultrasons des chauves-souris pendant une nuit entière. Le « PR » du nom de l'application. |
| **Point d'écoute** | Emplacement précis d'enregistrement dans un carré, identifié par un code (une lettre majuscule + un ou plusieurs chiffres, par exemple `A1` ou `Z41`). |
| **Préfixe** | Nommage normalisé appliqué aux fichiers à l'import, de la forme `CarXXXXXX-AAAA-PassN-YY-`, pour rendre chaque fichier identifiable sans ambiguïté. |
| **Relevé climatique** | Mesures de température et d'hygrométrie de la nuit, affichées sous forme de courbe dans le [diagnostic](ecrans/diagnostic.md). |
| **Réactivation** | Remise en place de l'audio d'un **passage archivé** à partir d'un dossier que vous désignez. Chaque fichier est **vérifié** avant d'être rebranché : un fichier homonyme au contenu différent est refusé, jamais rebranché en silence. L'application demande ensuite si elle doit **copier** ces fichiers ou les **laisser où ils sont** et s'y référer. |
| **Récupérer** | Rapatrier **depuis Vigie-Chiro** ce que l'application connaît déjà, pour le compléter ou le rafraîchir : vos sites et points, la météo et le matériel d'une nuit, les identifiants de ses observations. Rien n'est jamais écrasé chez vous sans que ce soit dit. Le geste inverse s'appelle **Envoyer vers Vigie-Chiro**. |
| **Séquence** | Extrait d'écoute de 5 secondes, **ralenti dix fois** (les ultrasons deviennent audibles), produit lors de la transformation des enregistrements bruts. |
| **Site** | Dans l'application, un carré de suivi et ses points d'écoute. Voir l'écran [Sites](ecrans/sites.md). |
| **Sonogramme** | Représentation visuelle de l'amplitude du son au fil du temps. |
| **Spectrogramme** | Représentation visuelle des fréquences du son au fil du temps (clair = forte intensité). |
| **Statut** | Étape d'avancement d'un passage dans le traitement : Importé, Transformé, Vérifié, Prêt à déposer, Dépôt en cours, Déposé. |
| **Tadarida** | Outil d'identification **automatique** des espèces à partir des séquences. Après le dépôt, Vigie-Chiro renvoie ses résultats (un fichier CSV), relus dans l'écran [Validation](ecrans/validation.md). |
| **Verdict** | Jugement de qualité porté sur une nuit à la vérification : **OK**, **Utilisable** ou **Inexploitable** (état initial **Non vérifié**), dérivé du verdict de chaque fichier son écouté. |
| **Vigie-Chiro** | Programme national de suivi des chauves-souris et sa plateforme web, où les nuits sont déposées et où Tadarida restitue les identifications. |
