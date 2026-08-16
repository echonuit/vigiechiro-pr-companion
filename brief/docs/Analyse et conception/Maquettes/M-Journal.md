# M-Journal - L'histoire d'une nuit

> **Type** : écran spécialisé, ouvert par une carte d'action de [M-Passage](M-Passage.md).
> **Persona principal** : [Samuel](../Personas/Samuel.md) (rendre compte de ce qui a été fait à la donnée) et [Karim](../Personas/Karim.md) (diagnostiquer après coup).
> **Parcours couvert** : [P15 - Relire l'histoire d'une nuit](../Parcours%20utilisateurs/P15%20-%20Relire%20l%27histoire%20d%27une%20nuit.md).
> **Issue** : #3846. **Cible non livrée** : l'écran n'existe pas, son état se lit sur l'issue.

La fiche d'un passage donne son **état courant**. Cet écran donne son **parcours** : quand chaque
étape a eu lieu, dans quel ordre, et ce qu'elle a produit.

> **Une lecture, pas un poste de pilotage.** Aucune action ne se prend depuis la frise. Elle raconte
> ce qui s'est passé ; ce qui reste à faire se décide sur la fiche du passage.

## Maquette principale - la frise antéchronologique

<div markdown="0">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 430" role="img" aria-label="Maquette M-Journal - frise antéchronologique des évènements d'un passage, du plus récent au plus ancien" style="max-width: 100%; height: auto; border: 1px solid #d0d7de; border-radius: 6px; background: #f7f9fb;">
  <style>
    .chrome { fill: #3f51b5; }
    .chrometxt { fill: #ffffff; font: 600 14px sans-serif; }
    .crumb { fill: #c5cae9; font: 13px sans-serif; }
    .crumb-curr { fill: #ffffff; font: 700 13px sans-serif; }
    .card { fill: #ffffff; stroke: #c4ccd4; stroke-width: 1; }
    .titre { font: 600 13px sans-serif; fill: #2c3e50; }
    .cell { font: 12px sans-serif; fill: #2c3e50; }
    .cell-b { font: 600 12px sans-serif; fill: #2c3e50; }
    .muted { font: 11px sans-serif; fill: #6b7a8d; }
    .date { font: 600 11px sans-serif; fill: #4a6785; text-anchor: end; }
    .chip { font: 600 10px sans-serif; text-anchor: middle; }
    .btn { fill: #ffffff; stroke: #c4ccd4; stroke-width: 1; }
    .btn-txt { font: 600 11px sans-serif; fill: #37405c; text-anchor: middle; }
    .rail { stroke: #c4ccd4; stroke-width: 2; }
  </style>

  <rect x="0" y="0" width="1000" height="430" fill="#f7f9fb"/>

  <rect class="chrome" x="0" y="0" width="1000" height="34"/>
  <text class="chrometxt" x="16" y="22">VigieChiro Companion</text>
  <text class="crumb" x="200" y="22">Mes sites › Carré 640380 › Passage n° 2 ›</text>
  <text class="crumb-curr" x="470" y="22">Journal</text>

  <text class="titre" x="16" y="58">Nuit du 22/06/2026 · Carré 640380 · Point A1</text>
  <text class="muted" x="16" y="76">9 évènements, du 24/06 au 12/05</text>
  <rect class="btn" x="700" y="46" width="130" height="24" rx="4"/>
  <text class="btn-txt" x="765" y="62">Tous les types ▾</text>
  <rect class="btn" x="842" y="46" width="142" height="24" rx="4"/>
  <text class="btn-txt" x="913" y="62">Exporter le journal</text>

  <rect class="card" x="16" y="92" width="968" height="322"/>
  <line class="rail" x1="150" y1="110" x2="150" y2="400"/>

  <text class="date" x="132" y="126">24/06 09:12</text>
  <circle cx="150" cy="122" r="5" fill="#2f5ea8"/>
  <rect x="168" y="112" width="150" height="18" rx="9" fill="#eef2f8"/>
  <text class="chip" fill="#2f5ea8" x="243" y="125">avis du validateur</text>
  <text class="cell" x="336" y="126">1 observation commentée, fil de discussion ouvert</text>

  <text class="date" x="132" y="170">23/06 21:40</text>
  <circle cx="150" cy="166" r="5" fill="#1e8449"/>
  <rect x="168" y="156" width="150" height="18" rx="9" fill="#e6f4ec"/>
  <text class="chip" fill="#1e8449" x="243" y="169">corrections publiées</text>
  <text class="cell" x="336" y="170">37 corrections envoyées, 37 acceptées</text>

  <text class="date" x="132" y="214">23/06 18:02</text>
  <circle cx="150" cy="210" r="5" fill="#1e8449"/>
  <rect x="168" y="200" width="150" height="18" rx="9" fill="#e6f4ec"/>
  <text class="chip" fill="#1e8449" x="243" y="213">observations importées</text>
  <text class="cell" x="336" y="214">2 109 observations, 12 espèces, ancrage et discussions</text>

  <text class="date" x="132" y="258">22/06 07:15</text>
  <circle cx="150" cy="254" r="5" fill="#1e8449"/>
  <rect x="168" y="244" width="150" height="18" rx="9" fill="#e6f4ec"/>
  <text class="chip" fill="#1e8449" x="243" y="257">calcul déclenché</text>
  <text class="cell" x="336" y="258">demandé à Vigie-Chiro, participation 1925492</text>

  <text class="date" x="132" y="302">21/06 23:48</text>
  <circle cx="150" cy="298" r="5" fill="#b7791f"/>
  <rect x="168" y="288" width="150" height="18" rx="9" fill="#fbf1da"/>
  <text class="chip" fill="#b7791f" x="243" y="301">dépôt interrompu</text>
  <text class="cell" x="336" y="302">2 archives sur 3 · repris et terminé le 22/06 à 06:40</text>

  <text class="date" x="132" y="346">20/06 14:22</text>
  <circle cx="150" cy="342" r="5" fill="#1e8449"/>
  <rect x="168" y="332" width="150" height="18" rx="9" fill="#e6f4ec"/>
  <text class="chip" fill="#1e8449" x="243" y="345">verdict posé</text>
  <text class="cell" x="336" y="346">exploitable · 18 séquences écoutées sur 542</text>

  <text class="date" x="132" y="390">12/05 10:05</text>
  <circle cx="150" cy="386" r="5" fill="#1e8449"/>
  <rect x="168" y="376" width="150" height="18" rx="9" fill="#e6f4ec"/>
  <text class="chip" fill="#1e8449" x="243" y="389">nuit importée</text>
  <text class="cell" x="336" y="390">542 séquences transformées · 41,2 Go lus, 4,3 Go écrits</text>
</svg>
</div>

### Annotations

**Le résumé chiffré est propre à chaque type.** « 542 séquences transformées » et « 2 archives sur
3 » ne se rangent pas dans les mêmes colonnes. Un journal générique qui afficherait « opération
réussie » pour tout n'apporterait rien de plus que le statut courant.

**Un évènement peut porter sa suite.** Le dépôt interrompu du 21/06 dit dans la même ligne qu'il a
été repris et terminé. Deux lignes séparées obligeraient le lecteur à recoudre lui-même, et une
seule ligne « dépôt réussi » effacerait l'incident.

**Antéchronologique, parce que la question est récente.** On ouvre ce journal quand quelque chose
surprend aujourd'hui. Le plus récent en premier met la réponse probable en haut.

## Interactions clés

| Élément | Action | Effet |
|---|---|---|
| **Tous les types ▾** | clic | filtre la frise sur une famille d'évènements (import, dépôt, validation…). |
| **Une ligne** | clic | déplie le détail complet de l'évènement quand il en a un, sans quitter la frise. |
| **Exporter le journal** | clic | produit le même contenu en texte, pour le joindre à une demande d'aide. |
| **Aucun** | - | aucune action ne modifie le passage depuis cet écran. |

## Variantes attendues

- **Passage antérieur à la migration** : la frise commence à une ligne qui le dit, plutôt que de
  laisser croire que rien ne s'est passé avant. La décision à prendre au chantier est de reconstituer
  ou non les évènements dérivables des dates déjà stockées, en les marquant comme reconstitués.
- **Nuit récupérée de la plateforme** : les évènements antérieurs à la récupération n'ont pas eu lieu
  sur ce poste. La frise doit le dire, sans quoi elle prêterait à l'utilisateur des gestes qu'il n'a
  pas faits.

## Notes pour l'implémentation

- Une table d'évènements est nécessaire, alimentée aux points de passage qui produisent déjà un
  résultat. Le critère de ce qui vaut un évènement doit être écrit avant la migration : sans lui, la
  table se remplit de bruit ou rate ce qui compte.
- L'écran est ouvert par une **carte d'action** de [M-Passage](M-Passage.md), via `Ouvrir*`.
- Le journal est aussi ce qui rend durable le compte rendu d'un traitement en lot, aujourd'hui
  affiché une fois puis perdu à la fermeture de sa fenêtre. Voir [M-CompteRendu](M-CompteRendu.md).
