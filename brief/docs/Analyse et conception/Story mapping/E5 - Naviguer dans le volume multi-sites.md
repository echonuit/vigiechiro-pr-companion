# E5 - 🗂 Naviguer dans le volume multi-sites

[← Retour au sommaire story mapping](index.md) · **Parcours principal** : [P5 - Naviguer dans plusieurs sites et passages](../Parcours%20utilisateurs/P5%20-%20Naviguer%20dans%20plusieurs%20sites%20et%20passages.md) (MUST de fait pour Karim et Samuel)

**Portée** : permettre à l'utilisateur de **se repérer rapidement** dans un volume de plusieurs sites, points et passages, sans se perdre. C'est l'épopée qui transforme l'application d'un outil mono-site (où la barre latérale de [E1.S4](E1%20-%20Gérer%20ses%20sites%20et%20points%20de%20suivi.md#e1s4) suffit) en un outil de production capable de tenir l'échelle de Karim (3 chantiers, 8 nuits par retour terrain) ou Samuel (24 enregistreurs × 40-50 nuits = **plus de 1 000 passages par saison**).

**Persona principal** : Karim et Samuel (Marie reste sur 1 site et n'a pas besoin de la majorité des stories de cette épopée).

**Pré-requis** : [E0.S2](E0%20-%20Fondations%20de%20persistance.md#e0s2), [E0.S3](E0%20-%20Fondations%20de%20persistance.md#e0s3), [E1](E1%20-%20Gérer%20ses%20sites%20et%20points%20de%20suivi.md) (sites/points déclarés), [E2](E2%20-%20Importer%20et%20transformer%20une%20nuit.md) (passages importés).

## E5.S1 - Vue arborescente des sites avec compteurs de passages par point { #e5s1 }

!!! warning "Non livré (cible)"
    L'application n'a **pas** de vue arborescente (aucun `TreeView` dans le code) : le volume multi-sites se parcourt par la **vue tabulaire** ([E5.S2](#e5s2)), livrée. Les critères ci-dessous décrivent la cible.

**En tant que** [Karim](../Personas/Karim.md)

**Je veux** voir tous mes sites de suivi sous forme d'arbre où chaque site se déplie en points d'écoute, et chaque point affiche le nombre de passages cette saison + l'état du dernier passage

**Afin de** voir d'un coup d'œil où j'en suis sur chaque site et identifier les points qui n'ont pas encore été utilisés

**Critères d'acceptation** :

- [ ] La vue arborescente présente un nœud racine par site (libellé : `Carré XXXXXX - <nom convivial>`).
- [ ] Chaque site se déplie en ses points d'écoute (libellé : `<code point> - N passages cette saison`).
- [ ] Chaque point se déplie en ses passages (libellé : `Passage N (date)` avec badge de statut d'avancement).
- [ ] Au niveau site, un méta-libellé indique la **date du dernier passage importé** (« dernier passage il y a 2 jours »).
- [ ] Au niveau point, on voit le nombre de passages **à vérifier** (statut `Transformé`, sans verdict) en évidence visuelle (badge orange).
- [ ] Le clic sur un passage ouvre directement sa fiche détail.
- [ ] L'état déplié/replié de chaque nœud est mémorisé pour la session.

**Parcours rattaché** : [P5](../Parcours%20utilisateurs/P5%20-%20Naviguer%20dans%20plusieurs%20sites%20et%20passages.md), étape 1<br>
**Maquettes cibles** : [M-MultiSite](../Maquettes/M-MultiSite.md) (panneau gauche arborescent), [M-Sites](../Maquettes/M-Sites.md) (vue d'entrée également)<br>
**Dépendances** : [E0.S2](E0%20-%20Fondations%20de%20persistance.md#e0s2), [E0.S3](E0%20-%20Fondations%20de%20persistance.md#e0s3), [E1.S4](E1%20-%20Gérer%20ses%20sites%20et%20points%20de%20suivi.md#e1s4)<br>

---

## E5.S2 - Vue tabulaire des passages avec tri et filtres par colonne { #e5s2 }

**En tant que** [Karim](../Personas/Karim.md) ou [Samuel](../Personas/Samuel.md)

**Je veux** une vue alternative à l'arborescence qui liste **tous mes passages** sous forme de tableau triable et filtrable par chaque colonne

**Afin de** pouvoir balayer rapidement un grand volume et identifier les passages qui demandent mon attention (statut, verdict, date)

**Critères d'acceptation** :

- [ ] Une bascule permet de basculer entre la vue arborescente ([E5.S1](#e5s1)) et la vue tabulaire.
- [ ] La vue tabulaire affiche une ligne par passage avec colonnes : Site (n° carré + nom), Point, Année, N° passage, Date de session d'enregistrement, Statut d'avancement, Verdict, Date de dépôt.
- [x] Toutes les colonnes sont **triables** (clic sur l'en-tête).
- [ ] Chaque colonne propose un **filtre rapide** :
    - colonnes textuelles : input texte avec match partiel (Site, Point)
    - colonnes énumérées : multi-sélection (Statut d'avancement, Verdict)
    - colonnes dates : sélecteur de plage
- [ ] Le tableau reste réactif jusqu'à au moins 500 lignes (cf. [O5](../../Objectifs%20qualités/Objectifs%20qualités/O5.md)) - pas de freeze IHM en tri/filtre.  *(non verifiable depuis le code)*
- [x] Le clic sur une ligne ouvre la fiche détail du passage.
- [x] L'état des filtres et du tri est mémorisé en session (perdu au redémarrage, contrairement à la sélection d'écoute qui est persistée).

**Parcours rattaché** : [P5](../Parcours%20utilisateurs/P5%20-%20Naviguer%20dans%20plusieurs%20sites%20et%20passages.md), étape 2<br>
**Maquettes cibles** : [M-MultiSite](../Maquettes/M-MultiSite.md) (panneau principal en mode tableau)<br>
**Dépendances** : [E0.S3](E0%20-%20Fondations%20de%20persistance.md#e0s3), [E4.S4](E4%20-%20Préparer%20et%20tracer%20le%20dépôt%20VigieChiro.md#e4s4)<br>

---

## E5.S3 - Filtres avancés multi-critères avec sauvegarde de vues { #e5s3 }

**En tant que** [Samuel](../Personas/Samuel.md)

**Je veux** pouvoir combiner plusieurs filtres simultanément (ex. « tous les passages au statut Transformé sur les sites 64XXXX, importés depuis le 1er juin ») et sauvegarder ces combinaisons comme « vues » réutilisables

**Afin de** ne pas reconstituer manuellement le même jeu de filtres chaque fois que je reprends ma routine de revue

**Critères d'acceptation** :

- [ ] Un bouton « Filtres avancés » ouvre un panneau de composition de filtres avec une logique ET entre les critères, OU à l'intérieur d'une même catégorie.
- [ ] Les critères disponibles couvrent : site (multi-select), point (multi-select), année, plage de n° de passage, plage de dates d'enregistrement, plage de dates de dépôt, statut d'avancement (multi-select), verdict (multi-select), enregistreur (par n° de série).
- [x] Un compteur en bas de panneau indique « N passages correspondent à ces critères ».
- [ ] Bouton « Appliquer » : la vue tabulaire ([E5.S2](#e5s2)) se rafraîchit avec les résultats filtrés.
- [x] Bouton « Sauvegarder cette vue » : permet de nommer le jeu de filtres et de le retrouver dans un menu de « Vues sauvegardées ».
- [x] Les vues sauvegardées sont **persistées** en BD et restent disponibles après redémarrage.
- [ ] Possibilité de définir une vue comme « vue par défaut à l'ouverture ».
- [ ] Réactivité acceptable même avec 1 000+ passages (Samuel) - pagination ou virtualisation si nécessaire.  *(non verifiable depuis le code)*

**Parcours rattaché** : [P5](../Parcours%20utilisateurs/P5%20-%20Naviguer%20dans%20plusieurs%20sites%20et%20passages.md), Notes pour Samuel<br>
**Maquettes cibles** : [M-MultiSite](../Maquettes/M-MultiSite.md) (panneau « Filtres avancés » dépliable + menu vues sauvegardées)<br>
**Dépendances** : [E0.S3](E0%20-%20Fondations%20de%20persistance.md#e0s3), [E5.S2](#e5s2)<br>

---

## E5.S4 - Actions de masse sur une sélection de passages { #e5s4 }

!!! warning "Non livré (cible)"
    Il n'existe ni sélection multiple de passages, ni actions de masse (verdict ou suppression en lot, saisie de « SUPPRIMER », journal d'opérations). La seule multi-sélection du code sert la **revue d'observations audio** ([E7](E7%20-%20Valider%20les%20résultats%20Tadarida.md)), pas cette story : suppression et verdict restent **unitaires**.

**En tant que** [Samuel](../Personas/Samuel.md)

**Je veux** pouvoir sélectionner plusieurs passages dans la vue tabulaire et leur appliquer une action commune (changer le verdict, supprimer, exporter)

**Afin de** ne pas être obligé d'ouvrir chaque passage individuellement quand je veux faire le même traitement sur un lot

**Critères d'acceptation** :

- [ ] La vue tabulaire ([E5.S2](#e5s2)) permet la sélection multiple (Ctrl+clic, Maj+clic pour plage, Ctrl+A pour tout).
- [ ] Une barre d'actions contextuelle apparaît dès qu'au moins 2 passages sont sélectionnés : « Actions sur 5 passages : [Verdict] [Exporter] [Supprimer] ».
- [ ] Action **Verdict** : applique le même verdict (OK / Utilisable / Inexploitable) à tous les passages sélectionnés, avec confirmation explicite.
- [ ] Action **Exporter** : produit un récapitulatif CSV (1 ligne par passage avec ses métadonnées) téléchargeable.
- [ ] Action **Supprimer** : confirmation forte (fenêtre modale expliquant que les fichiers sur disque seront aussi supprimés), avec saisie de « SUPPRIMER » pour valider. Action irréversible.
- [ ] Toute action de masse est tracée dans un journal d'opérations (pour audit en cas de bug ou de fausse manipulation).
- [ ] Si l'action échoue partiellement (ex. 3 passages traités, 2 en erreur), un récapitulatif post-action liste les succès et les échecs avec leur raison.

**Parcours rattaché** : [P5](../Parcours%20utilisateurs/P5%20-%20Naviguer%20dans%20plusieurs%20sites%20et%20passages.md), Notes pour Samuel<br>
**Maquettes cibles** : [M-MultiSite](../Maquettes/M-MultiSite.md) (barre d'actions contextuelle)<br>
**Dépendances** : [E0.S3](E0%20-%20Fondations%20de%20persistance.md#e0s3), [E5.S2](#e5s2)<br>

---

## E5.S5 - Import groupé de plusieurs dossiers SD à la suite { #e5s5 }

!!! warning "Non livré (cible)"
    Pas de file d'attente d'import persistée ni de panneau « File d'attente ». Ce qui existe : une reprise **idempotente fichier par fichier** au **relancement manuel** (#231), sans mise en file ni notification au démarrage. Dépend de [E0.S6](E0%20-%20Fondations%20de%20persistance.md#e0s6), également non livrée.

**En tant que** [Karim](../Personas/Karim.md) (qui revient d'une semaine de terrain avec 5 cartes SD)

**Je veux** pouvoir lancer en une seule fois l'import de plusieurs dossiers SD et que l'application les traite en file d'attente

**Afin de** ne pas avoir à attendre la fin d'un import pour démarrer le suivant et pouvoir tout lancer puis aller faire autre chose

**Critères d'acceptation** :

- [ ] Une action « Importer plusieurs nuits » permet de sélectionner N dossiers source d'un coup (sélection multiple dans le sélecteur de fichiers, ou glisser-déposer de N dossiers).
- [ ] Pour chaque dossier, l'inspection ([E2.S1](E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s1)) et le rattachement ([E2.S2](E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s2) ou [E2.S3](E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s3)) sont demandés successivement, avant que le traitement effectif (copie + transformation) ne commence.
- [ ] Une fois tous les rattachements validés, les imports sont **mis en file d'attente** et traités séquentiellement (pas en parallèle pour préserver les perfs).
- [ ] Un panneau « File d'attente d'import » visible affiche : import en cours (avec progression), imports en attente, imports terminés avec succès, imports en erreur.
- [ ] L'utilisateur peut **annuler** un import en attente (file d'attente) ou en cours (avec rollback de ce qui a été fait).
- [ ] La file d'attente est **persistée** en BD : si l'application est fermée pendant un import groupé, elle peut reprendre au démarrage suivant ([E0.S6](E0%20-%20Fondations%20de%20persistance.md#e0s6)).

**Parcours rattaché** : [P5](../Parcours%20utilisateurs/P5%20-%20Naviguer%20dans%20plusieurs%20sites%20et%20passages.md), étape 3<br>
**Maquettes cibles** : [M-Import](../Maquettes/M-Import.md) (variante multi-dossiers), [M-MultiSite](../Maquettes/M-MultiSite.md) (panneau file d'attente)<br>
**Dépendances** : [E0.S6](E0%20-%20Fondations%20de%20persistance.md#e0s6), [E2.S1](E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s1), [E2.S2](E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s2), [E2.S4](E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s4), [E2.S6](E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s6)<br>

---

## E5.S6 - Rechercher globalement depuis n'importe quel écran { #e5s6 }

**En tant que** [Karim](../Personas/Karim.md)

**Je veux** un champ de recherche accessible partout qui retrouve un site, un point, un passage ou une espèce

**Afin de** sauter directement au bon endroit sans naviguer d'écran en écran

**Critères d'acceptation** :

- [x] Un **champ de recherche** dans le bandeau (chrome) est accessible **depuis tout écran**.
- [x] La recherche **agrège sites, points, passages et espèces observées** ; la correspondance est **insensible à la casse et aux accents**.
- [x] Les résultats sont **plafonnés par type** (au plus 8) pour rester lisibles ; le surplus est omis.
- [x] La navigation se fait **au clavier** (flèche pour entrer dans la liste, Entrée pour ouvrir, Échap pour fermer) et un résultat est **annoncé** pour l'accessibilité.

**Parcours rattaché** : [P8](../Parcours%20utilisateurs/P8%20-%20Rechercher%20globalement.md) (transverse)<br>
**Maquettes cibles** : *champ de recherche du bandeau non maquetté* (cf. [#2382](https://github.com/echonuit/vigiechiro-pr-companion/issues/2382))<br>
**Dépendances** : [E5.S2](#e5s2)<br>
