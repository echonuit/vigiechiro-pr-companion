# Glossaire

Les termes du domaine employés dans cette documentation et dans l'application.

| Terme | Définition |
|---|---|
| **Carré** | Maille de suivi Vigie-Chiro, identifiée par un numéro à six chiffres. Dans l'application, un carré et ses points d'écoute forment un [site](ecrans/sites.md). |
| **Dépôt** | Envoi des données d'une nuit vérifiée sur la plateforme Vigie-Chiro. Le téléversement est **manuel** (l'application prépare le dossier mais ne dialogue pas avec le portail). Voir l'écran [Lot](ecrans/lot.md). |
| **Journal du capteur** | Fichier d'évènements produit par l'enregistreur (démarrage, arrêt programmé...), exploité notamment par le [diagnostic](ecrans/diagnostic.md). |
| **Lot** | Ensemble préparé des fichiers d'une nuit vérifiée, prêt à être déposé sur Vigie-Chiro. |
| **Passage** | Une **nuit d'enregistrement** sur un point d'écoute, identifiée par une année et un numéro de passage. C'est l'unité de travail de l'application. Voir l'écran [Passage](ecrans/passage.md). |
| **Passive Recorder (PR)** | Enregistreur autonome posé sur le terrain, qui capte les ultrasons des chauves-souris pendant une nuit entière. Le « PR » du nom de l'application. |
| **Point d'écoute** | Emplacement précis d'enregistrement dans un carré, identifié par un code (lettre + chiffre, par exemple `A1`). |
| **Préfixe** | Nommage normalisé appliqué aux fichiers à l'import, de la forme `CarXXXXXX-AAAA-PassN-YY-`, pour rendre chaque fichier identifiable sans ambiguïté. |
| **Relevé climatique** | Mesures de température et d'hygrométrie de la nuit, affichées sous forme de courbe dans le [diagnostic](ecrans/diagnostic.md). |
| **Séquence** | Extrait d'écoute de 5 secondes, **ralenti dix fois** (les ultrasons deviennent audibles), produit lors de la transformation des enregistrements bruts. |
| **Site** | Dans l'application, un carré de suivi et ses points d'écoute. Voir l'écran [Sites](ecrans/sites.md). |
| **Sonogramme** | Représentation visuelle de l'amplitude du son au fil du temps. |
| **Spectrogramme** | Représentation visuelle des fréquences du son au fil du temps (clair = forte intensité). |
| **Statut** | Étape d'avancement d'un passage dans le traitement : Importé, Transformé, Vérifié, Prêt à déposer, Déposé. |
| **Tadarida** | Outil d'identification **automatique** des espèces à partir des séquences. Après le dépôt, Vigie-Chiro renvoie ses résultats (un fichier CSV), relus dans l'écran [Validation](ecrans/validation.md). |
| **Verdict** | Jugement de qualité porté sur une nuit lors de la vérification : **OK**, **Douteux** ou **À jeter**. |
| **Vigie-Chiro** | Programme national de suivi des chauves-souris et sa plateforme web, où les nuits sont déposées et où Tadarida restitue les identifications. |
