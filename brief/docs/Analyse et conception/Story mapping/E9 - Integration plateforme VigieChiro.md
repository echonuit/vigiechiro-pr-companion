# E9 - ☁️ Intégration plateforme VigieChiro

[← Retour au sommaire story mapping](index.md) · **Parcours principaux** : [P4 - Préparer le dépôt](../Parcours%20utilisateurs/P4%20-%20Preparer%20un%20lot%20pret%20a%20deposer.md), [P7 - Valider les résultats Tadarida](../Parcours%20utilisateurs/P7%20-%20Valider%20les%20resultats%20Tadarida.md), [P12 - Récupérer une nuit déposée](../Parcours%20utilisateurs/P12%20-%20Recuperer%20une%20nuit%20deposee%20sur%20VigieChiro.md)

**Portée** : tous les échanges avec le serveur Vigie-Chiro : se connecter, déposer les données directement par l'API, lancer et suivre le calcul Tadarida côté serveur, publier les corrections de validation, et reconstruire une nuit déjà déposée mais absente de la machine.

**Pourquoi une épopée à part** : cette intégration est arrivée **après** le découpage initial E1 à E8 (le dépôt était d'abord manuel, par le navigateur). Elle forme un ensemble cohérent autour d'un même serveur, d'un même jeton et d'un même **ancrage plateforme** (`vigiechiro_link`) ; la disperser dans les épopées existantes la rendrait invisible.

**Persona principal** : [Marie](../Personas/Marie.md) (connexion, dépôt, publication d'une nuit) puis [Karim](../Personas/Karim.md) et [Samuel](../Personas/Samuel.md) (volume, reconstruction multi-postes).

**Pré-requis** : [E0](E0%20-%20Fondations%20de%20persistance.md) (persistance), [E4](E4%20-%20Preparer%20et%20tracer%20le%20depot%20VigieChiro.md) (dépôt préparé), [E7](E7%20-%20Valider%20les%20resultats%20Tadarida.md) (validation, pour la publication des corrections).

**Note transverse** : toutes les actions de cette épopée sont **réseau et bloquantes**, exécutées **hors du fil JavaFX**, avec **progression déterminée et annulation**, et se **retirent proprement** quand l'utilisateur n'est pas connecté (aucune action qui échoue faute de réseau).

---

## E9.S1 - Se connecter à Vigie-Chiro avec un jeton personnel { #e9s1 }

**En tant que** [Marie](../Personas/Marie.md)

**Je veux** relier l'application à mon compte Vigie-Chiro une fois pour toutes

**Afin de** déposer mes nuits et récupérer les résultats sans ressaisir mes identifiants à chaque fois

**Critères d'acceptation** :

- [ ] Une action « Se connecter à Vigie-Chiro » (menu principal (☰)) accepte un **jeton d'API personnel**.
- [ ] Le jeton est **vérifié** par un appel serveur (`GET /moi`) ; en cas de succès, l'identité (login, profil) est mémorisée.
- [ ] Le jeton est stocké **localement** dans le workspace, avec des **permissions restreintes** (600) et **hors du dépôt Git**.
- [ ] Le jeton **expire au bout de 14 jours** : passé ce délai, l'application se considère déconnectée **sans appel réseau**.
- [ ] Trois états sont distingués et affichés : **connecté**, **« jeton enregistré mais non vérifié »** (serveur injoignable), **non connecté**.
- [ ] Un serveur **injoignable ne déconnecte pas** : le jeton est conservé et re-vérifié à la prochaine ouverture.
- [ ] La connexion réussie **amorce** la synchronisation des référentiels (taxons, puis sites, puis passages) avec un résumé chiffré (« 385 taxons, 3 sites »).
- [ ] Une action « Se déconnecter » **efface** le jeton (idempotente).

**Parcours rattaché** : prérequis transverse de [P4](../Parcours%20utilisateurs/P4%20-%20Preparer%20un%20lot%20pret%20a%20deposer.md) et [P12](../Parcours%20utilisateurs/P12%20-%20Recuperer%20une%20nuit%20deposee%20sur%20VigieChiro.md)<br>
**Maquettes cibles** : *écran de connexion non maquetté* (cf. [#2382](https://github.com/echonuit/vigiechiro-pr-companion/issues/2382))<br>
**Dépendances** : [E0.S1](E0%20-%20Fondations%20de%20persistance.md#e0s1)<br>

---

## E9.S2 - Déposer directement sur l'API, en flux, reprenable et annulable { #e9s2 }

**En tant que** [Samuel](../Personas/Samuel.md) (qui dépose des nuits lourdes depuis un réseau qui peut couper)

**Je veux** téléverser mes unités de dépôt directement depuis l'application, sans passer par le navigateur

**Afin de** déposer un gros volume d'un seul geste, et reprendre sans tout recommencer si la connexion coupe

**Critères d'acceptation** :

- [ ] Depuis la préparation du dépôt ([M-Lot](../Maquettes/M-Lot.md)), une action **« Déposer »** téléverse les unités (archives ZIP ou fichiers WAV) **directement sur l'API**.
- [ ] Le dépôt est **repris unité par unité** : après une coupure, seule(s) l'(les) unité(s) manquante(s) sont re-téléversée(s), les unités déjà en ligne sont conservées.
- [ ] Le téléversement est **parallèle** (au plus **5 unités** simultanées, comme le front web) et peut être **annulé** à tout moment ; une unité non commencée reste « à déposer ».
- [ ] Le passage passe à **`Dépôt en cours`** dès la première unité, et à **`Déposé`** **seulement** quand **toutes** les unités sont en ligne (jamais sur un dépôt partiel).
- [ ] Le flux est **streamé** depuis le disque : une archive de ~700 Mo n'est pas chargée en mémoire.
- [ ] Une reprise est **refusée** si la liste des séquences a changé depuis le début du dépôt (garde « lot inchangé »), et un **pré-vol** vérifie que la participation correspond bien au point et à la nuit.
- [ ] Un **repli manuel** reste possible : ouvrir le sous-dossier `depot/` et téléverser via le navigateur.

**Parcours rattaché** : [P4](../Parcours%20utilisateurs/P4%20-%20Preparer%20un%20lot%20pret%20a%20deposer.md), étape « Déposer »<br>
**Maquettes cibles** : [M-Lot](../Maquettes/M-Lot.md) (étape de téléversement)<br>
**Dépendances** : [E9.S1](#e9s1), [E4.S2](E4%20-%20Preparer%20et%20tracer%20le%20depot%20VigieChiro.md#e4s2)<br>

---

## E9.S3 - Lancer et suivre le traitement Tadarida côté serveur { #e9s3 }

**En tant que** [Marie](../Personas/Marie.md)

**Je veux** déclencher le calcul d'identification sur le serveur après mon dépôt et savoir où il en est

**Afin de** savoir quand les résultats Tadarida seront prêts à être importés

**Critères d'acceptation** :

- [ ] Une fois le passage déposé, une action **« Lancer la participation »** déclenche le calcul Tadarida côté serveur.
- [ ] L'application **relève et affiche l'état** du traitement serveur : **planifié / en cours / fini / erreur / à relancer**.
- [ ] Cet état est **observé, pas piloté** : il est **distinct** du statut d'avancement local, et **peut régresser** (une relance ramène « fini » à « planifié »).
- [ ] Les résultats ne sont **récupérables que lorsque le traitement est `fini`** (avant, la liste renvoyée par le serveur est vide).
- [ ] Une **relance destructrice est bloquée** localement quand un calcul existe déjà, **sauf confirmation explicite** : recalculer supprime les données serveur, et **en dépôt ZIP l'audio est définitivement perdu**.
- [ ] Un état serveur **illisible** ne déclenche **pas** de relance sans confirmation (fail-safe).
- [ ] Le dernier état relevé est **mémorisé** (cache) et reste consultable **hors connexion**.

**Parcours rattaché** : [P4](../Parcours%20utilisateurs/P4%20-%20Preparer%20un%20lot%20pret%20a%20deposer.md) (fin) → [P7](../Parcours%20utilisateurs/P7%20-%20Valider%20les%20resultats%20Tadarida.md)<br>
**Maquettes cibles** : [M-Lot](../Maquettes/M-Lot.md) (étape « Lancer la participation »)<br>
**Dépendances** : [E9.S2](#e9s2)<br>

---

## E9.S4 - Publier les corrections de validation vers la plateforme { #e9s4 }

**En tant que** [Karim](../Personas/Karim.md)

**Je veux** renvoyer mes corrections de taxon vers Vigie-Chiro une fois ma validation faite

**Afin de** contribuer mes identifications vérifiées au programme national

**Critères d'acceptation** :

- [ ] Une action **« Publier les corrections »** envoie les taxons corrigés via un **PATCH par observation**, ciblant l'**ancrage** (identifiant de donnée + indice de l'observation).
- [ ] Seules sont publiées les observations **complètes** : **taxon observateur + certitude déclarée + ancrage présent + taxon dans le référentiel** ; les autres sont **écartées et comptées par cause** (sans ancrage / sans certitude / hors référentiel).
- [ ] La certitude n'est **jamais devinée** : une observation sans certitude déclarée n'est pas publiée.
- [ ] La publication est **rejouable** (re-pousser la même valeur est sans effet) ; un refus ponctuel **n'interrompt pas** la rafale, et les échecs sont **détaillés** (un `404` invite à réimporter l'ancrage périmé).
- [ ] Une nuit importée par CSV **acquiert d'abord son ancrage** (rapatriement) avant la publication.
- [ ] L'avis du **validateur MNHN** (taxon et certitude du validateur) est **lecture seule** : affiché, **jamais réécrit** vers le serveur.

**Parcours rattaché** : [P7](../Parcours%20utilisateurs/P7%20-%20Valider%20les%20resultats%20Tadarida.md), après la revue<br>
**Maquettes cibles** : [M-SonsValidation](../Maquettes/M-SonsValidation.md) (action plateforme)<br>
**Dépendances** : [E9.S1](#e9s1), [E7](E7%20-%20Valider%20les%20resultats%20Tadarida.md)<br>

---

## E9.S5 - Reconstruire un passage déposé absent de la machine { #e9s5 }

**En tant que** [Karim](../Personas/Karim.md) (qui dépose depuis plusieurs postes, ou réinstalle l'application)

**Je veux** retrouver dans l'application les nuits que j'ai déjà déposées mais qui ne sont pas sur cette machine

**Afin de** consulter et compléter mon suivi sans repartir de zéro

**Critères d'acceptation** :

- [ ] Un écran (depuis [Multisite](../Maquettes/M-MultiSite.md)) liste les nuits **déposées sur la plateforme mais absentes de cette machine** (déposées ailleurs, avant l'app, ou après réinstallation).
- [ ] La reconstruction recrée le passage en **état archivé** : **consultable** (métadonnées, observations rapatriées) mais **pas écoutable** (séquences sans fichier).
- [ ] Les **lacunes sont explicitées** (ce que la plateforme ne restitue pas), jamais masquées.
- [ ] La reconstruction affiche une **progression déterminée avec ETA** et peut être **annulée** (aucun passage créé si annulé).
- [ ] Un import **groupé** reconstruit toutes les nuits manquantes, avec **double progression** (globale « Nuit X/N » + nuit courante) et **comptage des nuits ignorées** (point inconnu, analyse non terminée).
- [ ] L'action **se retire** si l'utilisateur n'est pas connecté ou si l'import plateforme est désactivé.
- [ ] Un passage reconstruit peut ensuite être **réactivé** si ses fichiers d'origine sont retrouvés (cf. [E4](E4%20-%20Preparer%20et%20tracer%20le%20depot%20VigieChiro.md)).

**Parcours rattaché** : [P12](../Parcours%20utilisateurs/P12%20-%20Recuperer%20une%20nuit%20deposee%20sur%20VigieChiro.md)<br>
**Maquettes cibles** : *modale de reconstruction non maquettée* (cf. [#2382](https://github.com/echonuit/vigiechiro-pr-companion/issues/2382))<br>
**Dépendances** : [E9.S1](#e9s1)<br>
