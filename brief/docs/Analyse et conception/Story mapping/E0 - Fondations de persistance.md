# E0 - 🗄️ Fondations de persistance

[← Retour au sommaire story mapping](index.md) · **Épopée transverse** socle (S1-S5) (S6-S7) (S8)

**Portée** : tout le travail base de données (schéma SQLite, DAO, persistance des entités cœur, mécanismes de reprise sur erreur). Cette épopée n'est rattachée à **aucun parcours** spécifique car elle **sert toutes les autres épopées** : sans elle, aucune story métier n'est livrable.

**Justification du regroupement** : la couche de persistance (JDBC + SQLite, cf. [Contraintes techniques](../../Contraintes%20techniques.md)) est un livrable technique imposé par le brief. La regrouper comme épopée identifiée la rend visible, arbitrable, et donne un point d'entrée clair pour la partie persistance de l'application.

## E0.S1 - Initialiser le schéma SQLite et les DAO génériques { #e0s1 }

**En tant que** développeur de l'application

**Je veux** disposer d'un schéma SQLite initialisé au premier lancement et de classes DAO génériques

**Afin de** poser les fondations techniques sur lesquelles toutes les autres stories vont s'appuyer

**Critères d'acceptation** :

- [ ] Au premier lancement, l'application crée un fichier `companion.db` dans le dossier de travail utilisateur si absent.
- [ ] Le schéma initial contient toutes les tables vides correspondant aux entités du [modèle conceptuel](../Modèle%20conceptuel/index.md) (Utilisateur, Site, Point, Passage, Session d'enregistrement, EnregistrementOriginal, SéquenceDÉcoute, JournalCapteur, RelevéClimatique, SélectionDÉcoute, RésultatsIdentification, Observation, Taxon, GroupeTaxonomique).
- [ ] Une classe `DaoGenerique<T>` ou équivalent fournit les opérations CRUD de base (`create`, `findById`, `findAll`, `update`, `delete`).
- [ ] La connexion JDBC est gérée avec un pool ou un singleton thread-safe.
- [ ] Un test d'intégration crée la BD, exécute une opération CRUD, et vérifie le résultat.

**Parcours rattaché** : aucun (transverse)<br>
**Maquettes cibles** : aucune<br>
**Dépendances** : aucune<br>

---

## E0.S2 - Persister les sites de suivi et points d'écoute { #e0s2 }

**En tant que** développeur

**Je veux** des DAO opérationnels pour les entités `Site` et `Point d'écoute`

**Afin que** l'épopée [E1](E1%20-%20Gérer%20ses%20sites%20et%20points%20de%20suivi.md) puisse créer, lire, mettre à jour et supprimer des sites et points

**Critères d'acceptation** :

- [ ] `SiteDao` permet de créer un site avec son n° de carré, son nom convivial, son protocole.
- [ ] `PointEcouteDao` permet d'ajouter, modifier, supprimer des points sur un site existant.
- [ ] Un site supprimé entraîne la suppression en cascade de ses points (ou refus si des passages y sont rattachés - à arbitrer).
- [ ] Les contraintes d'unicité sont vérifiées (n° de carré unique, code de point unique par site).
- [ ] Tests d'intégration sur les opérations CRUD.

**Parcours rattaché** : sert [P1](../Parcours%20utilisateurs/P1%20-%20Déclarer%20un%20site%20de%20suivi.md)<br>
**Maquettes cibles** : aucune (DAO pur)<br>
**Dépendances** : [E0.S1](#e0s1)<br>

---

## E0.S3 - Persister les passages avec leurs statuts d'avancement { #e0s3 }

**En tant que** développeur

**Je veux** des DAO opérationnels pour l'entité `Passage` et ses statuts

**Afin que** les épopées [E2](index.md) (Import), [E3](index.md) (Vérification) et [E4](index.md) (Dépôt) puissent suivre l'avancement d'une nuit dans le cycle

**Critères d'acceptation** :

- [ ] `PassageDao` permet de créer un passage rattaché à un point d'écoute, une année, un n° de passage.
- [ ] L'unicité du quadruplet `(carré, année, n° passage, point)` est garantie au niveau BD (contrainte unique).
- [ ] Le statut d'avancement est persisté (`Importé`, `Transformé`, `Vérifié`, `Prêt à déposer`, `Déposé`).
- [ ] Le verdict de vérification est persisté (`OK`, `Utilisable`, `Inexploitable`, ou null si non vérifié).
- [ ] L'association `Enregistreur ↔ Site/Point` (mémorisée pour faciliter les imports suivants) est persistée.
- [ ] Tests d'intégration couvrant la création, la transition de statut et le verdict.

**Parcours rattaché** : sert [P2](../Parcours%20utilisateurs/P2%20-%20Importer%20une%20nuit%20d%27enregistrement.md), [P3](../Parcours%20utilisateurs/P3%20-%20Vérifier%20l%27enregistrement%20par%20échantillonnage.md), [P4](../Parcours%20utilisateurs/P4%20-%20Préparer%20un%20lot%20prêt%20à%20déposer.md)<br>
**Maquettes cibles** : aucune (DAO pur)<br>
**Dépendances** : [E0.S1](#e0s1), [E0.S2](#e0s2)<br>

---

## E0.S4 - Persister les sélections d'écoute et leurs séquences { #e0s4 }

**En tant que** développeur

**Je veux** des DAO opérationnels pour les entités `Sélection d'écoute` et `Séquence d'écoute`

**Afin que** l'épopée [E3](index.md) (Vérification) puisse mémoriser quelles séquences l'utilisateur a échantillonnées et écoutées

**Critères d'acceptation** :

- [ ] `SequenceDEcouteDao` permet de stocker les métadonnées de chaque chunk produit lors de la transformation (nom de fichier, index, durée, fichier source).
- [ ] `SelectionEcouteDao` permet de stocker une sélection (méthode de constitution, taille, séquences rattachées).
- [ ] Une séquence peut appartenir à 0..N sélections (table de jointure).
- [ ] Le statut « écouté oui/non » par séquence dans le contexte d'une sélection est persisté.
- [ ] Tests d'intégration.

**Parcours rattaché** : sert [P3](../Parcours%20utilisateurs/P3%20-%20Vérifier%20l%27enregistrement%20par%20échantillonnage.md)<br>
**Maquettes cibles** : aucune (DAO pur)<br>
**Dépendances** : [E0.S1](#e0s1), [E0.S3](#e0s3)<br>

---

## E0.S5 - Persister les observations Tadarida importées { #e0s5 }

**En tant que** développeur

**Je veux** des DAO opérationnels pour les entités `Résultats d'identification`, `Observation`, `Taxon` et `Groupe taxonomique`

**Afin que** l'épopée [E7](index.md) (Validation Tadarida) puisse importer un CSV de résultats et le présenter à l'utilisateur pour validation

**Critères d'acceptation** :

- [ ] `ResultatsIdentificationDao` permet de créer un import (chemin du CSV source, format détecté, date d'import) rattaché à un passage.
- [ ] `ObservationDao` permet d'insérer en masse des observations (volumétrie potentielle : 4000+ par passage), avec leur taxon Tadarida, probabilité, séquence rattachée, statut de validation, taxon observateur, commentaire.
- [ ] `TaxonDao` et `GroupeTaxonomiqueDao` permettent de gérer un référentiel des taxons (peuplé une fois pour toutes ou rafraîchi via un fichier de référence).
- [ ] Insertion en masse (bulk insert) optimisée pour ne pas freezer l'IHM (cf. [O3](../../Objectifs%20qualités/Objectifs%20qualités/O3.md)).
- [ ] Tests d'intégration avec un jeu de données représentatif (au moins 1000 observations).

**Parcours rattaché** : sert [P7](../Parcours%20utilisateurs/P7%20-%20Valider%20les%20résultats%20Tadarida.md)<br>
**Maquettes cibles** : aucune (DAO pur)<br>
**Dépendances** : [E0.S1](#e0s1), [E0.S3](#e0s3)<br>

---

## E0.S6 - Reprendre un import interrompu { #e0s6 }

**En tant que** [Marie](../Personas/Marie.md)

**Je veux** que mon import de nuit, s'il est interrompu (crash, fermeture inopinée, batterie à plat), puisse reprendre là où il s'est arrêté

**Afin de** ne pas avoir à tout recommencer si quelque chose se passe mal

**Critères d'acceptation** :

- [ ] L'import [P2](../Parcours%20utilisateurs/P2%20-%20Importer%20une%20nuit%20d%27enregistrement.md) écrit son état d'avancement en BD (file d'attente persistante : fichiers à copier, fichiers copiés, fichiers transformés).
- [ ] Au redémarrage de l'application, si un import était en cours, l'utilisateur est notifié et peut choisir de reprendre, abandonner, ou repartir de zéro.
- [ ] Les fichiers déjà copiés ne sont pas recopiés.
- [ ] Les fichiers déjà transformés ne sont pas re-transformés.
- [ ] Le statut d'avancement du passage reste cohérent (`En cours` jusqu'à complétion, puis `Transformé`).
- [ ] Test d'intégration simulant une interruption à différents moments du pipeline.

**Parcours rattaché** : transverse à [P2](../Parcours%20utilisateurs/P2%20-%20Importer%20une%20nuit%20d%27enregistrement.md)<br>
**Maquettes cibles** : [M-Import](../Maquettes/M-Import.md) (modale d'import doit afficher la reprise éventuelle)<br>
**Dépendances** : [E0.S1](#e0s1), [E0.S3](#e0s3), E2 (l'import lui-même)<br>

---

## E0.S7 - Reprendre une validation Tadarida en suspens { #e0s7 }

**En tant que** [Marie](../Personas/Marie.md) ou [Samuel](../Personas/Samuel.md)

**Je veux** que ma session de validation Tadarida ([P7](../Parcours%20utilisateurs/P7%20-%20Valider%20les%20résultats%20Tadarida.md)), si je la quitte avant la fin, puisse être reprise plus tard exactement là où je l'avais laissée

**Afin de** pouvoir étaler la validation sur plusieurs jours sans rien perdre

**Critères d'acceptation** :

- [ ] Le contexte de validation est persisté en BD : dernière observation vue, filtres actifs (taxon, groupe, seuil de probabilité, plage horaire), mode de validation choisi (inventaire/activité).
- [ ] Au retour sur l'écran de validation pour le même passage, le contexte est restauré automatiquement.
- [ ] Les observations déjà validées ou corrigées sont persistées et conservent leur statut.
- [ ] Test d'intégration : validation partielle, fermeture, réouverture, vérification de la restauration.

**Parcours rattaché** : transverse à [P7](../Parcours%20utilisateurs/P7%20-%20Valider%20les%20résultats%20Tadarida.md)<br>
**Maquettes cibles** : [M-SonsValidation](../Maquettes/M-SonsValidation.md) (la vue de validation doit indiquer si une session est restaurée)<br>
**Dépendances** : [E0.S1](#e0s1), [E0.S5](#e0s5), E7 (la validation elle-même)<br>

---

## E0.S8 - Versionner et migrer le schéma de BD { #e0s8 }

**En tant que** mainteneur de l'application

**Je veux** un mécanisme de versionning du schéma SQLite et de migration entre versions

**Afin de** pouvoir faire évoluer le schéma sans casser les BDs existantes des utilisateurs

**Critères d'acceptation** :

- [ ] Une table `schema_version` mémorise la version courante du schéma.
- [ ] Au démarrage, l'application compare la version code avec la version BD et applique les scripts de migration nécessaires.
- [ ] Les scripts de migration sont versionnés dans les sources (ex. `db/migrations/V01__init.sql`, `V02__add_observations.sql`).
- [ ] Une migration ratée laisse la BD dans son état initial (rollback) et bloque l'application avec un message clair.
- [ ] Test d'intégration : créer une BD en V01, lancer l'application qui fait passer en V02, vérifier la cohérence.

**Parcours rattaché** : transverse (technique pur)<br>
**Maquettes cibles** : aucune<br>
**Dépendances** : [E0.S1](#e0s1)<br>
