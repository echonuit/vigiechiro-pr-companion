# E7 - ✅ Valider les résultats Tadarida

[← Retour au sommaire story mapping](index.md) · **Parcours principal** : [P7 - Valider les résultats Tadarida](../Parcours%20utilisateurs/P7%20-%20Valider%20les%20résultats%20Tadarida.md) (cible étirable, filet de sécurité au-delà du fil rouge)

**Portée** : tout le travail post-Tadarida - récupérer le CSV de résultats automatiques, le passer en revue espèce par espèce, valider ou corriger chaque classification, exporter le fichier consolidé pour Vigie-Chiro. C'est la **cible étirable principale** : au-delà du fil rouge, c'est elle qui sert de filet de sécurité.

Les gains de productivité avancés (regroupement multi-nuits P9, bibliothèque sons de référence P10) sont sortis dans une épopée distincte [E8](E8%20-%20Productivité%20avancée%20Tadarida.md) pour distinguer nettement le cœur de la validation (E7) de ses gains secondaires (E8).

**Persona principal** : Marie pour la validation simple ; Samuel pour la validation intensive sur grands volumes.

**Pré-requis** : [E0.S5](E0%20-%20Fondations%20de%20persistance.md#e0s5) (DAO observations + taxons), [E0.S7](E0%20-%20Fondations%20de%20persistance.md#e0s7) (reprise de validation en suspens), [E2.S6](E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s6) (séquences d'écoute disponibles), [E4.S3](E4%20-%20Préparer%20et%20tracer%20le%20dépôt%20VigieChiro.md#e4s3) (passage déposé).

## E7.S1 - Importer un CSV de résultats Tadarida et l'associer à un passage { #e7s1 }

**En tant que** [Marie](../Personas/Marie.md)

**Je veux** charger dans l'application le fichier CSV des résultats Tadarida que j'ai téléchargé depuis le portail Vigie-Chiro et l'associer au bon passage

**Afin de** pouvoir ensuite passer en revue les classifications proposées et les valider

**Critères d'acceptation** :

- [ ] Sur la fiche d'un passage au statut `Déposé`, un bouton « Importer les résultats Tadarida » est mis en avant.
- [x] Le clic ouvre un sélecteur de fichier (filtre `.csv`).
- [x] L'application **détecte automatiquement le format** du CSV : `Brut` (sortie brute Tadarida) ou `Vu` (déjà partiellement validé) ([R17](../Modèle%20conceptuel/Règles%20métier.md#r17)).
- [x] Pour chaque ligne du CSV, l'application **associe l'observation à la séquence d'écoute correspondante** par matching du nom de fichier.
- [x] Les observations dont la séquence n'existe pas dans le passage (orphelines) sont signalées dans un récapitulatif post-import (« 12 observations sur 4031 n'ont pas de séquence correspondante - vérifiez la cohérence du dépôt »).
- [x] Si le passage a **déjà** un import Tadarida actif, l'application demande confirmation : remplacer l'import existant (perd les validations en cours) ou annuler.
- [ ] Le passage transitionne au statut `Annoté Tadarida` après import réussi.
- [x] La date d'import et le chemin du CSV source sont persistés ([E0.S5](E0%20-%20Fondations%20de%20persistance.md#e0s5)).
- [ ] Volumétrie cible : un CSV de 4 000+ observations doit s'importer en moins de 10 s sans freezer l'IHM.  *(non verifiable depuis le code)*

