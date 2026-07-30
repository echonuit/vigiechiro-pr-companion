# E8 - 🚀 Productivité avancée Tadarida

[← Retour au sommaire story mapping](index.md) · **Parcours principaux** : [P9 - Regrouper les nuits successives par point](../Parcours%20utilisateurs/P9%20-%20Regrouper%20les%20nuits%20successives%20par%20point.md), [P10 - Exporter une bibliothèque de sons de référence](../Parcours%20utilisateurs/P10%20-%20Exporter%20une%20bibliothèque%20de%20sons%20de%20référence.md) (au mieux)

**Portée** : les gains de productivité avancés sur la validation taxonomique - regrouper plusieurs nuits successives du même point pour validation conjointe (P9), constituer une bibliothèque de sons de référence par espèce (P10). Ce sont des **idées remontées par Samuel** (mai 2026) qui multiplient la valeur de la chaîne de validation [E7](E7%20-%20Valider%20les%20résultats%20Tadarida.md).

Ces gains sont **secondaires** : ils enrichissent la validation sans en être un prérequis. Ils prennent leur sens une fois la chaîne principale ([E1](E1%20-%20Gérer%20ses%20sites%20et%20points%20de%20suivi.md) à [E4](E4%20-%20Préparer%20et%20tracer%20le%20dépôt%20VigieChiro.md)) et la validation ([E7](E7%20-%20Valider%20les%20résultats%20Tadarida.md)) solides.

**Persona principal** : Samuel exclusivement (Karim en bénéficie aussi par effet de bord, Marie n'en a pas l'usage).

