# M-Releve - Relevé d'une nuit sur la plateforme

> **Type** : écran spécialisé, ouvert par une carte d'action de [M-Passage](M-Passage.md).
> **Persona principal** : [Karim](../Personas/Karim.md) (une nuit d'un lot a échoué) et [Marie](../Personas/Marie.md) (« est-ce que ma nuit est bien arrivée ? »).
> **Parcours couvert** : [P14 - Vérifier ce que la plateforme détient d'une nuit](../Parcours%20utilisateurs/P14%20-%20Vérifier%20ce%20que%20la%20plateforme%20détient%20d%27une%20nuit.md).
> **Issue** : #3845. **Cible non livrée** : l'écran n'existe pas, son état se lit sur l'issue.

L'écran répond à une question posée depuis **une nuit précise** : que détient la plateforme, et en
quoi cela diffère-t-il de ce que la base locale affirme ? L'[audit de cohérence](M-Audit.md) répond à
la même famille de questions à l'échelle de l'installation ; celui-ci part du passage ouvert.

> **Lire d'abord, écrire ensuite.** Le relevé est une **lecture**. Aucune écriture n'a lieu tant que
> l'utilisateur n'a pas déclenché une réparation nommée, et chaque réparation est confirmée à part.

## Maquette principale - des écarts constatés

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 470" role="img" aria-label="Maquette M-Releve - état comparé entre la plateforme et la base locale, avec quatre écarts catégorisés" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #f7f9fb;">
  <style>
    .chrome { fill: #3f51b5; }
    .chrometxt { fill: #ffffff; font: 600 14px sans-serif; }
    .crumb { fill: #c5cae9; font: 13px sans-serif; }
    .crumb-curr { fill: #ffffff; font: 700 13px sans-serif; }
    .card { fill: #ffffff; stroke: #c4ccd4; stroke-width: 1; }
    .card-head { fill: #eef2f5; stroke: #c4ccd4; stroke-width: 1; }
    .head-txt { font: 600 11px sans-serif; fill: #4a6785; }
    .titre { font: 600 13px sans-serif; fill: #2c3e50; }
    .cell { font: 12px sans-serif; fill: #2c3e50; }
    .cell-b { font: 600 12px sans-serif; fill: #2c3e50; }
    .muted { font: 11px sans-serif; fill: #6b7a8d; }
    .num { font: 600 15px sans-serif; fill: #2c3e50; text-anchor: end; }
    .ok { fill: #1e8449; }
    .warn { fill: #b7791f; }
    .bad { fill: #a83a33; }
    .info { fill: #2f5ea8; }
    .btn { fill: #ffffff; stroke: #c4ccd4; stroke-width: 1; }
    .btn-pri { fill: #3f51b5; stroke: #2c3a8c; stroke-width: 1; }
    .btn-txt { font: 600 11px sans-serif; fill: #37405c; text-anchor: middle; }
    .btn-pri-txt { font: 600 11px sans-serif; fill: #ffffff; text-anchor: middle; }
    .tag { font: 600 10px sans-serif; text-anchor: middle; }
  </style>

  <rect x="0" y="0" width="1000" height="470" fill="#f7f9fb"/>

  <rect class="chrome" x="0" y="0" width="1000" height="34"/>
  <text class="chrometxt" x="16" y="22">VigieChiro Companion</text>
  <text class="crumb" x="200" y="22">Mes sites › Carré 640380 › Passage n° 2 ›</text>
  <text class="crumb-curr" x="470" y="22">Relevé sur la plateforme</text>

  <text class="titre" x="16" y="58">Nuit du 22/06/2026 · Carré 640380 · Point A1</text>
  <text class="muted" x="16" y="76">Relevé effectué le 16/08/2026 à 09:12 · participation 1925492</text>
  <rect class="btn" x="856" y="46" width="128" height="24" rx="4"/>
  <text class="btn-txt" x="920" y="62">Relever à nouveau</text>

  <rect class="card-head" x="16" y="92" width="480" height="26" rx="4"/>
  <text class="head-txt" x="28" y="109">CE QUE LA PLATEFORME DÉTIENT</text>
  <rect class="card" x="16" y="118" width="480" height="104"/>
  <text class="cell" x="28" y="140">Fichiers déposés</text>
  <text class="num" x="300" y="140">542</text>
  <text class="cell" x="28" y="162">Analyse Tadarida</text>
  <text class="cell-b info" x="180" y="162">jamais lancée</text>
  <text class="cell" x="28" y="184">Observations disponibles</text>
  <text class="num" x="300" y="184">0</text>
  <text class="cell" x="28" y="206">Avis du validateur</text>
  <text class="cell" x="180" y="206">aucun</text>

  <rect class="card-head" x="512" y="92" width="472" height="26" rx="4"/>
  <text class="head-txt" x="524" y="109">CE QUE LA BASE LOCALE AFFIRME</text>
  <rect class="card" x="512" y="118" width="472" height="104"/>
  <text class="cell" x="524" y="140">Statut</text>
  <text class="cell-b warn" x="676" y="140">Dépôt en cours</text>
  <text class="cell" x="524" y="162">Unités téléversées</text>
  <text class="num" x="796" y="162">3 / 3</text>
  <text class="cell" x="524" y="184">Séquences en base</text>
  <text class="num" x="796" y="184">542</text>
  <text class="cell" x="524" y="206">Fichiers encore sur le disque</text>
  <text class="num" x="796" y="206">0</text>

  <rect class="card-head" x="16" y="238" width="968" height="26" rx="4"/>
  <text class="head-txt" x="28" y="255">ÉCARTS CONSTATÉS · 4</text>
  <text class="head-txt" x="640" y="255">CATÉGORIE</text>
  <text class="head-txt" x="820" y="255">ACTION</text>
  <rect class="card" x="16" y="264" width="968" height="166"/>

  <text class="cell-b bad" x="28" y="288">Le dépôt est complet côté serveur, le statut local dit « en cours »</text>
  <text class="muted" x="28" y="304">542 fichiers sur 542 sont en ligne. Le statut n'a pas été mis à jour.</text>
  <rect x="628" y="276" width="140" height="18" rx="9" fill="#e6f4ec"/>
  <text class="tag ok" x="698" y="289">réparable ici</text>
  <rect class="btn-pri" x="820" y="274" width="150" height="24" rx="4"/>
  <text class="btn-pri-txt" x="895" y="290">Passer à « Déposé »</text>

  <line x1="16" y1="318" x2="984" y2="318" stroke="#e3e8ee"/>
  <text class="cell-b warn" x="28" y="340">L'analyse n'a jamais été lancée</text>
  <text class="muted" x="28" y="356">Sans elle, aucune observation ne redescendra. Le lot avait échoué avant cette étape.</text>
  <rect x="628" y="328" width="140" height="18" rx="9" fill="#e6f4ec"/>
  <text class="tag ok" x="698" y="341">réparable ici</text>
  <rect class="btn-pri" x="820" y="326" width="150" height="24" rx="4"/>
  <text class="btn-pri-txt" x="895" y="342">Déclencher le calcul</text>

  <line x1="16" y1="370" x2="984" y2="370" stroke="#e3e8ee"/>
  <text class="cell-b info" x="28" y="392">542 fichiers sont sur serveur seul</text>
  <text class="muted" x="28" y="408">Purgés du disque après le dépôt. Ce n'est pas un manque : c'est l'état normal d'une nuit archivée.</text>
  <rect x="628" y="380" width="140" height="18" rx="9" fill="#eef2f8"/>
  <text class="tag info" x="698" y="393">constat</text>
  <text class="muted" x="836" y="394">rien à faire</text>
</svg>
</div>

### Annotations

**Deux colonnes, pas une synthèse.** L'écran montre côte à côte ce que chaque partie affirme, avant
de dire ce qui les sépare. Un utilisateur qui doute du verdict peut lire les deux relevés et se faire
son propre avis, plutôt que de croire une conclusion.

**Chaque écart porte sa catégorie.** Trois valeurs seulement : *réparable ici*, *à faire sur le
portail*, *constat*. Un écart de la troisième catégorie n'a pas de bouton, et l'écran dit pourquoi
plutôt que de laisser une case vide qui se lirait comme un oubli.

**Le troisième écart n'en est pas un.** « 542 fichiers sur serveur seul » décrit une nuit archivée
dont tout s'est bien passé. Il figure quand même au relevé, parce que ne pas l'afficher laisserait
croire à un trou, et il est catégorisé *constat* pour ne pas alarmer. C'est le cas qui décide de la
qualité de cet écran : un outil de diagnostic qui crie au loup sur le cas nominal cesse d'être lu.

**L'horodatage du relevé est affiché.** Un relevé est une photographie, pas un état vivant. Le
bouton « Relever à nouveau » est le seul moyen de la rafraîchir : aucun sondage automatique, ce qui
prolonge la décision déjà prise sur le suivi du calcul.

## Interactions clés

| Élément | Action | Effet |
|---|---|---|
| **Relever à nouveau** | clic | réinterroge la plateforme et réécrit l'horodatage. Aucune écriture en base. |
| **Bouton d'un écart** | clic | ouvre une confirmation qui nomme ce qui sera écrit, puis l'écrit et relance le relevé. |
| **Écart de catégorie *constat*** | aucune | pas de bouton. Le motif tient lieu d'action. |
| **Jeton expiré** | pendant le relevé | message qui nomme la cause, et retour à l'écran. Jamais de téléversement relancé. |
| **Hors connexion** | à l'ouverture | l'écran affiche la colonne locale seule et dit que la colonne serveur n'a pas pu être lue. |

## Variantes attendues

- **Aucun écart** : les deux colonnes, et une ligne unique « la plateforme et la base disent la même
  chose », avec l'horodatage. L'absence d'écart est une information, pas un écran vide.
- **Nuit jamais déposée** : la colonne serveur est vide et l'écran le dit d'une phrase, sans lister
  quatre écarts qui découleraient tous du même fait.

## Notes pour l'implémentation

- L'écran est ouvert par une **carte d'action** de [M-Passage](M-Passage.md), via le contrat socle
  `Ouvrir*`, comme les autres facettes d'un passage. Il porte donc le hub à sept cartes, ce qui est
  le seuil à partir duquel le pivot demande un regroupement par nature.
- Les contrôles réutilisés doivent être ceux de l'[audit de cohérence](M-Audit.md), ou la différence
  de périmètre doit être écrite : deux jeux de contrôles qui répondraient différemment à la même
  question se contrediraient devant l'utilisateur.
- Le relevé est une lecture réseau : il suit la politique de réessai **insistante**, puisque quelqu'un
  attend devant l'écran.
