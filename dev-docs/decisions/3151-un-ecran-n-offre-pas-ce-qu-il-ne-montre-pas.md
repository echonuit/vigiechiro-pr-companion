# ADR 3151 - Un écran n'offre pas un lieu qu'il ne **montre** pas, et le chemin d'accès compte

- **Statut** : Accepté - 2026-08-05
- **Chantier** : #3151
- **Vérification** : certaine - `SaisonViewTest#colonne_carre_montre_les_deux_etiquettes`

> La règle est tenue sur cinq écrans par autant de gardes : `MultisiteViewTest#colonne_commune_visible_et_vide_si_non_resolue`, `ColonnesAudioContexteTest#nom_du_carre_disponible_mais_masque`, `AnalyseViewTest#colonne_commune_sur_la_table_des_observations`, `SaisonViewTest#colonne_commune`. Le garde des ADR n'accepte qu'une référence, la décision en a cinq.

## Contexte

Le chantier est parti d'un défaut répété : **une recherche trouvait sans montrer**. On tapait un texte,
des lignes apparaissaient, et rien à l'écran ne disait **pourquoi elles correspondaient**.

Il s'est produit quatre fois, sur quatre dimensions et quatre écrans :

| Dimension | Écran | Corrigé par |
|---|---|---|
| la commune | Carte & passages, Sons & validation, Espèces & observations | #3226, #3229, #3233 |
| le nom du carré | Ma saison | #3297 |
| le nom du carré | Carte & passages, Sons & validation | #3310 |
| la commune | Ma saison | #3317 |

Aucune de ces PR n'a inventé la règle : elles l'ont **appliquée**. Elle n'était écrite nulle part, sinon
en une phrase du *contexte* de l'[ADR 2861](2861-une-donnee-de-point-ne-se-montre-pas-sur-une-ligne-agregee.md),
à propos d'un seul cas.

## Décision

**Ce qu'un écran offre de chercher ou de filtrer, il doit pouvoir le montrer.** L'inverse est libre : un
écran peut montrer sans offrir.

L'asymétrie n'est pas arbitraire. Montrer sans offrir laisse l'utilisateur devant une information qu'il
lit ; offrir sans montrer le laisse devant un **résultat qu'il doit croire**.

## Ce que quatre applications ont appris, et qui ne se devinait pas

### 1. La surface n'est pas forcément une colonne

Sur les quatre écrans qui ont une puce « Lieu », les entrées de la puce **sont** une surface : elles
affichent le carré qualifié, `640380 · Vallon` ([ADR 3157](3157-un-carre-a-un-identifiant-et-une-etiquette.md)).
Un écran peut donc offrir le nom du carré sans lui donner de colonne - **à condition qu'on y arrive par
la puce**.

### 2. Le chemin compte, et c'est le point le moins évident

Une puce ne sert **pas** la recherche libre. Sur Carte & passages et Sons & validation, la recherche
retenait une ligne sur le nom du carré (`CriteresMultisite.correspond`, `CriteresAudio.correspond`) :
qui tape « Vallon » dans la recherche ne passe pas par la puce, et ne voit donc rien qui explique le
résultat.

Ces deux écrans avaient été **écartés à tort** au premier examen de la clôture, par un raisonnement
d'analogie - « la puce le montre » - vrai d'un chemin et faux de l'autre. La règle se vérifie donc
**chemin par chemin**, pas écran par écran.

### 3. Une table pleine n'oblige pas à choisir

Quand la table ne peut plus prendre de colonne, la colonne existe quand même, **masquée**, inscrite au
sélecteur (#919). Carte & passages totalisait déjà 1 360 px dans une scène partagée avec la carte, et
Sons & validation compte 22 colonnes : les deux ont reçu leur colonne, éteinte. C'est un test qui l'a
imposé - ouverte, elle poussait la date hors de la zone visible et `double_clic_ouvre_le_passage`
échouait.

### 4. La forme se juge sur une capture, pas sur un raisonnement

Sur Ma saison, qualifier la colonne « Carré » en `640380 · Vallon`, comme les entrées de la puce, semblait
évident. La capture régénérée montrait `640380 · …`. Il a fallu **trois** captures - qualification,
colonne dédiée, puis élargissement de la scène - pour que le nom **et** « Reste à faire » tiennent
ensemble. Ni un test ni une relecture n'auraient pris cette décision.

## Conséquences

- Ajouter un champ à une recherche libre ou à une puce oblige à se demander **où il se voit** sur cet
  écran, et **par quel chemin** on y arrive ;
- l'inventaire se fait **dimension par dimension et chemin par chemin** : la matrice qui a servi à la
  clôture croise, pour chaque écran, ce que la recherche libre interroge, ce que la puce offre, et ce
  que les colonnes rendent ;
- la ligne de commande suit la même règle sous une autre forme : ce que `--lieu` retient doit se lire
  dans la sortie. C'est ce qui a fait ajouter le nom du carré aux trois sorties de `solde-saison`
  (#3297) et la commune ensuite (#3317).

## Alternatives écartées

- **Retirer de la recherche ce qui ne se montre pas.** Le moins de code, et la contradiction disparaît -
  mais on enlève un raccourci qui marche, et que les utilisateurs emploient précisément parce qu'ils
  pensent leurs carrés par leur nom.
- **Une colonne visible partout, systématiquement.** Rejeté par la mesure : sur les deux tables les plus
  denses, elle chassait de l'écran une colonne que l'utilisateur regarde davantage.
- **Laisser la puce couvrir la recherche libre.** C'est ce qu'on a cru un moment. Deux chemins distincts
  ne se couvrent pas l'un l'autre.
