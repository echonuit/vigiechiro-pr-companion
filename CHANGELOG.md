# Journal des modifications

Le format suit [Keep a Changelog](https://keepachangelog.com/fr/) et le versionnage [SemVer](https://semver.org/lang/fr/). Les entrées sont ajoutées automatiquement par semantic-release à chaque version.

# [1.67.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.66.0...v1.67.0) (2026-07-02)


### Features

* **audio:** activer la normalisation visuelle du sonogramme (audio-view 1.13.1) ([#444](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/444)) ([b27752f](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/b27752f83d9bd1ad1684929c091fbb7ea0f45cac))

# [1.66.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.65.1...v1.66.0) (2026-07-01)


### Features

* **audio:** deux colonnes de taxon (Tadarida vs votre décision) au lieu de trois ([#442](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/442)) ([8f4028b](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/8f4028b984f153aea059bc2326e2c5ec16aa31da))

## [1.65.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.65.0...v1.65.1) (2026-06-30)


### Bug Fixes

* **validation:** réparer les taxons-souches masquant le référentiel officiel (V06) ([#440](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/440)) ([7437dc2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/7437dc216787c60bfe28037855271925f5faeec5)), closes [#437](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/437)

# [1.65.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.64.0...v1.65.0) (2026-06-30)


### Features

* **audio:** nom vernaculaire (espèce + Tadarida) et probabilité dans la table de validation ([#437](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/437)) ([a64cc06](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/a64cc06651a1f6e9a3d524be8c0003c373ddc599))

# [1.64.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.63.0...v1.64.0) (2026-06-30)


### Features

* **audio:** proposer la réimportation d'un CSV Tadarida + icône info lisible ([#436](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/436)) ([3fb0cc5](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/3fb0cc5ba59f3d9fedaac5cf4b12a58791aa20d8))

# [1.63.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.62.0...v1.63.0) (2026-06-30)


### Features

* **validation:** seed du référentiel officiel Tadarida (France) — V05 ([#435](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/435)) ([61a2a3f](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/61a2a3fa85b216430ebe10df87f5ee6532119e8c))

# [1.62.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.61.1...v1.62.0) (2026-06-30)


### Features

* **audio:** import Tadarida tolérant (séquences manquantes + taxons hors référentiel) ([#432](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/432)) ([e424154](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/e42415441839139a5e1263f3b8bb9022d167afd9))

## [1.61.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.61.0...v1.61.1) (2026-06-30)


### Bug Fixes

* **audio:** bandeau de retour d'import bien plus visible + erreur séquence actionnable ([#431](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/431)) ([49e540a](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/49e540a587f3b96682ecf62ef949cdce981872c5)), closes [#bandeauRetour](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/bandeauRetour) [#lblMessage](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/lblMessage)

# [1.61.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.60.0...v1.61.0) (2026-06-30)


### Features

* **audio:** glisser-déposer d'un CSV Tadarida (fallback FileChooser) ([#427](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/427)) ([ca40eec](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/ca40eecda8035fcfdb493d7d11cee2654bd9a766))

# [1.60.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.59.2...v1.60.0) (2026-06-30)


### Features

* **audio:** retour d'information explicite pour l'import/export (fini le placeholder gris) ([#426](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/426)) ([d86dc11](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/d86dc1112ce02567c49206fdb605846bc3468a02))

## [1.59.2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.59.1...v1.59.2) (2026-06-30)


### Bug Fixes

* **validation:** tolère une probabilité textuelle dans un _Vu Tadarida (SUR) ([#425](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/425)) ([a85d35b](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/a85d35b448af5a29b8d4d942bd8c4e8b304812fc))

## [1.59.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.59.0...v1.59.1) (2026-06-29)


### Bug Fixes

* **audio:** restaure progression de revue + option « inclure le mode » ([#423](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/423)) ([a319ef4](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/a319ef40e18910d76c0da59510ba95c93b5f5e85))

# [1.59.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.58.0...v1.59.0) (2026-06-29)


### Features

* **audio:** branchement multisite — ligne + lot filtré (PR-3d) ([#415](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/415)) ([70a63e0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/70a63e0c7a97c1c2e7efb01c53088a2f72f9bcc8)), closes [#291](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/291)

# [1.58.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.57.0...v1.58.0) (2026-06-29)


### Features

* **audio:** branchement analyse — source ParEspece + contrat OuvrirAnalyse (PR-3c) ([#413](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/413)) ([300e0ee](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/300e0eee68c795ed0b5852301b5c4b5e57b40868))

# [1.57.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.56.0...v1.57.0) (2026-06-29)


### Features

* **audio:** branchement passage — OuvrirValidation délègue à OuvrirAudio (PR-3b) ([#411](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/411)) ([bd0ffdb](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/bd0ffdb744806eefbad6eae523534452a9b55823))

# [1.56.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.55.0...v1.56.0) (2026-06-29)


### Features

* **audio:** écran Sons & validation + entrée Références (PR-3a) ([#409](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/409)) ([53bc874](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/53bc8748feb7113346331e9796d7f34a23a72f5e)), closes [#329](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/329)

# [1.55.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.54.0...v1.55.0) (2026-06-28)


### Features

* **audio:** SourceObservations + ViewModel audio unifié ([#407](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/407)) ([d7bb51d](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/d7bb51d26ccd12ddc6fdac3467cf38a9878ae43d)), closes [#404](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/404)

# [1.54.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.53.0...v1.54.0) (2026-06-28)


### Features

* **audio:** couche modèle de la vue audio unifiée (record + projections) ([#404](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/404)) ([924a395](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/924a39533fca8d414c4376564727be65feab1d5d)), closes [#audio](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/audio)

# [1.53.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.52.0...v1.53.0) (2026-06-28)


### Features

* **validation:** marquer référence + lister les références (socle vue audio unifiée) ([#402](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/402)) ([5f9ecc6](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/5f9ecc616be54b26f10e9a865fe176524a2b1b09))

# [1.52.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.51.0...v1.52.0) (2026-06-28)


### Features

* **analyse:** carte de répartition (choroplèthe de richesse + répartition d'une espèce) ([#400](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/400)) ([5aaf510](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/5aaf510ecf167ae2dca794eaed91713bcf2a71fd))

# [1.51.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.50.0...v1.51.0) (2026-06-28)


### Features

* **analyse:** exposer les carrés de l'espèce sélectionnée (données carte) ([#398](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/398)) ([ade69d2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/ade69d294f613370f0c5781f9c86147fe47e9f5b))

# [1.50.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.49.1...v1.50.0) (2026-06-28)


### Features

* **accueil:** accueil à deux prismes (Collecte & passages / Espèces & biodiversité) ([#396](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/396)) ([f5f2e02](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/f5f2e027f76aa2715b68ab7a9107970fdbdd59fd))

## [1.49.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.49.0...v1.49.1) (2026-06-28)


### Bug Fixes

* **captures:** cohérence géographique carré ↔ coordonnées ([#392](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/392)) ([e335fc0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/e335fc02bcd8094ac51094b2f68eed262919542c))

# [1.49.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.48.0...v1.49.0) (2026-06-28)


### Features

* **analyse:** écouter / valider une détection depuis « Espèces & observations » ([#388](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/388)) ([0b8abca](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/0b8abca93a6df9c98af8decd14c70cc005242ce5))

# [1.48.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.47.0...v1.48.0) (2026-06-28)


### Features

* **recherche:** documenter la recherche globale (page, capture, README) ([#389](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/389)) ([acca4a9](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/acca4a9b77a096feef5e9d271f842925be1b8ed9))

# [1.47.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.46.1...v1.47.0) (2026-06-28)


### Features

* **analyse:** détail des observations d'une espèce à travers les passages + ouvrir le passage ([#379](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/379)) ([d45333b](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/d45333b291e4868fa2330c9015b871c5c5509c81)), closes [#381](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/381)

## [1.46.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.46.0...v1.46.1) (2026-06-28)


### Bug Fixes

* **importation:** afficher la carte dans la capture de l'assistant d'import ([#383](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/383)) ([68ec523](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/68ec523aff9021a4b2e2df04f7c576c52d2b6a5e))

# [1.46.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.45.0...v1.46.0) (2026-06-28)


### Features

* **sites:** lien « placer sur la carte » pour les points sans GPS ([#380](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/380)) ([bc43439](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/bc434391fb5e9179a498acc4f0b9c98bb9e6704f))

# [1.45.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.44.0...v1.45.0) (2026-06-28)


### Features

* **multisite:** barre d'outils épurée (menu ☰, replis en bas, invites intégrées) ([#377](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/377)) ([e112def](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/e112defafacd4dc4d06ddb3d6e5149624233e86a)), closes [#163](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/163)

# [1.44.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.43.0...v1.44.0) (2026-06-28)


### Features

* **analyse:** filtre texte + export CSV de l'inventaire des espèces ([#375](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/375)) ([a856ab0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/a856ab0bf626bf7f6508b212e0ad32681e3006df))

# [1.43.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.42.0...v1.43.0) (2026-06-28)


### Features

* **accueil:** icône de carte pour « Carte & passages » ([#373](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/373)) ([10fbeff](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/10fbeffc93c15d93ac91ebbe7f87fe91ba11e172))

# [1.42.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.41.0...v1.42.0) (2026-06-28)


### Features

* **carte:** numéro de carré dans le coin + nom de point abrégé, contourés ([#371](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/371)) ([bdaeb99](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/bdaeb99a0be37bd3e223e7bd8104701f1ee6492e))

# [1.41.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.40.0...v1.41.0) (2026-06-28)


### Features

* **analyse:** écran « Espèces & observations » (inventaire pivot espèce/carré) ([#367](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/367)) ([91c6026](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/91c602639ff51b39ab61a93ee49554674e7d714c)), closes [#365](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/365) [#86](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/86) [#fafbfc](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/fafbfc)

# [1.40.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.39.0...v1.40.0) (2026-06-28)


### Features

* **multisite:** « Enregistrer » en overlay + capture & doc du mode édition ([#368](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/368)) ([9bc9933](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/9bc9933de183c3188282003e5142c61945209c74)), closes [#154](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/154)

# [1.39.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.38.0...v1.39.0) (2026-06-28)


### Features

* **analyse:** projections d'inventaire des espèces (par espèce / par carré) ([#365](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/365)) ([541a649](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/541a649abd9aac2e6006aac9c477503aaa4ddb79))
* **multisite:** toggle « Éditer les positions » en overlay icône sur la carte ([#362](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/362)) ([dae9131](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/dae913181300070080861f1cc31bfae57b410adb)), closes [#154](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/154)

# [1.38.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.37.0...v1.38.0) (2026-06-28)


### Features

* **recherche:** inclure les espèces/observations dans la recherche globale ([#323](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/323)) ([#363](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/363)) ([75ba3ab](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/75ba3abf12909cf3312a33bffa357e0e8b5b4501)), closes [DaoGenerique#projeter](https://github.com/DaoGenerique/issues/projeter)

# [1.37.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.36.0...v1.37.0) (2026-06-28)


### Features

* carte de confirmation au rattachement d'import ([#154](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/154)) ([#360](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/360)) ([5eb45b6](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/5eb45b633a650facc32dc2fd06c862fd7d94ed8b))

# [1.36.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.35.0...v1.36.0) (2026-06-28)


### Features

* badge GPS de la fiche site → « voir sur la carte » du point ([#154](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/154)) ([#358](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/358)) ([9e4eba6](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/9e4eba692a2d6155f9d443ef01d230cd4f231457))

# [1.35.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.34.0...v1.35.0) (2026-06-28)


### Features

* **multisite:** barre d'outils sur deux rangées, lisible ([#340](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/340)) ([#356](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/356)) ([76f6848](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/76f6848ddf446c32ed13a8fdbf184ca3e03d6001)), closes [#154](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/154)

# [1.34.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.33.0...v1.34.0) (2026-06-28)


### Features

* éditer les positions des points sur la carte multi-sites ([#154](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/154)) ([#353](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/353)) ([d9b59da](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/d9b59da3a4aafbb314e3881737cce895bec22315))
* **multisite:** renomme la vue en « Carte & passages » ([#342](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/342)) ([#354](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/354)) ([ea48f92](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/ea48f923f2e4b7d952f7444792d914ed96e36707)), closes [#152](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/152)

# [1.33.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.32.0...v1.33.0) (2026-06-28)


### Features

* **multisite:** retire le titre de page pour récupérer de la hauteur ([#341](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/341)) ([#351](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/351)) ([af515fc](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/af515fc60914b4c4c39bf6ca5214fb87aa479084)), closes [#lblResume](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/lblResume)

# [1.32.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.31.0...v1.32.0) (2026-06-28)


### Features

* **carte:** bouton « recadrer » sur la carte multisite ([#339](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/339)) ([#349](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/349)) ([c42710e](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/c42710e40e12a07b7af32a3d7adac726a87eeebb)), closes [#345](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/345)

# [1.31.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.30.0...v1.31.0) (2026-06-28)


### Features

* carte-outil de saisie GPS dans la modale point ([#153](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/153)) ([#345](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/345)) ([9302c30](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/9302c30643235ee6117c5a77714df73ac901a8f9))
* **multisite:** « Voir sur la carte » replie le tableau ([#338](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/338)) ([#347](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/347)) ([0ae770b](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/0ae770b87f7dfb86f97c9dc4d99689b02e40d369))

# [1.30.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.29.0...v1.30.0) (2026-06-28)


### Features

* **multisite:** légende de la carte repliée par défaut ([#337](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/337)) ([#344](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/344)) ([ecf12c3](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/ecf12c3b635f4c689c83366353202978a25aea61))

# [1.29.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.28.1...v1.29.0) (2026-06-28)


### Features

* afficher un point sans GPS au centre de son carré ([#153](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/153)) ([#336](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/336)) ([33ab9aa](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/33ab9aa82a7389a6bf79f4945ed8aaf3b61c61fc)), closes [#163](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/163)

## [1.28.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.28.0...v1.28.1) (2026-06-27)


### Bug Fixes

* **captures:** CaptureEcrans rend le chrome complet (RechercheGlobale bindé) ([#334](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/334)) ([5d48eb2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/5d48eb23de6bba01c578bdf7dec3c19531d58c22)), closes [#333](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/333) [#144](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/144)

# [1.28.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.27.0...v1.28.0) (2026-06-27)


### Features

* **carte:** boutons « Voir sur la carte » (M-Passage, fiche site) ([#152](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/152)) ([0f4820b](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/0f4820b5aa311925064c176d9821d24f05039924))
* **carte:** contrat OuvrirMultisite + focaliserSur la carte multi-sites ([#152](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/152)) ([1319bca](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/1319bca17adb2381df73af4c3b820ca6bcf02917))

# [1.27.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.26.2...v1.27.0) (2026-06-27)


### Features

* **carte:** focaliser + éditer un point — socle géo (PR-1) ([#330](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/330)) ([f47da26](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/f47da26896e9e5213961257a30b2298de6d221a3)), closes [#163](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/163)

## [1.26.2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.26.1...v1.26.2) (2026-06-27)


### Bug Fixes

* **multisite:** poignées de repli dans la barre d'actions ([#328](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/328)) ([#331](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/331)) ([92d529b](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/92d529b07fca098def7343162db7d8afe4b003d7)), closes [#314](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/314)

## [1.26.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.26.0...v1.26.1) (2026-06-27)


### Bug Fixes

* **qualification:** la colonne détail défile au lieu que l'AudioView déborde ([#329](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/329)) ([e2a6126](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/e2a6126341fa6ae6706e6c8837196d8ea2166a04))

# [1.26.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.25.0...v1.26.0) (2026-06-27)


### Features

* **carte:** carroyage national officiel + recalage du carré d'exemple ([#325](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/325)) ([#327](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/327)) ([ef01917](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/ef0191715e0165a50a64f4cb70fbd226770271a9))

# [1.25.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.24.1...v1.25.0) (2026-06-27)


### Features

* **sites:** édition de la fiche site (bouton « Modifier ») ([#326](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/326)) ([efc3709](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/efc37093af3fbe3322187af3b9ed5a1b68e5a116))

## [1.24.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.24.0...v1.24.1) (2026-06-27)


### Bug Fixes

* **sites:** explique pourquoi « Supprimer » est grisé (tooltip) ([#320](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/320)) ([e73b8a9](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/e73b8a9548ce0bb8d8eb0acf9fb204f4afab6c41))

# [1.24.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.23.0...v1.24.0) (2026-06-27)


### Features

* **accessibilité:** nom accessible sur les cartes d'action M-Passage ([#163](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/163)) ([#319](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/319)) ([9692edd](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/9692edda19496441f0dd589753044f8c878a491f))

# [1.23.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.22.0...v1.23.0) (2026-06-27)


### Features

* **recherche:** champ de recherche globale dans le chrome ([#144](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/144)) ([#314](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/314)) ([915e0ce](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/915e0ceab8314e568e37a6a1d85f72a4e26e1cd2))

# [1.22.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.21.0...v1.22.0) (2026-06-27)


### Features

* **recherche:** moteur de recherche globale — sites, points, passages ([#144](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/144)) ([#312](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/312)) ([1a9c083](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/1a9c0833244895835740b174c7847c854492b253))

# [1.21.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.20.0...v1.21.0) (2026-06-27)


### Features

* **multisite:** tableau de bord — mini-stats au survol ([#152](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/152)) ([#309](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/309)) ([1fe2362](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/1fe23620763540cc862b215d245757eafeafa454)), closes [#163](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/163)

# [1.20.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.19.0...v1.20.0) (2026-06-27)


### Features

* **multisite:** tableau de bord — densité par carré + légende ([#152](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/152)) ([#304](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/304)) ([315f36a](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/315f36ae6daaaf44f79dccfc354adaa8175f3b03)), closes [#163](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/163) [#163](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/163)

# [1.19.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.18.0...v1.19.0) (2026-06-27)


### Features

* **multisite:** liaisons carte ↔ tableau ([#152](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/152), PR 2-4) ([#301](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/301)) ([af4e85e](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/af4e85e4d4e9c4d688fc9e385d8d75a152470aae)), closes [#163](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/163)

# [1.18.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.17.2...v1.18.0) (2026-06-27)


### Features

* **multisite:** vue carte + tableau ([#152](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/152), PR 2-3) ([#294](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/294)) ([198f8f2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/198f8f2f869a9070a053e6042d086a54629d53ce))

## [1.17.2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.17.1...v1.17.2) (2026-06-27)


### Bug Fixes

* **multisite:** l'export CSV suit le tri par clic d'en-tête (suivi de [#291](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/291)) ([#295](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/295)) ([ad0557c](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/ad0557cc0f3661dd1d941b6f9d41439d06f4c5f1))

## [1.17.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.17.0...v1.17.1) (2026-06-27)


### Performance Improvements

* **carte:** séparer le rafraîchissement carte du tableau ([#152](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/152), suivi de [#289](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/289)) ([#292](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/292)) ([76e1831](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/76e183151e1295bd7f94df73717351a79f393bc9))

# [1.17.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.16.0...v1.17.0) (2026-06-27)


### Features

* **multisite:** tri du tableau par clic d'en-tête ([#145](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/145), PR 2-2) ([#291](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/291)) ([16b1109](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/16b1109cf3160de923ca3c15e1163e3795dc959f)), closes [#26](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/26)

# [1.16.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.15.0...v1.16.0) (2026-06-27)


### Features

* **carte:** agrégat des carrés du multisite pour la carte ([#152](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/152), PR 2-1) ([#289](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/289)) ([edd7d8d](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/edd7d8d857bfdad4fadbbf350d158f65fa053bbc))

# [1.15.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.14.0...v1.15.0) (2026-06-27)


### Features

* **carte:** composant réutilisable CarteSites (carrés + points) ([#152](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/152), PR 1) ([#288](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/288)) ([5f21fc3](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/5f21fc3cd857280c74500ed95d183e1b97a4de2c)), closes [#163](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/163)

# [1.14.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.13.2...v1.14.0) (2026-06-26)


### Features

* **import:** rapport importés/ignorés/rejetés + dimension doublon ([#214](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/214)) ([#285](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/285)) ([f01da3a](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/f01da3ab9b226a491c9d81d16712d3e194ef2613)), closes [#155](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/155) [#147](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/147) [#147](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/147)

## [1.13.2](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.13.1...v1.13.2) (2026-06-26)


### Bug Fixes

* **test:** débloquer le hang headless introduit par [#283](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/283) (modale native) ([#284](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/284)) ([91a96b6](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/91a96b661b6d9dff8a786dd123ecb15d9fb567f3)), closes [#147](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/147)

## [1.13.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.13.0...v1.13.1) (2026-06-26)


### Bug Fixes

* **import:** rafraîchir la détection de nuit déjà importée au clic ([#214](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/214)) ([#283](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/283)) ([13aeec6](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/13aeec6ce9ec538eeefa45b0086351d48aec640a)), closes [214/#147](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/147) [#280](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/280) [#280](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/280)

# [1.13.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.12.0...v1.13.0) (2026-06-26)


### Features

* **import:** confirmer avant de réimporter une nuit déjà importée ([#214](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/214)) ([#280](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/280)) ([0653834](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/0653834149752f8463b5f51e7bae14ece8429308)), closes [214/#147](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/147) [#147](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/147) [#279](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/279)

# [1.12.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.11.0...v1.12.0) (2026-06-26)


### Features

* **import:** écraser un passage en doublon, remplacement atomique ([#279](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/279)) ([#278](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/278)) ([9edf532](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/9edf5323aaca0e57528b12ae2ab2fdbf96db1968)), closes [#214](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/214) [#54](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/54) [#214](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/214) [#214](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/214) [#214](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/214) [#214](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/214) [#214](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/214)

# [1.11.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.10.0...v1.11.0) (2026-06-26)


### Features

* **navigation:** nettoyer le temporaire .zip à l'abandon d'un écran d'import ([#230](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/230)) ([#276](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/276)) ([8ace68e](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/8ace68ed348637f3793186d680f2a151d641ff6f)), closes [#228](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/228)

# [1.10.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.9.1...v1.10.0) (2026-06-26)


### Features

* **dépôt:** checklist vivante des contrôles de cohérence à l'étape « Préparer » ([#254](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/254)) ([#267](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/267)) ([0c890f8](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/0c890f89bda172cc4e2e52cc255b8438a5634686))

## [1.9.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.9.0...v1.9.1) (2026-06-26)


### Bug Fixes

* **navigation:** rafraîchir M-Multisite et M-Site-detail au retour ([#262](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/262)) ([fba1e2b](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/fba1e2b954cc30801dc4c7f3f4166d045b767d14)), closes [#260](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/260)

# [1.9.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.8.1...v1.9.0) (2026-06-26)


### Features

* **dépôt:** M-Lot — génération hors-thread + dossier depot/ ouvrable ([#251](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/251)) ([#259](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/259)) ([c4784a8](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/c4784a8de0aff6bbcf5f748d37d279c17af524b9))

## [1.8.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.8.0...v1.8.1) (2026-06-26)


### Bug Fixes

* **navigation:** rafraîchir M-Passage au retour d'une vérification ([#260](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/260)) ([9505be8](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/9505be8e32e36c4781dcc76ae41b0d356bcfc988))

# [1.8.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.7.0...v1.8.0) (2026-06-26)


### Features

* **chrome:** barre de défilement centrale quand l'écran dépasse la hauteur ([#256](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/256)) ([074ebb3](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/074ebb3358f3cdddd644bd615fb07de7ed1035d5))

# [1.7.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.6.0...v1.7.0) (2026-06-26)


### Features

* **dépôt:** clarifier M-Lot — flux ordonné, stepper et étape « Préparer » explicite ([#251](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/251)) ([d92391f](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/d92391fc67099f60387bba6bcd35c319e7009edd)), closes [#lblCheminDossier](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/lblCheminDossier)

# [1.6.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.5.0...v1.6.0) (2026-06-26)


### Features

* **captures:** spectrogrammes lisibles dans les aperçus bibliothèque et qualification ([#252](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/252)) ([3794456](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/37944569921ab9a10816fd226b0a5c2fda05c174)), closes [#159](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/159) [#tableEntrees](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/tableEntrees)

# [1.5.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.4.1...v1.5.0) (2026-06-25)


### Features

* **écoute:** activer la normalisation du son à l'écoute ([#109](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/109), [#159](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/159)) ([#248](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/248)) ([e712e8a](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/e712e8a6c20d0124fec3eca37a14dd979301b4f0))

## [1.4.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.4.0...v1.4.1) (2026-06-25)


### Bug Fixes

* **import:** vérification d'intégrité des fichiers WAV ([#156](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/156)) ([#250](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/250)) ([9907c51](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/9907c51d804e0e79d264e89af4f380c923283350)), closes [#155](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/155)

# [1.4.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.3.0...v1.4.0) (2026-06-25)


### Features

* **import:** import résilient + rapport d'anomalies exportable ([#155](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/155)) ([#246](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/246)) ([690d0d9](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/690d0d9b2f0fd4904b6c33c3f410b62e938e35ea)), closes [#146](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/146) [#zoneRejets](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/zoneRejets) [#listeRejets](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/listeRejets)

# [1.3.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.2.0...v1.3.0) (2026-06-25)


### Features

* **sites:** activer « Importer une nuit » sur la fiche site (pré-rattachée) ([#245](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/245)) ([7e668cd](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/7e668cdca7b9d41071a8fff8c3a590d663509170))

# [1.2.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.1.0...v1.2.0) (2026-06-25)


### Features

* **style:** re-skin de l'application en indigo (étape 2/2, aligné sur le brief) ([#242](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/242)) ([d6852ed](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/d6852ed32bdbe37ea0152d3e0b2ad98cfda68a28)), closes [#4a90d9](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/4a90d9) [#3f51b5](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/3f51b5) [#303f9f](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/303f9f) [#c5cae9](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/c5cae9) [#eef0fa](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/eef0fa) [#1a2a45](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1a2a45) [#1a237e](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1a237e) [#b9770e](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/b9770e)

# [1.1.0](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.0.1...v1.1.0) (2026-06-25)


### Features

* **lot:** archives ZIP de dépôt Tadarida (≤ 700 Mo, <préfixe>-N.zip) ([#110](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/110)) ([#239](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/239)) ([4a4e61d](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/4a4e61d70fa18090b774cc9e18537a4410519b66)), closes [#104](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/104)

## [1.0.1](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/compare/v1.0.0...v1.0.1) (2026-06-24)


### Bug Fixes

* **installer:** identifiant de bundle macOS valide pour jpackage ([#236](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/236)) ([ca82b79](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/commit/ca82b7966a2a487460614546ae8fc9168a835c86))