**Parcours rattaché** : [P7](../Parcours%20utilisateurs/P7%20-%20Valider%20les%20résultats%20Tadarida.md), étapes 1-3<br>
**Maquettes cibles** : [M-SonsValidation](../Maquettes/M-SonsValidation.md) (zone d'import + récapitulatif post-import)<br>
**Dépendances** : [E0.S5](E0%20-%20Fondations%20de%20persistance.md#e0s5), [E2.S6](E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s6), [E4.S3](E4%20-%20Préparer%20et%20tracer%20le%20dépôt%20VigieChiro.md#e4s3)<br>

---

## E7.S2 - Vue de validation : liste des observations + panneau de détail { #e7s2 }

**En tant que** [Marie](../Personas/Marie.md)

**Je veux** voir toutes les observations importées sous forme de liste à gauche, avec un panneau de détail à droite qui se rafraîchit selon l'observation sélectionnée

**Afin de** pouvoir balayer rapidement les observations et avoir tout le contexte nécessaire (séquence audio, taxon proposé, probabilité) au même endroit pour décider

**Critères d'acceptation** :

- [ ] L'écran de validation est divisé en **deux colonnes** : liste des observations à gauche (tableau triable), panneau de détail à droite.
- [x] Colonnes de la liste : nom du fichier de séquence, taxon Tadarida, probabilité Tadarida, statut de validation (À voir / Validée / Corrigée), commentaire (icône si présent).
- [x] Tri possible par chaque colonne ; tri par défaut : ordre du CSV (souvent chronologique).
- [ ] Le panneau de détail affiche : nom du taxon proposé (avec nom latin et vernaculaire), probabilité Tadarida, fréquence médiane, lecteur audio pour la séquence (réutilise [E3.S3](E3%20-%20Vérifier%20la%20qualité%20d%27enregistrement.md#e3s3)), spectrogramme (cf. [E7.S3](#e7s3)), boutons d'action (cf. [E7.S4](#e7s4)).
- [x] La sélection d'une ligne déclenche le chargement immédiat du détail (idéal sous 200 ms).
- [ ] Raccourcis clavier : `↑/↓` pour naviguer dans la liste, `Espace` pour lecture/pause de la séquence courante.
- [x] Volumétrie cible : la liste reste réactive avec 5 000+ observations (virtualisation TableView).

**Parcours rattaché** : [P7](../Parcours%20utilisateurs/P7%20-%20Valider%20les%20résultats%20Tadarida.md), étapes 3-4<br>
**Maquettes cibles** : [M-SonsValidation](../Maquettes/M-SonsValidation.md) (patron liste + écoute)<br>
**Dépendances** : [E3.S3](E3%20-%20Vérifier%20la%20qualité%20d%27enregistrement.md#e3s3), [E7.S1](#e7s1)<br>

---

## E7.S3 - Intégrer le composant de vue audio (sonogramme + spectrogramme) { #e7s3 }

**En tant que** [Marie](../Personas/Marie.md) ou [Samuel](../Personas/Samuel.md)

**Je veux** voir un sonogramme et un spectrogramme synchronisés de la séquence courante, avec possibilité de zoomer sur le spectrogramme

**Afin de** distinguer les caractéristiques discriminantes d'un cri (forme, fréquence dominante, harmoniques) qui complètent l'écoute pour prendre une décision de classification éclairée

!!! info "Composant fourni"
    Le composant de vue audio (sonogramme + spectrogramme avec zoom) est une **dépendance externe** (`audio-view`, JitPack) : le calcul FFT et le rendu du spectrogramme y sont déjà réalisés. Cette story ne porte que l'**intégration** : instancier le composant, le lier au cycle de lecture audio, synchroniser le curseur avec le lecteur. Le même composant sert dans [E3.S3](E3%20-%20Vérifier%20la%20qualité%20d%27enregistrement.md#e3s3) (M-Qualification).

**Critères d'acceptation** :

- [x] Le panneau de détail ([E7.S2](#e7s2)) affiche le composant audio fourni, alimenté par le chemin de la séquence courante (WAV ralenti ×10, cf. [R10](../Modèle%20conceptuel/Règles%20métier.md#r10)).
- [ ] Le **curseur de lecture** est synchronisé entre le sonogramme (en haut), le spectrogramme (en bas) et le lecteur audio.  *(non verifiable depuis le code)*
- [ ] Les **contrôles de zoom** du spectrogramme (molette ou slider, indépendants pour temps et fréquence) sont accessibles à l'utilisateur. Bouton « Reset zoom » disponible.  *(non verifiable depuis le code)*
- [x] Quand l'utilisateur change de séquence, le composant se recharge proprement (pas de fuite mémoire ni de freeze).
- [x] Si la séquence est introuvable sur disque, le composant affiche un substitut explicite plutôt que de planter.
- [ ] Test d'intégration : navigation séquentielle dans 100 observations vérifie que le composant reste réactif (< 200 ms de bascule).

**Parcours rattaché** : [P7](../Parcours%20utilisateurs/P7%20-%20Valider%20les%20résultats%20Tadarida.md), étape 4<br>
**Maquettes cibles** : [M-SonsValidation](../Maquettes/M-SonsValidation.md) (section vue audio combinée)<br>
**Dépendances** : [E2.S6](E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s6), [E7.S2](#e7s2), composant audio fourni<br>

---

## E7.S4 - Valider ou corriger le taxon d'une observation { #e7s4 }

**En tant que** [Marie](../Personas/Marie.md)

**Je veux** pouvoir confirmer en un clic que la classification Tadarida est correcte, ou la corriger en saisissant un autre taxon, et accompagner ma décision d'un commentaire libre

**Afin de** consolider la base de données nationale Vigie-Chiro avec mes apports d'expertise

**Critères d'acceptation** :

- [x] Le panneau de détail propose deux boutons primaires : **« Valider »** (le taxon Tadarida est correct) et **« Corriger »** (saisir un autre taxon).
- [x] **Valider** : `taxon observateur = taxon Tadarida` et `probabilité observateur` renseignée à 100 % par défaut (modifiable). Statut → `Validée` ([R15](../Modèle%20conceptuel/Règles%20métier.md#r15)).
- [x] **Corriger** : ouvre un sélecteur de taxon (autocomplétion sur le code à 6 lettres ou nom latin/vernaculaire). À la validation, statut → `Corrigée` ([R16](../Modèle%20conceptuel/Règles%20métier.md#r16)).
- [x] Un champ texte multi-ligne permet d'ajouter un commentaire libre (ex. « pic 39 kHz, morphologie atypique »).
- [x] Le commentaire est persisté avec l'observation et visible dans la liste sous forme d'icône cliquable.
- [ ] Après validation/correction, la liste passe automatiquement à l'observation suivante (avec retour visuel rapide, animation discrète).
- [x] Les observations **non touchées** par l'utilisateur (statut `À voir`) conservent uniquement les colonnes `tadarida_*` et seront exportées telles quelles ([R17](../Modèle%20conceptuel/Règles%20métier.md#r17)).
- [ ] Bouton « Annuler ma validation » sur une observation déjà validée (revient à `À voir`).

**Parcours rattaché** : [P7](../Parcours%20utilisateurs/P7%20-%20Valider%20les%20résultats%20Tadarida.md), étape 5<br>
**Maquettes cibles** : [M-SonsValidation](../Maquettes/M-SonsValidation.md) (zone d'action + sélecteur de taxon)<br>
**Dépendances** : [E0.S5](E0%20-%20Fondations%20de%20persistance.md#e0s5), [E7.S2](#e7s2)<br>

---

## E7.S5 - Filtrer les observations par critères multiples { #e7s5 }

**En tant que** [Samuel](../Personas/Samuel.md)

**Je veux** pouvoir filtrer la liste des observations par taxon, par groupe taxonomique (« toutes les pipistrelles », « tous les murins »), par seuil de probabilité, ou par plage horaire

**Afin de** structurer ma revue en m'attaquant à un sous-ensemble cohérent à la fois (ex. « ce soir je traite tous les murins »)

**Critères d'acceptation** :

- [x] Une barre de filtres au-dessus de la liste propose : taxon (multi-select), groupe taxonomique (Pipistrelles / Murins / Noctules / etc., cf. [C15](../Modèle%20conceptuel/C15%20-%20Groupe%20taxonomique.md)), seuil de probabilité min/max, plage horaire (timestamp début/fin), statut de validation (À voir / Validée / Corrigée).
- [x] Les filtres se cumulent en logique ET.
- [ ] Compteur visible : « N/M observations affichées (filtre actif) ».
- [ ] Bouton « Réinitialiser les filtres » bien visible.
- [x] Les filtres actifs sont affichés sous forme de **chips supprimables** au-dessus de la liste.
- [x] Les filtres sont mémorisés en session (pas persistés entre redémarrages, sauf si combinés à [E0.S7](E0%20-%20Fondations%20de%20persistance.md#e0s7) reprise de validation).
- [x] Compatible avec le tri de [E7.S2](#e7s2).

**Parcours rattaché** : [P7](../Parcours%20utilisateurs/P7%20-%20Valider%20les%20résultats%20Tadarida.md), étape 6<br>
**Maquettes cibles** : [M-SonsValidation](../Maquettes/M-SonsValidation.md) (barre de filtres + chips au-dessus de la liste)<br>
**Dépendances** : [E0.S5](E0%20-%20Fondations%20de%20persistance.md#e0s5), [E7.S2](#e7s2)<br>

---

## E7.S6 - Choisir le mode de validation : inventaire vs activité { #e7s6 }

**En tant que** [Marie](../Personas/Marie.md) ou [Samuel](../Personas/Samuel.md)

**Je veux** déclarer au démarrage de ma session de validation si je veux juste produire la **liste des espèces présentes** (inventaire) ou si je veux **quantifier l'activité** (toutes les observations validées)

**Afin que** l'application adapte son ergonomie à mon objectif et m'évite du travail inutile

**Critères d'acceptation** :

- [x] À l'ouverture de la vue de validation pour un passage, l'application demande le mode (bascule ou fenêtre modale) :
    - **Mode inventaire** : Marie cherche juste à savoir quelles espèces sont présentes. Une fois une espèce validée avec confiance sur une nuit, les autres détections de la même espèce sur la même nuit sont marquées automatiquement comme « secondaires » (statut spécial) et ne demandent pas de validation manuelle.
    - **Mode activité** : Samuel veut quantifier. Toutes les observations doivent être passées en revue.
- [ ] Le mode choisi est **persisté par passage** : on peut ouvrir un passage en mode inventaire et un autre en mode activité.
- [ ] Le mode peut être changé en cours de session (avec avertissement explicite : « Vous avez X observations marquées secondaires, elles redeviendront À voir si vous passez en mode activité »).
- [ ] L'export Vu.csv ([E7.S7](#e7s7)) reflète le mode : en mode inventaire, les observations secondaires reprennent les valeurs Tadarida (puisque non validées manuellement, [R17](../Modèle%20conceptuel/Règles%20métier.md#r17)).
- [ ] Documentation in-app du sens des deux modes (tooltip ou page d'aide) ([R18](../Modèle%20conceptuel/Règles%20métier.md#r18)).

**Parcours rattaché** : [P7](../Parcours%20utilisateurs/P7%20-%20Valider%20les%20résultats%20Tadarida.md), Notes importantes - deux modes<br>
**Maquettes cibles** : [M-SonsValidation](../Maquettes/M-SonsValidation.md) (modal de choix de mode au démarrage + indicateur du mode courant)<br>
**Dépendances** : [E0.S5](E0%20-%20Fondations%20de%20persistance.md#e0s5), [E7.S4](#e7s4)<br>

---

## E7.S7 - Exporter le fichier de résultats validés (Vu.csv) { #e7s7 }

**En tant que** [Marie](../Personas/Marie.md)

**Je veux** exporter mes validations sous forme d'un fichier `*_Vu.csv` au format attendu par Vigie-Chiro

**Afin de** pouvoir le re-téléverser sur le portail et finaliser ma contribution

**Critères d'acceptation** :

- [ ] Bouton « Exporter Vu.csv » dans la vue de validation, désactivé tant qu'aucune observation n'a été validée/corrigée.
- [x] Le fichier produit reprend la structure exacte du CSV d'observations Tadarida (cf. [Expression du besoin](../../Expression%20du%20besoin.md)) avec les colonnes `observateur_taxon` et `observateur_probabilite` remplies pour les observations validées/corrigées.
- [x] Les observations **non touchées** (statut `À voir`) conservent les colonnes Tadarida d'origine sans modification ([R17](../Modèle%20conceptuel/Règles%20métier.md#r17)).
- [ ] Encodage CSV identique à la sortie Tadarida (séparateur `;`, doubles guillemets vides `""""` pour les champs nuls).
- [ ] Le fichier est nommé automatiquement au format `<nom_csv_source>_Vu.csv` (ex. `8a4fa…-observations_Vu.csv`).
- [x] L'utilisateur choisit le dossier de destination via un sélecteur natif.
- [ ] Bouton « Ouvrir le dossier » après export pour faciliter le téléversement manuel sur Vigie-Chiro.
- [x] Tests d'intégration : export d'un passage avec mix de Validées/Corrigées/À voir, vérification de la conformité du CSV produit (lecture round-trip).

**Parcours rattaché** : [P7](../Parcours%20utilisateurs/P7%20-%20Valider%20les%20résultats%20Tadarida.md), étape 8<br>
**Maquettes cibles** : [M-SonsValidation](../Maquettes/M-SonsValidation.md) (bouton « Exporter Vu.csv » + modal de récapitulatif)<br>
**Dépendances** : [E0.S5](E0%20-%20Fondations%20de%20persistance.md#e0s5), [E7.S4](#e7s4)<br>

---

## E7.S8 - Déclarer sa certitude et marquer une observation douteuse { #e7s8 }

**En tant que** [Karim](../Personas/Karim.md)

**Je veux** dire à quel point je suis sûr d'une identification, et pouvoir marquer une détection « à repasser »

**Afin de** tracer ma confiance et mes doutes sans bloquer ma revue

**Critères d'acceptation** :

- [x] Chaque observation peut porter une **certitude `SUR` / `PROBABLE` / `POSSIBLE`**, **vide par défaut** et **jamais dérivée** de la probabilité Tadarida (cf. [C13](../Modèle%20conceptuel/C13%20-%20Observation.md)).
- [x] Une observation peut être marquée **« douteuse »** (écoutée mais à repasser), **distincte** de « pas encore vue ».
- [x] La certitude et le drapeau douteux sont **persistés** et restitués tels quels.
- [x] La certitude est **le même domaine** pour l'observateur et pour le validateur (cf. [E7.S9](#e7s9)).

**Parcours rattaché** : [P7](../Parcours%20utilisateurs/P7%20-%20Valider%20les%20résultats%20Tadarida.md), pendant la revue<br>
**Maquettes cibles** : [M-SonsValidation](../Maquettes/M-SonsValidation.md) (menu certitude + marquage douteux)<br>
**Dépendances** : [E7.S4](#e7s4)<br>

---

## E7.S9 - Consulter l'avis du validateur MNHN et dialoguer avec lui { #e7s9 }

**En tant que** [Marie](../Personas/Marie.md)

**Je veux** voir l'avis de l'expert MNHN sur mes détections et pouvoir lui répondre

**Afin de** comprendre une correction et échanger sur un cas litigieux

**Critères d'acceptation** :

- [x] Sur une observation, **trois avis coexistent** : Tadarida propose, l'observateur corrige, le validateur MNHN tranche.
- [x] L'avis du validateur (taxon + certitude) est **affiché mais en lecture seule** : il n'est **jamais réécrit** vers le serveur.
- [x] Un **fil de discussion ordonné** s'attache à l'observation ; chaque message distingue **« vous »** d'**« un validateur »**.
- [x] **Poster un message est irréversible** (aucune route serveur de suppression) et exige une **confirmation explicite** ; le message part **au serveur d'abord, la base ensuite** (jamais un message cru envoyé mais invisible côté expert).

**Parcours rattaché** : [P7](../Parcours%20utilisateurs/P7%20-%20Valider%20les%20résultats%20Tadarida.md), échange avec le validateur<br>
**Maquettes cibles** : [M-SonsValidation](../Maquettes/M-SonsValidation.md) (panneau de discussion)<br>
**Dépendances** : [E7.S8](#e7s8), [E9.S1](E9%20-%20Intégration%20plateforme%20VigieChiro.md#e9s1)<br>