**Pré-requis** : [E7](E7%20-%20Valider%20les%20résultats%20Tadarida.md) en place ([E7.S1](E7%20-%20Valider%20les%20résultats%20Tadarida.md#e7s1) à [E7.S7](E7%20-%20Valider%20les%20résultats%20Tadarida.md#e7s7) au minimum).

## E8.S1 - Regrouper plusieurs passages successifs pour validation conjointe { #e8s1 }

!!! warning "Non livré (cible COULD)"
    Il n'existe ni service ni ViewModel de regroupement de nuits pour validation groupée (le test E2E du parcours le documente lui-même : « il n'existe aucun service ni ViewModel de regroupement »). L'axe d'inventaire `PAR_ESPECE` / `PAR_CARRE` existe par ailleurs, mais ce n'est pas la validation regroupée décrite ici.

**En tant que** [Samuel](../Personas/Samuel.md) (qui déploie un PR sur le même point pendant 4 nuits successives)

**Je veux** pouvoir sélectionner plusieurs passages successifs d'un même point et les valider ensemble dans une vue unifiée

**Afin de** ne pas re-valider la même espèce 4 fois si elle est présente toutes les nuits

**Critères d'acceptation** :

- [ ] Depuis la vue tabulaire ([E5.S2](E5%20-%20Naviguer%20dans%20le%20volume%20multi-sites.md#e5s2)), l'utilisateur peut sélectionner plusieurs passages d'un même point (multi-sélection).
- [ ] Action « Regrouper pour validation » disponible si au moins 2 passages sélectionnés et tous au statut `Annoté Tadarida`.
- [ ] L'écran de validation regroupée fusionne les observations des N passages en une seule liste, triée par espèce.
- [ ] Compteur explicite : « 4 nuits, 12 espèces détectées, 2 870 observations au total ».
- [ ] En **mode inventaire** ([E7.S6](E7%20-%20Valider%20les%20résultats%20Tadarida.md#e7s6)), valider une espèce sur la 1re occurrence l'applique aux autres (toutes nuits du regroupement).
- [ ] En **mode activité**, le tri par espèce permet d'enchaîner rapidement même si chaque obs doit être validée individuellement.
- [ ] L'export Vu.csv produit **N fichiers distincts** (un par passage) avec les validations propagées correctement.
- [ ] Bouton « Sortir du mode regroupé » pour revenir à la validation d'un passage individuel.

**Parcours rattaché** : [P9](../Parcours%20utilisateurs/P9%20-%20Regrouper%20les%20nuits%20successives%20par%20point.md)<br>
**Maquettes cibles** : [M-SonsValidation](../Maquettes/M-SonsValidation.md) (variante regroupée), [M-MultiSite](../Maquettes/M-MultiSite.md) (action de regroupement)<br>
**Dépendances** : [E5.S2](E5%20-%20Naviguer%20dans%20le%20volume%20multi-sites.md#e5s2), [E7.S2](E7%20-%20Valider%20les%20résultats%20Tadarida.md#e7s2), [E7.S4](E7%20-%20Valider%20les%20résultats%20Tadarida.md#e7s4), [E7.S6](E7%20-%20Valider%20les%20résultats%20Tadarida.md#e7s6)<br>

---

## E8.S2 - Marquer des séquences comme référence et exporter une bibliothèque par espèce { #e8s2 }

!!! note "Partiellement livré"
    Le marquage « séquence de référence » et l'export existent, et l'export produit désormais une **archive ZIP** annulable au bilan chiffré (harmonisation avec l'export « observations + sons », clôture de l'EPIC #2790). Mais les sons y restent **à plat** sous `sons/`, accompagnés d'un CSV récapitulatif où le taxon est une colonne : ils ne sont **pas** rangés en **arborescence de sous-dossiers par taxon** (cf. [R32](../Modèle%20conceptuel/Règles%20métier.md#r32)).

**En tant que** [Samuel](../Personas/Samuel.md) (qui forme un débutant)

**Je veux** pouvoir marquer certaines observations validées comme « séquence de référence » et générer un dossier organisé par espèce avec mes meilleurs exemples

**Afin de** transmettre une bibliothèque pédagogique exploitable à un débutant ou pour mon propre usage

**Critères d'acceptation** :

- [x] Pendant la validation ([E7.S4](E7%20-%20Valider%20les%20résultats%20Tadarida.md#e7s4)), un bouton « Marquer comme référence » est disponible sur l'observation courante.
- [x] Une observation marquée référence est mise en évidence visuellement dans la liste (icône ⭐).
- [x] Action « Démarquer » réversible.
- [ ] Menu « Exporter > Bibliothèque de sons de référence » qui génère un dossier organisé :
    ```
    bibliotheque/
      Pippip - Pipistrellus pipistrellus/
        Car640380-2026-Pass2-Z1-...20260422_212817_003.wav
        Car640380-2026-Pass2-Z1-...20260423_001435_001.wav
      Nyclei - Nyctalus leisleri/
        ...
    ```
- [x] Les WAV exportés sont **copies** des séquences ralenties ×10 (lisibles directement par tout lecteur audio).
- [ ] **Variante** : option « Document récapitulatif » pour produire en plus un PDF/HTML par espèce avec spectrogrammes et métadonnées (à arbitrer selon la complexité, [E6.S5](E6%20-%20Diagnostiquer%20le%20matériel.md#e6s5) peut servir de base technique).
- [x] L'utilisateur choisit l'**archive** de destination : elle est produite directement, sans étape de compression manuelle.
- [x] La sélection « référence » est persistée en BD ([E0.S5](E0%20-%20Fondations%20de%20persistance.md#e0s5)) et réutilisable d'une session à l'autre.

**Parcours rattaché** : [P10](../Parcours%20utilisateurs/P10%20-%20Exporter%20une%20bibliothèque%20de%20sons%20de%20référence.md)<br>
**Maquettes cibles** : [M-SonsValidation](../Maquettes/M-SonsValidation.md) (bouton « Marquer comme référence »), [M-Lot](../Maquettes/M-Lot.md) (action « Exporter bibliothèque »)<br>
**Dépendances** : [E0.S5](E0%20-%20Fondations%20de%20persistance.md#e0s5), [E7.S4](E7%20-%20Valider%20les%20résultats%20Tadarida.md#e7s4)<br>

---

## E8.S3 - Explorer l'inventaire des espèces et la biodiversité { #e8s3 }

**En tant que** [Karim](../Personas/Karim.md)

**Je veux** un écran qui agrège mes observations par espèce et par carré

**Afin d'** avoir une vue biodiversité de mes campagnes, au-delà d'un passage isolé

**Critères d'acceptation** :

- [x] Un écran **« Espèces & biodiversité »** charge les observations enrichies à l'ouverture et **filtre / agrège côté client** (par espèce, par carré).
- [x] Une vue **maître-détail** présente chaque espèce et ses observations.
- [x] Une **carte de répartition** et la **richesse par carré** sont affichées.
- [x] L'inventaire est **exportable en CSV**.

**Parcours rattaché** : [P11](../Parcours%20utilisateurs/P11%20-%20Inventaire%20des%20espèces%20détectées.md)<br>
**Maquettes cibles** : [M-Analyse](../Maquettes/M-Analyse.md)<br>
**Dépendances** : [E7.S1](E7%20-%20Valider%20les%20résultats%20Tadarida.md#e7s1)<br>

---

## E8.S4 - Exporter un sous-ensemble filtré avec ses sons, pour avis d'expert { #e8s4 }

**En tant que** [Samuel](../Personas/Samuel.md)

**Je veux** emporter les observations que j'ai à l'écran **et leurs fichiers son** dans une archive unique

**Afin de** faire trancher une identification douteuse par un spécialiste, qui doit pouvoir **réécouter**

**Critères d'acceptation** :

- [x] La vue « Sons & validation » offre un critère **« Lieu »** (commune, carré, point, site présents
      dans le sous-ensemble chargé) qui se combine avec le critère **Espèce**, et sa recherche texte
      couvre ces mêmes champs.
- [x] La **commune** d'un point est dérivée de ses coordonnées GPS et mémorisée
      ([C3](../Modèle%20conceptuel/C3%20-%20Point%20d%27écoute.md)) : « Aix-en-Provence » est trouvable
      même si le site porte un autre nom.
- [x] Le menu ☰ produit une **archive ZIP** du sous-ensemble affiché : `observations.csv` (identique à
      l'export CSV seul) et les sons rangés **par nuit**, dédupliqués.
- [x] L'écriture est **annulable** et montre sa progression ; l'annulation comme l'échec ne laissent
      **aucune archive partielle**.
- [x] Un son dont le fichier a quitté le disque est **compté** dans le compte rendu, sans bloquer
      l'export : l'observation reste au CSV.
- [x] La **ligne de commande** produit la même archive (`exporter-sons --passage | --espece --sortie`).

**Parcours rattaché** : [P13](../Parcours%20utilisateurs/P13%20-%20Envoyer%20un%20sous-ensemble%20à%20un%20expert.md)<br>
**Maquettes cibles** : [M-SonsValidation](../Maquettes/M-SonsValidation.md) (menu ☰ + modale de progression)<br>
**Dépendances** : [E7.S1](E7%20-%20Valider%20les%20résultats%20Tadarida.md#e7s1), [E8.S3](#e8s3)<br>
